package com.ohchurus.budget.service.impl;

import com.ohchurus.budget.dto.output.DashboardSummaryDTO;
import com.ohchurus.budget.dto.output.ResultDTO;
import com.ohchurus.budget.dto.output.ResultMovementDTO;
import com.ohchurus.budget.entity.Category;
import com.ohchurus.budget.entity.Movement;
import com.ohchurus.budget.util.Computables;
import com.ohchurus.budget.entity.ScheduledMovement;
import com.ohchurus.budget.enums.CategoryType;
import com.ohchurus.budget.mapper.MovementMapper;
import com.ohchurus.budget.repository.CategoryRepository;
import com.ohchurus.budget.repository.MovementRepository;
import com.ohchurus.budget.repository.ScheduledMovementRepository;
import com.ohchurus.budget.service.DashboardService;
import com.ohchurus.budget.service.ScheduledMovementService;
import com.ohchurus.budget.util.PeriodUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional(readOnly = true)
public class DashboardServiceImpl implements DashboardService {

    private final MovementRepository movementRepository;
    private final ScheduledMovementRepository scheduledMovementRepository;
    private final CategoryRepository categoryRepository;
    private final MovementMapper movementMapper;
    private final ScheduledMovementService scheduledMovementService;
    private final HouseholdServiceImpl householdService;

    public DashboardServiceImpl(MovementRepository movementRepository,
                                 ScheduledMovementRepository scheduledMovementRepository,
                                 CategoryRepository categoryRepository,
                                 MovementMapper movementMapper,
                                 @org.springframework.context.annotation.Lazy ScheduledMovementService scheduledMovementService,
                                 HouseholdServiceImpl householdService) {
        this.movementRepository = movementRepository;
        this.scheduledMovementRepository = scheduledMovementRepository;
        this.categoryRepository = categoryRepository;
        this.movementMapper = movementMapper;
        this.scheduledMovementService = scheduledMovementService;
        this.householdService = householdService;
    }

    /**
     * Resumen financiero del periodo.
     *
     * totalIncome   = suma de movimientos CONFIRMADOS cuya categoria es INCOME
     * totalExpense  = suma de movimientos CONFIRMADOS cuya categoria es EXPENSE
     * balance       = totalIncome - totalExpense
     * budgetTotal   = suma de montos de programados activos para el periodo (proyeccion)
     * pendingCount  = cantidad de movimientos NO confirmados cuya fecha <= hoy
     * pendingAmount = suma de montos de esos pendientes
     */
    @Override
    public ResultDTO getSummary(Long userId, Integer budgetStartDay) {
        return getSummary(userId, budgetStartDay, null);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public ResultDTO getSummary(Long userId, Integer budgetStartDay, LocalDate referenceDate) {
        try {
            LocalDate ref = referenceDate != null ? referenceDate : LocalDate.now();
            LocalDate periodStart = PeriodUtils.getStartOfPeriod(budgetStartDay, ref);
            LocalDate periodEnd = PeriodUtils.getEndOfPeriod(budgetStartDay, periodStart);

            // Auto-generate pending from scheduled movements (idempotent)
            LocalDate currentPeriodStart = PeriodUtils.getStartOfPeriod(budgetStartDay, LocalDate.now());
            if (periodStart.equals(currentPeriodStart)) {
                try {
                    scheduledMovementService.generatePending(userId, budgetStartDay);
                } catch (Exception e) {
                    log.warn("Auto-generate pending failed: {}", e.getMessage());
                }
            }

            Map<Long, Category> categoryCache = new HashMap<>();

            // Get household IDs for this user
            List<Long> householdIds = householdService.getHouseholdIds(userId);

            // 1. Movimientos confirmados del periodo (personales + household)
            List<Movement> confirmed;
            if (!householdIds.isEmpty()) {
                confirmed = movementRepository.findHouseholdConfirmed(userId, householdIds, periodStart, periodEnd);
            } else {
                confirmed = movementRepository.findByUserIdAndDateBetweenAndConfirmedTrueAndActiveTrue(userId, periodStart, periodEnd);
            }

            BigDecimal totalIncome = BigDecimal.ZERO;
            BigDecimal totalExpense = BigDecimal.ZERO;
            for (Movement m : confirmed) {
                /* Una sola regla para toda la app: ver Computables.
                   Antes aqui se excluian solo las transferencias y se contaban
                   los sub-movimientos, mientras que `budgetTotal` —quince
                   lineas mas abajo, en este mismo metodo— si los excluia. La
                   misma plata daba dos cifras distintas en la misma pantalla. */
                if (!Computables.suma(m)) continue;
                BigDecimal amount = Computables.importe(m);
                if (getCategoryType(m.getCategoryId(), categoryCache) == CategoryType.INCOME) {
                    totalIncome = totalIncome.add(amount);
                } else {
                    totalExpense = totalExpense.add(amount);
                }
            }

            // 2. Presupuesto = suma de TODOS los egresos del periodo (confirmados + pendientes)
            List<Movement> pendingInPeriod;
            if (!householdIds.isEmpty()) {
                pendingInPeriod = movementRepository.findHouseholdPending(userId, householdIds, periodStart, periodEnd);
            } else {
                pendingInPeriod = movementRepository.findByUserIdAndDateBetweenAndConfirmedFalseAndActiveTrue(userId, periodStart, periodEnd);
            }
            List<Movement> allPeriodMovements = new ArrayList<>(confirmed);
            allPeriodMovements.addAll(pendingInPeriod);
            BigDecimal budgetTotal = allPeriodMovements.stream()
                    .filter(m -> getCategoryType(m.getCategoryId(), categoryCache) == CategoryType.EXPENSE)
                    .filter(Computables::suma)
                    .map(Computables::importe)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // 3. Pendientes (personales + household)
            List<Movement> oldPending;
            if (!householdIds.isEmpty()) {
                oldPending = movementRepository.findHouseholdAllPending(userId, householdIds).stream()
                        .filter(m -> m.getDate().isBefore(periodStart))
                        .collect(Collectors.toList());
            } else {
                oldPending = movementRepository.findByUserIdAndConfirmedFalseAndActiveTrue(userId).stream()
                        .filter(m -> m.getDate().isBefore(periodStart))
                        .collect(Collectors.toList());
            }
            List<Movement> duePending = new ArrayList<>(pendingInPeriod);
            duePending.addAll(oldPending);

            BigDecimal pendingAmount = Computables.total(duePending);

            DashboardSummaryDTO summary = DashboardSummaryDTO.builder()
                    .totalIncome(totalIncome)
                    .totalExpense(totalExpense)
                    .balance(totalIncome.subtract(totalExpense))
                    .budgetTotal(budgetTotal)
                    .pendingCount(duePending.size())
                    .pendingAmount(pendingAmount)
                    .periodStart(periodStart)
                    .periodEnd(periodEnd)
                    .build();

            return new ResultDTO(summary);
        } catch (Exception e) {
            log.error("Error getting dashboard summary for user {}: {}", userId, e.getMessage(), e);
            return new ResultDTO(false, "Error getting dashboard summary", 500);
        }
    }

    @Override
    public ResultDTO getByCategory(Long userId, Integer budgetStartDay) {
        return getByCategory(userId, budgetStartDay, null);
    }

    @Override
    public ResultDTO getByCategory(Long userId, Integer budgetStartDay, LocalDate referenceDate) {
        try {
            LocalDate ref = referenceDate != null ? referenceDate : LocalDate.now();
            LocalDate periodStart = PeriodUtils.getStartOfPeriod(budgetStartDay, ref);
            LocalDate periodEnd = PeriodUtils.getEndOfPeriod(budgetStartDay, periodStart);

            List<Long> hIds = householdService.getHouseholdIds(userId);
            List<Movement> confirmed = !hIds.isEmpty()
                    ? movementRepository.findHouseholdConfirmed(userId, hIds, periodStart, periodEnd)
                    : movementRepository.findByUserIdAndDateBetweenAndConfirmedTrueAndActiveTrue(userId, periodStart, periodEnd);

            Map<Long, Category> categoryCache = new HashMap<>();

            // Bug 6: exclude transfers from category breakdown
            List<DashboardSummaryDTO.CategorySummary> categories = confirmed.stream()
                    .filter(m -> !Boolean.TRUE.equals(m.getIsTransfer()))
                    .collect(Collectors.groupingBy(Movement::getCategoryId))
                    .entrySet().stream()
                    .map(entry -> {
                        Long categoryId = entry.getKey();
                        Category cat = categoryCache.computeIfAbsent(categoryId,
                                id -> categoryRepository.findByIdAndActiveTrue(id).orElse(null));

                        DashboardSummaryDTO.CategorySummary.CategorySummaryBuilder builder =
                                DashboardSummaryDTO.CategorySummary.builder()
                                        .categoryId(categoryId)
                                        /* Misma regla: si la dona sumara los
                                           sub-movimientos, no cuadraria con el
                                           total del panel ni con su propio
                                           detalle al entrar en la categoria. */
                                        .total(Computables.total(entry.getValue()))
                                        .count((int) entry.getValue().stream()
                                                .filter(Computables::suma).count());

                        if (cat != null) {
                            builder.categoryName(cat.getName())
                                    .categoryType(cat.getType() != null ? cat.getType().name() : null)
                                    .icon(cat.getIcon())
                                    .color(cat.getColor());
                        }

                        return builder.build();
                    })
                    .collect(Collectors.toList());

            return new ResultDTO(categories);
        } catch (Exception e) {
            log.error("Error getting dashboard by category for user {}: {}", userId, e.getMessage(), e);
            return new ResultDTO(false, "Error getting dashboard by category", 500);
        }
    }

    @Override
    public ResultDTO getTrend(Long userId, Integer budgetStartDay) {
        try {
            LocalDate today = LocalDate.now();
            LocalDate currentStart = PeriodUtils.getStartOfPeriod(budgetStartDay, today);
            LocalDate currentEnd = PeriodUtils.getEndOfPeriod(budgetStartDay, currentStart);

            LocalDate previousStart = PeriodUtils.getStartOfPeriod(budgetStartDay, currentStart.minusDays(1));
            LocalDate previousEnd = PeriodUtils.getEndOfPeriod(budgetStartDay, previousStart);

            Map<Long, Category> categoryCache = new HashMap<>();
            List<Long> hIds = householdService.getHouseholdIds(userId);

            List<Movement> currentMovements = !hIds.isEmpty()
                    ? movementRepository.findHouseholdConfirmed(userId, hIds, currentStart, currentEnd)
                    : movementRepository.findByUserIdAndDateBetweenAndConfirmedTrueAndActiveTrue(userId, currentStart, currentEnd);

            List<Movement> previousMovements = !hIds.isEmpty()
                    ? movementRepository.findHouseholdConfirmed(userId, hIds, previousStart, previousEnd)
                    : movementRepository.findByUserIdAndDateBetweenAndConfirmedTrueAndActiveTrue(userId, previousStart, previousEnd);

            BigDecimal[] currentTotals = calculateIncomeExpense(currentMovements, categoryCache);
            BigDecimal[] previousTotals = calculateIncomeExpense(previousMovements, categoryCache);

            BigDecimal currentBalance = currentTotals[0].subtract(currentTotals[1]);
            BigDecimal previousBalance = previousTotals[0].subtract(previousTotals[1]);

            BigDecimal changePercentage = BigDecimal.ZERO;
            if (previousBalance.abs().compareTo(BigDecimal.ZERO) > 0) {
                changePercentage = currentBalance.subtract(previousBalance)
                        .divide(previousBalance.abs(), 4, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"))
                        .setScale(2, RoundingMode.HALF_UP);
            }

            DashboardSummaryDTO.TrendDTO trend = DashboardSummaryDTO.TrendDTO.builder()
                    .currentIncome(currentTotals[0])
                    .currentExpense(currentTotals[1])
                    .previousIncome(previousTotals[0])
                    .previousExpense(previousTotals[1])
                    .changePercentage(changePercentage)
                    .currentPeriodStart(currentStart)
                    .currentPeriodEnd(currentEnd)
                    .previousPeriodStart(previousStart)
                    .previousPeriodEnd(previousEnd)
                    .build();

            return new ResultDTO(trend);
        } catch (Exception e) {
            log.error("Error getting dashboard trend for user {}: {}", userId, e.getMessage(), e);
            return new ResultDTO(false, "Error getting dashboard trend", 500);
        }
    }

    /**
     * Retorna todos los pendientes del periodo actual + pendientes de periodos pasados.
     * Enriquecidos con datos de categoria.
     */
    @Override
    public ResultDTO getPending(Long userId, Integer budgetStartDay) {
        return getPending(userId, budgetStartDay, null);
    }

    @Override
    public ResultDTO getPending(Long userId, Integer budgetStartDay, LocalDate referenceDate) {
        try {
            LocalDate ref = referenceDate != null ? referenceDate : LocalDate.now();
            LocalDate periodStart = PeriodUtils.getStartOfPeriod(budgetStartDay, ref);
            LocalDate periodEnd = PeriodUtils.getEndOfPeriod(budgetStartDay, periodStart);

            // Pendientes del periodo (personales + household)
            List<Long> hIds = householdService.getHouseholdIds(userId);
            List<Movement> periodPending = !hIds.isEmpty()
                    ? movementRepository.findHouseholdPending(userId, hIds, periodStart, periodEnd)
                    : movementRepository.findByUserIdAndDateBetweenAndConfirmedFalseAndActiveTrue(userId, periodStart, periodEnd);
            List<Movement> oldPending = (!hIds.isEmpty()
                    ? movementRepository.findHouseholdAllPending(userId, hIds)
                    : movementRepository.findByUserIdAndConfirmedFalseAndActiveTrue(userId))
                    .stream().filter(m -> m.getDate().isBefore(periodStart)).collect(Collectors.toList());
            List<Movement> pending = new ArrayList<>(periodPending);
            pending.addAll(oldPending);

            Map<Long, Category> categoryCache = new HashMap<>();

            List<ResultMovementDTO> result = pending.stream()
                    .map(m -> {
                        ResultMovementDTO dto = movementMapper.toResultDTO(m);
                        if (dto != null) {
                            Category cat = categoryCache.computeIfAbsent(m.getCategoryId(),
                                    id -> categoryRepository.findByIdAndActiveTrue(id).orElse(null));
                            if (cat != null) {
                                dto.setCategoryName(cat.getName());
                                dto.setCategoryType(cat.getType() != null ? cat.getType().name() : null);
                                dto.setCategoryIcon(cat.getIcon());
                                dto.setCategoryColor(cat.getColor());
                            }
                        }
                        return dto;
                    })
                    .collect(Collectors.toList());

            return new ResultDTO(result);
        } catch (Exception e) {
            log.error("Error getting pending movements for user {}: {}", userId, e.getMessage(), e);
            return new ResultDTO(false, "Error getting pending movements", 500);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ResultDTO getSplitSummary(Long userId, Integer budgetStartDay, LocalDate referenceDate) {
        try {
            LocalDate ref = referenceDate != null ? referenceDate : LocalDate.now();
            LocalDate periodStart = PeriodUtils.getStartOfPeriod(budgetStartDay, ref);
            LocalDate periodEnd = PeriodUtils.getEndOfPeriod(budgetStartDay, periodStart);

            List<Long> hIds = householdService.getHouseholdIds(userId);
            Map<Long, Category> cache = new HashMap<>();

            // ALL movements in period (confirmed) - personal + shared
            List<Movement> allConfirmed = !hIds.isEmpty()
                    ? movementRepository.findHouseholdConfirmed(userId, hIds, periodStart, periodEnd)
                    : movementRepository.findByUserIdAndDateBetweenAndConfirmedTrueAndActiveTrue(userId, periodStart, periodEnd);

            // ALL pending
            List<Movement> allPending;
            if (!hIds.isEmpty()) {
                allPending = movementRepository.findHouseholdPending(userId, hIds, periodStart, periodEnd);
                List<Movement> oldPending = movementRepository.findHouseholdAllPending(userId, hIds).stream()
                        .filter(m -> m.getDate().isBefore(periodStart)).collect(Collectors.toList());
                allPending = new ArrayList<>(allPending);
                allPending.addAll(oldPending);
            } else {
                allPending = new ArrayList<>(movementRepository.findByUserIdAndDateBetweenAndConfirmedFalseAndActiveTrue(userId, periodStart, periodEnd));
                allPending.addAll(movementRepository.findByUserIdAndConfirmedFalseAndActiveTrue(userId).stream()
                        .filter(m -> m.getDate().isBefore(periodStart)).collect(Collectors.toList()));
            }

            // Split confirmed into personal vs shared, tracking transfers separately
            BigDecimal persIncome = BigDecimal.ZERO, persExpense = BigDecimal.ZERO;
            BigDecimal sharedIncome = BigDecimal.ZERO, sharedExpense = BigDecimal.ZERO;
            BigDecimal transferOut = BigDecimal.ZERO, transferIn = BigDecimal.ZERO;

            for (Movement m : allConfirmed) {
                Category cat = cache.computeIfAbsent(m.getCategoryId(),
                        id -> categoryRepository.findByIdAndActiveTrue(id).orElse(null));
                BigDecimal amt = m.getAmount() != null ? m.getAmount() : BigDecimal.ZERO;
                boolean isShared = cat != null && cat.getHouseholdId() != null;
                boolean isIncome = cat != null && cat.getType() == CategoryType.INCOME;
                boolean isTransfer = Boolean.TRUE.equals(m.getIsTransfer());

                if (isTransfer) {
                    // Transfers count in shared/personal views but NOT in total
                    if (isShared) { sharedExpense = sharedExpense.add(amt); transferOut = transferOut.add(amt); }
                    else { persIncome = persIncome.add(amt); transferIn = transferIn.add(amt); }
                } else if (isShared) {
                    if (isIncome) sharedIncome = sharedIncome.add(amt);
                    else sharedExpense = sharedExpense.add(amt);
                } else {
                    if (isIncome) persIncome = persIncome.add(amt);
                    else persExpense = persExpense.add(amt);
                }
            }

            // Split pending count
            int persPending = 0, sharedPending = 0;
            for (Movement m : allPending) {
                Category cat = cache.computeIfAbsent(m.getCategoryId(),
                        id -> categoryRepository.findByIdAndActiveTrue(id).orElse(null));
                if (cat != null && cat.getHouseholdId() != null) sharedPending++;
                else persPending++;
            }

            // Total: exclude transfers (they cancel out)
            BigDecimal totalIncome = sharedIncome.add(persIncome).subtract(transferIn);
            BigDecimal totalExpense = sharedExpense.add(persExpense).subtract(transferOut);

            DashboardSummaryDTO.SplitSummary split = DashboardSummaryDTO.SplitSummary.builder()
                    .personalIncome(persIncome).personalExpense(persExpense)
                    .personalBalance(persIncome.subtract(persExpense))
                    .sharedIncome(sharedIncome).sharedExpense(sharedExpense)
                    .sharedBalance(sharedIncome.subtract(sharedExpense))
                    .totalIncome(totalIncome).totalExpense(totalExpense)
                    .totalBalance(totalIncome.subtract(totalExpense))
                    .personalPendingCount(persPending).sharedPendingCount(sharedPending)
                    .periodStart(periodStart).periodEnd(periodEnd)
                    .build();

            return new ResultDTO(split);
        } catch (Exception e) {
            log.error("Error getting split summary: {}", e.getMessage(), e);
            return new ResultDTO(false, "Error getting split summary", 500);
        }
    }

    // --- Helpers ---

    private CategoryType getCategoryType(Long categoryId, Map<Long, Category> cache) {
        Category cat = cache.computeIfAbsent(categoryId,
                id -> categoryRepository.findByIdAndActiveTrue(id).orElse(null));
        return cat != null && cat.getType() != null ? cat.getType() : CategoryType.EXPENSE;
    }

    private BigDecimal[] calculateIncomeExpense(List<Movement> movements, Map<Long, Category> cache) {
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;
        for (Movement m : movements) {
            if (!Computables.suma(m)) continue;   // misma regla que el resto
            BigDecimal amount = Computables.importe(m);
            if (getCategoryType(m.getCategoryId(), cache) == CategoryType.INCOME) {
                income = income.add(amount);
            } else {
                expense = expense.add(amount);
            }
        }
        return new BigDecimal[]{income, expense};
    }
}

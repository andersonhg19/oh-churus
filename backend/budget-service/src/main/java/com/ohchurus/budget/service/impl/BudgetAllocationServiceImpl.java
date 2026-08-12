package com.ohchurus.budget.service.impl;

import com.ohchurus.budget.dto.output.ResultDTO;
import com.ohchurus.budget.entity.BudgetAllocation;
import com.ohchurus.budget.entity.Category;
import com.ohchurus.budget.entity.Movement;
import com.ohchurus.budget.enums.CategoryType;
import com.ohchurus.budget.repository.BudgetAllocationRepository;
import com.ohchurus.budget.repository.CategoryRepository;
import com.ohchurus.budget.repository.MovementRepository;
import com.ohchurus.budget.util.PeriodUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
public class BudgetAllocationServiceImpl {

    private final BudgetAllocationRepository allocationRepository;
    private final com.ohchurus.budget.util.ControlAcceso acceso;
    private final MovementRepository movementRepository;
    private final CategoryRepository categoryRepository;
    private final HouseholdServiceImpl householdService;

    public BudgetAllocationServiceImpl(BudgetAllocationRepository allocationRepository,
                                        MovementRepository movementRepository,
                                        CategoryRepository categoryRepository,
                                        HouseholdServiceImpl householdService,
                                       com.ohchurus.budget.util.ControlAcceso acceso) {
        this.allocationRepository = allocationRepository;
        this.movementRepository = movementRepository;
        this.categoryRepository = categoryRepository;
        this.householdService = householdService;
        this.acceso = acceso;
    }

    // ===== CRUD =====

    public ResultDTO save(Long userId, Long categoryId, BigDecimal amount, String notes,
                           Integer budgetStartDay, LocalDate referenceDate) {
        try {
            LocalDate ref = referenceDate != null ? referenceDate : LocalDate.now();
            LocalDate periodStart = PeriodUtils.getStartOfPeriod(budgetStartDay, ref);
            LocalDate periodEnd = PeriodUtils.getEndOfPeriod(budgetStartDay, periodStart);

            Optional<Category> catOpt = categoryRepository.findByIdAndActiveTrue(categoryId);
            if (catOpt.isEmpty()) return new ResultDTO(false, "Category not found", 404);

            Category cat = catOpt.get();

            // Upsert: if allocation exists for this category+period, update it
            Optional<BudgetAllocation> existing = allocationRepository
                    .findByCategoryIdAndPeriodStartAndActiveTrue(categoryId, periodStart);

            BudgetAllocation allocation;
            if (existing.isPresent()) {
                allocation = existing.get();
                allocation.setAllocatedAmount(amount);
                allocation.setNotes(notes);
            } else {
                allocation = BudgetAllocation.builder()
                        .userId(userId)
                        .categoryId(categoryId)
                        .householdId(cat.getHouseholdId())
                        .periodStart(periodStart)
                        .periodEnd(periodEnd)
                        .allocatedAmount(amount)
                        .notes(notes)
                        .status("ACTIVE")
                        .active(true)
                        .build();
            }

            BudgetAllocation saved = allocationRepository.save(allocation);
            log.info("Budget allocation saved: id={}, cat={}, amount={}", saved.getId(), categoryId, amount);
            return new ResultDTO(saved);
        } catch (Exception e) {
            log.error("Error saving budget allocation: {}", e.getMessage(), e);
            return new ResultDTO(false, "Error saving budget allocation", 500);
        }
    }

    @Transactional(readOnly = true)
    public ResultDTO list(Long userId, Integer budgetStartDay, LocalDate referenceDate) {
        try {
            LocalDate ref = referenceDate != null ? referenceDate : LocalDate.now();
            LocalDate periodStart = PeriodUtils.getStartOfPeriod(budgetStartDay, ref);

            List<Long> hIds = householdService.getHouseholdIds(userId);
            List<BudgetAllocation> allocations = !hIds.isEmpty()
                    ? allocationRepository.findAllForUserAndHousehold(userId, hIds, periodStart)
                    : allocationRepository.findByUserIdAndPeriodStartAndActiveTrue(userId, periodStart);

            // Enrich with category info
            List<Map<String, Object>> result = allocations.stream().map(a -> {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("id", a.getId());
                map.put("categoryId", a.getCategoryId());
                map.put("householdId", a.getHouseholdId());
                map.put("allocatedAmount", a.getAllocatedAmount());
                map.put("status", a.getStatus());
                map.put("notes", a.getNotes());
                map.put("periodStart", a.getPeriodStart().toString());
                map.put("periodEnd", a.getPeriodEnd().toString());

                categoryRepository.findByIdAndActiveTrue(a.getCategoryId()).ifPresent(cat -> {
                    map.put("categoryName", cat.getName());
                    map.put("categoryType", cat.getType() != null ? cat.getType().name() : null);
                    map.put("categoryIcon", cat.getIcon());
                    map.put("categoryColor", cat.getColor());
                    map.put("shared", cat.getHouseholdId() != null);
                });
                return map;
            }).collect(Collectors.toList());

            return new ResultDTO(result);
        } catch (Exception e) {
            log.error("Error listing allocations: {}", e.getMessage(), e);
            return new ResultDTO(false, "Error listing allocations", 500);
        }
    }

    public ResultDTO delete(Long id) {
        Optional<BudgetAllocation> opt = allocationRepository.findByIdAndActiveTrue(id);
        if (opt.isEmpty() || !acceso.puedeVer(opt.get().getUserId(), opt.get().getCategoryId())) {
            return new ResultDTO(false, "Allocation not found", 404);
        }
        BudgetAllocation a = opt.get();
        a.setActive(false);
        allocationRepository.save(a);
        return new ResultDTO(true, "Allocation deleted", 0);
    }

    // ===== BUDGET VS ACTUAL (VARIANZA) =====

    @Transactional(readOnly = true)
    public ResultDTO summary(Long userId, Integer budgetStartDay, LocalDate referenceDate) {
        try {
            LocalDate ref = referenceDate != null ? referenceDate : LocalDate.now();
            LocalDate periodStart = PeriodUtils.getStartOfPeriod(budgetStartDay, ref);
            LocalDate periodEnd = PeriodUtils.getEndOfPeriod(budgetStartDay, periodStart);

            List<Long> hIds = householdService.getHouseholdIds(userId);

            // Get allocations
            List<BudgetAllocation> allocations = !hIds.isEmpty()
                    ? allocationRepository.findAllForUserAndHousehold(userId, hIds, periodStart)
                    : allocationRepository.findByUserIdAndPeriodStartAndActiveTrue(userId, periodStart);

            // Get all confirmed movements in period (non-transfer, non-child for category totals)
            List<Movement> confirmed = !hIds.isEmpty()
                    ? movementRepository.findHouseholdConfirmed(userId, hIds, periodStart, periodEnd)
                    : movementRepository.findByUserIdAndDateBetweenAndConfirmedTrueAndActiveTrue(userId, periodStart, periodEnd);

            // Group actual spending by categoryId (exclude transfers)
            Map<Long, BigDecimal> actualByCategory = new HashMap<>();
            for (Movement m : confirmed) {
                if (Boolean.TRUE.equals(m.getIsTransfer())) continue;
                Category cat = categoryRepository.findByIdAndActiveTrue(m.getCategoryId()).orElse(null);
                if (cat != null && cat.getType() == CategoryType.EXPENSE) {
                    actualByCategory.merge(m.getCategoryId(), m.getAmount(), BigDecimal::add);
                }
            }

            BigDecimal totalBudgeted = BigDecimal.ZERO;
            BigDecimal totalActual = BigDecimal.ZERO;

            List<Map<String, Object>> items = new ArrayList<>();
            for (BudgetAllocation a : allocations) {
                BigDecimal budgeted = a.getAllocatedAmount();
                BigDecimal actual = actualByCategory.getOrDefault(a.getCategoryId(), BigDecimal.ZERO);
                BigDecimal variance = budgeted.subtract(actual);
                double executionPct = budgeted.compareTo(BigDecimal.ZERO) > 0
                        ? actual.multiply(new BigDecimal("100")).divide(budgeted, 1, RoundingMode.HALF_UP).doubleValue()
                        : 0;

                totalBudgeted = totalBudgeted.add(budgeted);
                totalActual = totalActual.add(actual);

                Map<String, Object> item = new LinkedHashMap<>();
                item.put("categoryId", a.getCategoryId());
                item.put("allocatedAmount", budgeted);
                item.put("actualAmount", actual);
                item.put("variance", variance);
                item.put("executionPct", executionPct);
                item.put("favorable", variance.compareTo(BigDecimal.ZERO) >= 0);

                categoryRepository.findByIdAndActiveTrue(a.getCategoryId()).ifPresent(cat -> {
                    item.put("categoryName", cat.getName());
                    item.put("categoryIcon", cat.getIcon());
                    item.put("categoryColor", cat.getColor());
                });
                items.add(item);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("periodStart", periodStart.toString());
            result.put("periodEnd", periodEnd.toString());
            result.put("totalBudgeted", totalBudgeted);
            result.put("totalActual", totalActual);
            result.put("totalVariance", totalBudgeted.subtract(totalActual));
            result.put("categories", items);

            return new ResultDTO(result);
        } catch (Exception e) {
            log.error("Error getting budget summary: {}", e.getMessage(), e);
            return new ResultDTO(false, "Error getting budget summary", 500);
        }
    }

    // ===== TRANSFER (DISPONIBILIZAR) =====

    public ResultDTO transfer(Long userId, Long fromCategoryId, Long toCategoryId,
                               BigDecimal amount, String description) {
        try {
            Optional<Category> fromCatOpt = categoryRepository.findByIdAndActiveTrue(fromCategoryId);
            Optional<Category> toCatOpt = categoryRepository.findByIdAndActiveTrue(toCategoryId);

            if (fromCatOpt.isEmpty()) return new ResultDTO(false, "Source category not found", 404);
            if (toCatOpt.isEmpty()) return new ResultDTO(false, "Destination category not found", 404);

            Category fromCat = fromCatOpt.get();
            Category toCat = toCatOpt.get();

            // Bug 1+2: Validate amount
            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                return new ResultDTO(false, "Amount must be greater than 0", 400);
            }

            if (fromCat.getHouseholdId() == null) {
                return new ResultDTO(false, "Source category must be shared (household)", 400);
            }
            if (toCat.getHouseholdId() != null) {
                return new ResultDTO(false, "Destination category must be personal", 400);
            }

            // Bug 3: Validate destination belongs to requesting user
            if (!toCat.getUserId().equals(userId)) {
                return new ResultDTO(false, "Destination category must belong to you", 403);
            }

            LocalDate today = LocalDate.now();
            String desc = description != null ? description : "Disponibilizar";

            // 1. Expense in shared category (visible to all household)
            Movement expense = Movement.builder()
                    .userId(userId)
                    .categoryId(fromCategoryId)
                    .date(today)
                    .amount(amount)
                    .description(desc)
                    .isTransfer(true)
                    .confirmed(true)
                    .active(true)
                    .build();
            Movement savedExpense = movementRepository.save(expense);

            // 2. Income in personal category (visible only to owner)
            Movement income = Movement.builder()
                    .userId(userId)
                    .categoryId(toCategoryId)
                    .date(today)
                    .amount(amount)
                    .description(desc)
                    .isTransfer(true)
                    .transferPairId(savedExpense.getId())
                    .confirmed(true)
                    .active(true)
                    .build();
            Movement savedIncome = movementRepository.save(income);

            // Link the pair
            savedExpense.setTransferPairId(savedIncome.getId());
            movementRepository.save(savedExpense);

            log.info("Transfer created: userId={}, from={} to={}, amount={}", userId, fromCategoryId, toCategoryId, amount);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("expenseId", savedExpense.getId());
            result.put("incomeId", savedIncome.getId());
            result.put("amount", amount);
            result.put("fromCategory", fromCat.getName());
            result.put("toCategory", toCat.getName());

            return new ResultDTO(result);
        } catch (Exception e) {
            log.error("Error creating transfer: {}", e.getMessage(), e);
            return new ResultDTO(false, "Error creating transfer", 500);
        }
    }

    // ===== CONSOLIDATED REPORT =====

    @Transactional(readOnly = true)
    public ResultDTO consolidated(Long userId, Integer budgetStartDay, LocalDate referenceDate) {
        try {
            LocalDate ref = referenceDate != null ? referenceDate : LocalDate.now();
            LocalDate periodStart = PeriodUtils.getStartOfPeriod(budgetStartDay, ref);
            LocalDate periodEnd = PeriodUtils.getEndOfPeriod(budgetStartDay, periodStart);

            List<Long> hIds = householdService.getHouseholdIds(userId);

            // All confirmed movements
            List<Movement> allConfirmed = !hIds.isEmpty()
                    ? movementRepository.findHouseholdConfirmed(userId, hIds, periodStart, periodEnd)
                    : movementRepository.findByUserIdAndDateBetweenAndConfirmedTrueAndActiveTrue(userId, periodStart, periodEnd);

            Map<Long, Category> catCache = new HashMap<>();

            // Classify movements
            BigDecimal sharedIncome = BigDecimal.ZERO, sharedExpense = BigDecimal.ZERO;
            BigDecimal personalIncome = BigDecimal.ZERO, personalExpense = BigDecimal.ZERO;
            BigDecimal transferOut = BigDecimal.ZERO, transferIn = BigDecimal.ZERO;

            for (Movement m : allConfirmed) {
                Category cat = catCache.computeIfAbsent(m.getCategoryId(),
                        id -> categoryRepository.findByIdAndActiveTrue(id).orElse(null));
                if (cat == null) continue;

                BigDecimal amt = m.getAmount() != null ? m.getAmount() : BigDecimal.ZERO;
                boolean isShared = cat.getHouseholdId() != null;
                boolean isIncome = cat.getType() == CategoryType.INCOME;
                boolean isTransfer = Boolean.TRUE.equals(m.getIsTransfer());

                if (isTransfer) {
                    /* Solo cuenta la pata COMPARTIDA, y cuenta por las dos.
                       La pata personal vive en el bolsillo de quien
                       disponibilizo, asi que la pareja no la ve: cuando Bruno
                       abria el consolidado veia salir 400.000 del bote comun y
                       no veia entrarlos en ninguna parte, y compartido +
                       personal no daba el total. Como el par es atomico, la
                       entrada vale exactamente lo que la salida. */
                    if (isShared) {
                        transferOut = transferOut.add(amt);
                        transferIn = transferIn.add(amt);
                    }
                } else if (isShared) {
                    if (isIncome) sharedIncome = sharedIncome.add(amt);
                    else sharedExpense = sharedExpense.add(amt);
                } else {
                    if (isIncome) personalIncome = personalIncome.add(amt);
                    else personalExpense = personalExpense.add(amt);
                }
            }

            // Budget allocations
            List<BudgetAllocation> allocations = !hIds.isEmpty()
                    ? allocationRepository.findAllForUserAndHousehold(userId, hIds, periodStart)
                    : allocationRepository.findByUserIdAndPeriodStartAndActiveTrue(userId, periodStart);

            BigDecimal totalBudgeted = allocations.stream()
                    .map(BudgetAllocation::getAllocatedAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Build report
            Map<String, Object> shared = new LinkedHashMap<>();
            shared.put("income", sharedIncome);
            shared.put("expense", sharedExpense);
            shared.put("transfersToPersonal", transferOut);
            shared.put("balance", sharedIncome.subtract(sharedExpense).subtract(transferOut));

            Map<String, Object> personal = new LinkedHashMap<>();
            personal.put("incomeFromTransfer", transferIn);
            personal.put("incomeOther", personalIncome);
            personal.put("expense", personalExpense);
            personal.put("balance", transferIn.add(personalIncome).subtract(personalExpense));

            // Total: transfers cancel out
            BigDecimal totalRealIncome = sharedIncome.add(personalIncome);
            BigDecimal totalRealExpense = sharedExpense.add(personalExpense);
            BigDecimal totalBalance = totalRealIncome.subtract(totalRealExpense);

            Map<String, Object> total = new LinkedHashMap<>();
            total.put("income", totalRealIncome);
            total.put("expense", totalRealExpense);
            total.put("balance", totalBalance);
            total.put("status", totalBalance.compareTo(BigDecimal.ZERO) >= 0 ? "SUPERAVIT" : "DEFICIT");

            Map<String, Object> budget = new LinkedHashMap<>();
            budget.put("totalBudgeted", totalBudgeted);
            budget.put("totalActualExpense", sharedExpense.add(personalExpense));
            budget.put("executionPct", totalBudgeted.compareTo(BigDecimal.ZERO) > 0
                    ? totalRealExpense.multiply(new BigDecimal("100"))
                    .divide(totalBudgeted, 1, RoundingMode.HALF_UP).doubleValue()
                    : 0);

            Map<String, Object> report = new LinkedHashMap<>();
            report.put("periodStart", periodStart.toString());
            report.put("periodEnd", periodEnd.toString());
            report.put("shared", shared);
            report.put("personal", personal);
            report.put("total", total);
            report.put("budget", budget);

            return new ResultDTO(report);
        } catch (Exception e) {
            log.error("Error getting consolidated: {}", e.getMessage(), e);
            return new ResultDTO(false, "Error getting consolidated report", 500);
        }
    }

    // ===== AUTO-CLOSE =====

    public ResultDTO autoCloseExpired() {
        try {
            LocalDate today = LocalDate.now();
            List<BudgetAllocation> expired = allocationRepository.findExpiredActive(today);
            int closed = 0;
            for (BudgetAllocation a : expired) {
                a.setStatus("CLOSED");
                allocationRepository.save(a);
                closed++;
            }
            log.info("Auto-closed {} expired budget allocations", closed);
            return new ResultDTO(true, "Closed " + closed + " allocations", 0);
        } catch (Exception e) {
            log.error("Error auto-closing: {}", e.getMessage(), e);
            return new ResultDTO(false, "Error auto-closing", 500);
        }
    }
}

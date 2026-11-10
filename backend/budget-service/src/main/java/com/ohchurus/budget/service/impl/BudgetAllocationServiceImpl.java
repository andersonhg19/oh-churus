package com.ohchurus.budget.service.impl;

import com.ohchurus.budget.dto.output.ResultDTO;
import com.ohchurus.budget.entity.BudgetAllocation;
import com.ohchurus.budget.entity.Category;
import com.ohchurus.budget.entity.Movement;
import com.ohchurus.budget.enums.CategoryType;
import com.ohchurus.budget.repository.BudgetAllocationRepository;
import com.ohchurus.budget.repository.CategoryRepository;
import com.ohchurus.budget.repository.MovementRepository;
import com.ohchurus.budget.util.Computables;
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
            /* Presupuestar sobre una categoria ajena no es una operacion que
               exista: metia una asignacion dentro de las cuentas de otro y le
               descuadraba la varianza sin que pudiera verla ni borrarla. */
            if (catOpt.isEmpty() || !acceso.puedeVerCategoria(catOpt.get())) {
                return new ResultDTO(false, "Category not found", 404);
            }

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
            List<BudgetAllocation> allocations = visibles(!hIds.isEmpty()
                    ? allocationRepository.findAllForUserAndHousehold(userId, hIds, periodStart)
                    : allocationRepository.findByUserIdAndPeriodStartAndActiveTrue(userId, periodStart), userId);

            // Enrich with category info
            List<Map<String, Object>> result = allocations.stream().map(a -> {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("id", a.getId());
                map.put("categoryId", a.getCategoryId());
                map.put("householdId", a.getHouseholdId());
                map.put("allocatedAmount", a.getAllocatedAmount());
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

    /**
     * El presupuesto no se repetia solo: cada mes habia que volver a escribir
     * las doce categorias. Esto copia las del periodo anterior a este, pero
     * SOLO donde este periodo no tiene nada: si ya subiste Mercado a 1.300.000,
     * copiar no te lo devuelve a 1.200.000.
     *
     * Se copia cada asignacion con su dueno original. Las compartidas las
     * puede copiar cualquiera del hogar; las privadas de otro ni se leen.
     */
    @Transactional
    public ResultDTO copiarDelAnterior(Long userId, Integer budgetStartDay, LocalDate referenceDate) {
        try {
            int dia = budgetStartDay != null ? budgetStartDay : 1;
            LocalDate ref = referenceDate != null ? referenceDate : LocalDate.now();
            LocalDate periodStart = PeriodUtils.getStartOfPeriod(dia, ref);
            LocalDate periodEnd = PeriodUtils.getEndOfPeriod(dia, periodStart);
            LocalDate anteriorStart = PeriodUtils.getStartOfPeriod(dia, periodStart.minusDays(1));

            List<Long> hIds = householdService.getHouseholdIds(userId);
            List<BudgetAllocation> anteriores = !hIds.isEmpty()
                    ? allocationRepository.findAllForUserAndHousehold(userId, hIds, anteriorStart)
                    : allocationRepository.findByUserIdAndPeriodStartAndActiveTrue(userId, anteriorStart);
            List<BudgetAllocation> actuales = !hIds.isEmpty()
                    ? allocationRepository.findAllForUserAndHousehold(userId, hIds, periodStart)
                    : allocationRepository.findByUserIdAndPeriodStartAndActiveTrue(userId, periodStart);
            java.util.Set<Long> yaTienen = actuales.stream().map(BudgetAllocation::getCategoryId)
                    .collect(Collectors.toSet());

            int copiadas = 0;
            for (BudgetAllocation a : anteriores) {
                if (yaTienen.contains(a.getCategoryId())) continue;
                if (categoryRepository.findByIdAndActiveTrue(a.getCategoryId()).isEmpty()) continue;
                allocationRepository.save(BudgetAllocation.builder()
                        .userId(a.getUserId())
                        .categoryId(a.getCategoryId())
                        .householdId(a.getHouseholdId())
                        .periodStart(periodStart)
                        .periodEnd(periodEnd)
                        .allocatedAmount(a.getAllocatedAmount())
                        .notes(a.getNotes())
                        .active(true)
                        .build());
                yaTienen.add(a.getCategoryId());
                copiadas++;
            }
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("periodStart", periodStart.toString());
            r.put("copied", copiadas);
            r.put("fromPeriodStart", anteriorStart.toString());
            log.info("Allocations copied: {} into {} for user {}", copiadas, periodStart, userId);
            return new ResultDTO(r);
        } catch (Exception e) {
            log.error("Error copying allocations: {}", e.getMessage(), e);
            return new ResultDTO(false, "Error copying allocations", 500);
        }
    }

    /**
     * Las asignaciones que de verdad le corresponden a quien pregunta, segun
     * como esta la categoria HOY: un ingreso no es gasto presupuestado (salia
     * sumado al total), y una categoria que la pareja volvio privada deja de
     * ensenar su presupuesto aunque la asignacion conserve el hogar.
     */
    private List<BudgetAllocation> visibles(List<BudgetAllocation> todas, Long userId) {
        List<BudgetAllocation> out = new ArrayList<>();
        Map<Long, Category> cats = new HashMap<>();
        for (BudgetAllocation a : todas) {
            Category c = cats.computeIfAbsent(a.getCategoryId(), id -> categoryRepository.findByIdAndActiveTrue(id).orElse(null));
            if (c == null || c.getType() == CategoryType.INCOME) continue;
            if (c.getHouseholdId() == null && !Objects.equals(c.getUserId(), userId)) continue;
            out.add(a);
        }
        return out;
    }
}

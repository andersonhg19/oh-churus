package com.ohchurus.budget.service.impl;

import com.ohchurus.budget.dto.output.ResultDTO;
import com.ohchurus.budget.dto.output.ResultMovementDTO;
import com.ohchurus.budget.entity.Category;
import com.ohchurus.budget.entity.Movement;
import com.ohchurus.budget.mapper.MovementMapper;
import com.ohchurus.budget.repository.CategoryRepository;
import com.ohchurus.budget.repository.MovementRepository;
import com.ohchurus.budget.service.DashboardService;
import com.ohchurus.budget.util.PeriodUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final CategoryRepository categoryRepository;
    private final MovementMapper movementMapper;
    private final HouseholdServiceImpl householdService;

    public DashboardServiceImpl(MovementRepository movementRepository,
                                 CategoryRepository categoryRepository,
                                 MovementMapper movementMapper,
                                 HouseholdServiceImpl householdService) {
        this.movementRepository = movementRepository;
        this.categoryRepository = categoryRepository;
        this.movementMapper = movementMapper;
        this.householdService = householdService;
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
                                dto.setShared(cat.getHouseholdId() != null);
                            }
                            /* El pendiente esta a nombre de quien lo paga (ver
                               crearPendiente), asi que su dueno ES el responsable. */
                            dto.setPayerName(householdService.apodoDe(m.getUserId()));
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
}

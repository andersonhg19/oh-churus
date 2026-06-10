package com.ohchurus.budget.service.impl;

import com.ohchurus.budget.dto.input.MovementFilterDTO;
import com.ohchurus.budget.dto.input.MovementSaveDTO;
import com.ohchurus.budget.dto.output.ResultDTO;
import com.ohchurus.budget.dto.output.ResultMovementDTO;
import com.ohchurus.budget.entity.Movement;
import com.ohchurus.budget.mapper.MovementMapper;
import com.ohchurus.budget.entity.Category;
import com.ohchurus.budget.repository.CategoryRepository;
import com.ohchurus.budget.repository.MovementRepository;
import com.ohchurus.budget.service.MovementService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.Collections;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
public class MovementServiceImpl implements MovementService {

    private final MovementRepository movementRepository;
    private final CategoryRepository categoryRepository;
    private final MovementMapper movementMapper;
    private final HouseholdServiceImpl householdService;

    public MovementServiceImpl(MovementRepository movementRepository,
                               CategoryRepository categoryRepository,
                               MovementMapper movementMapper,
                               HouseholdServiceImpl householdService) {
        this.movementRepository = movementRepository;
        this.categoryRepository = categoryRepository;
        this.movementMapper = movementMapper;
        this.householdService = householdService;
    }

    @Override
    public ResultDTO saveAndUpdate(MovementSaveDTO dto) {
        categoryCacheTL.remove();
        try {
            boolean isUpdate = dto.getId() != null;
            return isUpdate ? updateMovement(dto) : createMovement(dto);
        } catch (Exception e) {
            log.error("Error saving movement: {}", e.getMessage(), e);
            return new ResultDTO(false, "Error saving movement", 500);
        }
    }

    private ResultDTO createMovement(MovementSaveDTO dto) {
        if (!categoryRepository.findByIdAndActiveTrue(dto.getCategoryId()).isPresent()) {
            return new ResultDTO(false, "Category not found", 404);
        }

        // Validate parent and depth
        if (dto.getParentMovementId() != null) {
            Optional<Movement> parentOpt = movementRepository.findByIdAndActiveTrue(dto.getParentMovementId());
            if (parentOpt.isEmpty()) {
                return new ResultDTO(false, "Parent movement not found", 404);
            }
            Movement parent = parentOpt.get();
            // Check depth: if parent has a parent, we're at level 2 (max)
            if (parent.getParentMovementId() != null) {
                // Check if grandparent also has a parent (would be level 3 = too deep)
                Optional<Movement> grandparent = movementRepository.findByIdAndActiveTrue(parent.getParentMovementId());
                if (grandparent.isPresent() && grandparent.get().getParentMovementId() != null) {
                    return new ResultDTO(false, "Maximum nesting depth exceeded (max 3 levels)", 400);
                }
            }
            // Child inherits category from parent
            dto.setCategoryId(parent.getCategoryId());
        }

        Movement movement = Movement.builder()
                .userId(dto.getUserId())
                .categoryId(dto.getCategoryId())
                .date(dto.getDate())
                .amount(dto.getAmount())
                .description(dto.getDescription())
                .scheduledMovementId(dto.getScheduledMovementId())
                .parentMovementId(dto.getParentMovementId())
                .confirmed(dto.getConfirmed() != null ? dto.getConfirmed() : true)
                .active(true)
                .build();

        Movement saved = movementRepository.save(movement);
        log.info("Movement created: id={} for user {}", saved.getId(), saved.getUserId());
        return new ResultDTO(enrichWithCategory(movementMapper.toResultDTO(saved)));
    }

    private ResultDTO updateMovement(MovementSaveDTO dto) {
        Optional<Movement> existing = movementRepository.findByIdAndActiveTrue(dto.getId());
        if (existing.isEmpty()) {
            return new ResultDTO(false, "Movement not found", 404);
        }

        if (!categoryRepository.findByIdAndActiveTrue(dto.getCategoryId()).isPresent()) {
            return new ResultDTO(false, "Category not found", 404);
        }

        Movement movement = existing.get();
        // Don't change userId on shared movements - keep original creator
        Category cat = categoryRepository.findByIdAndActiveTrue(dto.getCategoryId()).orElse(null);
        if (cat != null && cat.getHouseholdId() == null) {
            // Only change userId for personal categories
            movement.setUserId(dto.getUserId());
        }
        movement.setCategoryId(dto.getCategoryId());
        movement.setDate(dto.getDate());
        movement.setAmount(dto.getAmount());
        movement.setDescription(dto.getDescription());
        movement.setScheduledMovementId(dto.getScheduledMovementId());
        if (dto.getConfirmed() != null) {
            movement.setConfirmed(dto.getConfirmed());
        }

        Movement saved = movementRepository.save(movement);
        log.info("Movement updated: id={}", saved.getId());
        return new ResultDTO(enrichWithCategory(movementMapper.toResultDTO(saved)));
    }

    @Override
    @Transactional(readOnly = true)
    public ResultDTO getById(Long id) {
        categoryCacheTL.remove();
        Optional<Movement> movement = movementRepository.findByIdAndActiveTrue(id);
        if (movement.isEmpty()) {
            return new ResultDTO(false, "Movement not found", 404);
        }
        return new ResultDTO(enrichWithCategory(movementMapper.toResultDTO(movement.get())));
    }

    @Override
    @Transactional(readOnly = true)
    public ResultDTO getAll(MovementFilterDTO filter) {
        categoryCacheTL.remove();
        Pageable pageable = PageRequest.of(filter.getPage(), filter.getSize(), Sort.by("date").descending());

        List<Long> householdIds = filter.getUserId() != null
                ? householdService.getHouseholdIds(filter.getUserId())
                : Collections.emptyList();

        Page<Movement> page;
        if (!householdIds.isEmpty()) {
            page = movementRepository.findAllWithFiltersAndHousehold(
                    filter.getUserId(), householdIds, filter.getCategoryId(),
                    filter.getStartDate(), filter.getEndDate(),
                    filter.getConfirmed(), pageable);
        } else {
            page = movementRepository.findAllWithFilters(
                    filter.getUserId(), filter.getCategoryId(),
                    filter.getStartDate(), filter.getEndDate(),
                    filter.getConfirmed(), pageable);
        }

        List<ResultMovementDTO> list = page.getContent().stream()
                .map(movementMapper::toResultDTO)
                .map(this::enrichWithCategory)
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("page", page.getNumber());
        response.put("size", page.getSize());
        response.put("totalPage", page.getTotalPages());
        response.put("totalElements", page.getTotalElements());
        response.put("list", list);

        return new ResultDTO(response);
    }

    @Override
    public ResultDTO confirm(Long id) {
        return confirmWithAmount(id, null);
    }

    @Override
    public ResultDTO confirmWithAmount(Long id, java.math.BigDecimal newAmount) {
        categoryCacheTL.remove();
        Optional<Movement> movement = movementRepository.findByIdAndActiveTrue(id);
        if (movement.isEmpty()) {
            return new ResultDTO(false, "Movement not found", 404);
        }

        Movement entity = movement.get();
        if (newAmount != null && newAmount.compareTo(java.math.BigDecimal.ZERO) > 0) {
            entity.setAmount(newAmount);
        }
        entity.setConfirmed(true);
        Movement saved = movementRepository.save(entity);
        log.info("Movement confirmed: id={}, amount={}", saved.getId(), saved.getAmount());
        return new ResultDTO(enrichWithCategory(movementMapper.toResultDTO(saved)));
    }

    @Override
    @Transactional(readOnly = true)
    public ResultDTO getByPeriod(Long userId, LocalDate startDate, LocalDate endDate) {
        categoryCacheTL.remove();
        List<Long> hIds = householdService.getHouseholdIds(userId);
        List<Movement> movements = !hIds.isEmpty()
                ? movementRepository.findHouseholdByPeriod(userId, hIds, startDate, endDate)
                : movementRepository.findByUserIdAndDateBetweenAndActiveTrue(userId, startDate, endDate);

        List<ResultMovementDTO> list = movements.stream()
                .map(movementMapper::toResultDTO)
                .map(this::enrichWithCategory)
                .collect(Collectors.toList());

        return new ResultDTO(list);
    }

    @Override
    @Transactional(readOnly = true)
    public ResultDTO getChildren(Long parentId) {
        categoryCacheTL.remove();
        Optional<Movement> parentOpt = movementRepository.findByIdAndActiveTrue(parentId);
        if (parentOpt.isEmpty()) {
            return new ResultDTO(false, "Parent movement not found", 404);
        }

        Movement parent = parentOpt.get();
        List<Movement> children = movementRepository.findByParentMovementIdAndActiveTrue(parentId);

        java.math.BigDecimal childrenTotal = children.stream()
                .map(m -> m.getAmount() != null ? m.getAmount() : java.math.BigDecimal.ZERO)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

        List<ResultMovementDTO> list = children.stream()
                .map(movementMapper::toResultDTO)
                .map(this::enrichWithCategory)
                .collect(Collectors.toList());

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("parentId", parent.getId());
        result.put("parentAmount", parent.getAmount());
        result.put("parentDescription", parent.getDescription());
        result.put("childrenTotal", childrenTotal);
        result.put("childrenCount", children.size());
        java.math.BigDecimal parentAmt = parent.getAmount() != null ? parent.getAmount() : java.math.BigDecimal.ZERO;
        result.put("remaining", parentAmt.subtract(childrenTotal));
        result.put("executionPct", parentAmt.compareTo(java.math.BigDecimal.ZERO) > 0
                ? childrenTotal.multiply(new java.math.BigDecimal("100"))
                .divide(parentAmt, 1, java.math.RoundingMode.HALF_UP).doubleValue()
                : 0);
        result.put("children", list);

        return new ResultDTO(result);
    }

    @Override
    public ResultDTO delete(Long id) {
        Optional<Movement> movement = movementRepository.findByIdAndActiveTrue(id);
        if (movement.isEmpty()) {
            return new ResultDTO(false, "Movement not found", 404);
        }

        Movement entity = movement.get();

        // If it's a transfer, delete both pair movements
        if (Boolean.TRUE.equals(entity.getIsTransfer()) && entity.getTransferPairId() != null) {
            movementRepository.findByIdAndActiveTrue(entity.getTransferPairId()).ifPresent(pair -> {
                pair.setActive(false);
                movementRepository.save(pair);
            });
        }

        entity.setActive(false);
        movementRepository.save(entity);
        log.info("Movement deleted (soft): id={}", entity.getId());
        return new ResultDTO(true, "Movement deleted successfully", 0);
    }

    // Request-scoped category cache (ThreadLocal to avoid concurrency issues)
    private static final ThreadLocal<java.util.Map<Long, com.ohchurus.budget.entity.Category>> categoryCacheTL = ThreadLocal.withInitial(java.util.HashMap::new);

    private ResultMovementDTO enrichWithCategory(ResultMovementDTO dto) {
        if (dto.getCategoryId() != null) {
            java.util.Map<Long, com.ohchurus.budget.entity.Category> cache = categoryCacheTL.get();
            com.ohchurus.budget.entity.Category cat = cache.computeIfAbsent(dto.getCategoryId(),
                    id -> categoryRepository.findByIdAndActiveTrue(id).orElse(null));
            if (cat != null) {
                dto.setCategoryName(cat.getName());
                dto.setCategoryType(cat.getType() != null ? cat.getType().name() : null);
                dto.setCategoryIcon(cat.getIcon());
                dto.setCategoryColor(cat.getColor());
            }
        }
        return dto;
    }
}

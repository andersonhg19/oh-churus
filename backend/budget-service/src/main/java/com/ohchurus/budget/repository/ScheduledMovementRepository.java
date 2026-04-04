package com.ohchurus.budget.repository;

import com.ohchurus.budget.entity.ScheduledMovement;
import com.ohchurus.budget.enums.Frequency;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ScheduledMovementRepository extends JpaRepository<ScheduledMovement, Long> {

    Optional<ScheduledMovement> findByIdAndActiveTrue(Long id);

    List<ScheduledMovement> findByUserIdAndActiveTrue(Long userId);

    @Query("SELECT s FROM ScheduledMovement s WHERE s.active = true " +
            "AND s.userId = :userId " +
            "AND s.startDate <= :endDate " +
            "AND (s.endDate IS NULL OR s.endDate >= :startDate)")
    List<ScheduledMovement> findActiveInPeriod(@Param("userId") Long userId,
                                                @Param("startDate") LocalDate startDate,
                                                @Param("endDate") LocalDate endDate);

    @Query("SELECT s FROM ScheduledMovement s WHERE s.active = true " +
            "AND (:userId IS NULL OR s.userId = :userId) " +
            "AND (:categoryId IS NULL OR s.categoryId = :categoryId) " +
            "AND (:frequency IS NULL OR s.frequency = :frequency)")
    Page<ScheduledMovement> findAllWithFilters(@Param("userId") Long userId,
                                                @Param("categoryId") Long categoryId,
                                                @Param("frequency") Frequency frequency,
                                                Pageable pageable);

    // Household-aware: scheduled movements from user + shared categories
    @Query("SELECT s FROM ScheduledMovement s WHERE s.active = true " +
            "AND (s.userId = :userId OR (s.categoryId IN " +
            "  (SELECT c.id FROM Category c WHERE c.householdId IN :householdIds AND c.active = true))) " +
            "AND (:categoryId IS NULL OR s.categoryId = :categoryId) " +
            "AND (:frequency IS NULL OR s.frequency = :frequency)")
    Page<ScheduledMovement> findAllWithFiltersAndHousehold(@Param("userId") Long userId,
                                                            @Param("householdIds") List<Long> householdIds,
                                                            @Param("categoryId") Long categoryId,
                                                            @Param("frequency") Frequency frequency,
                                                            Pageable pageable);

    @Query("SELECT s FROM ScheduledMovement s WHERE s.active = true " +
            "AND (s.userId = :userId OR (s.categoryId IN " +
            "  (SELECT c.id FROM Category c WHERE c.householdId IN :householdIds AND c.active = true)))")
    List<ScheduledMovement> findHouseholdActive(@Param("userId") Long userId,
                                                 @Param("householdIds") List<Long> householdIds);
}

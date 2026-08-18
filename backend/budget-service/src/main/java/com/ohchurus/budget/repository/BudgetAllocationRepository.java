package com.ohchurus.budget.repository;

import com.ohchurus.budget.entity.BudgetAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface BudgetAllocationRepository extends JpaRepository<BudgetAllocation, Long> {

    Optional<BudgetAllocation> findByIdAndActiveTrue(Long id);

    // Find allocation for a specific category in a specific period
    Optional<BudgetAllocation> findByCategoryIdAndPeriodStartAndActiveTrue(Long categoryId, LocalDate periodStart);

    // All allocations for a user in a period (personal only)
    List<BudgetAllocation> findByUserIdAndPeriodStartAndActiveTrueAndHouseholdIdIsNull(Long userId, LocalDate periodStart);

    // All allocations for a household in a period
    List<BudgetAllocation> findByHouseholdIdAndPeriodStartAndActiveTrue(Long householdId, LocalDate periodStart);

    // Household-aware: personal + household allocations for a user
    @Query("SELECT a FROM BudgetAllocation a WHERE a.active = true AND a.periodStart = :periodStart " +
            "AND (a.userId = :userId OR a.householdId IN :householdIds)")
    List<BudgetAllocation> findAllForUserAndHousehold(@Param("userId") Long userId,
                                                       @Param("householdIds") List<Long> householdIds,
                                                       @Param("periodStart") LocalDate periodStart);

    // Personal only for a user
    List<BudgetAllocation> findByUserIdAndPeriodStartAndActiveTrue(Long userId, LocalDate periodStart);

    /* Al expulsar a alguien del hogar, sus asignaciones sobre categorias de ese
       hogar quedaban huerfanas: seguian activas y contando en un presupuesto
       cuya categoria el expulsado ya no ve. Estas dos consultas las recogen
       para desactivarlas. */
    List<BudgetAllocation> findByUserIdAndActiveTrueAndCategoryIdIn(Long userId, List<Long> categoryIds);

    List<BudgetAllocation> findByUserIdAndHouseholdIdAndActiveTrue(Long userId, Long householdId);

    /**
     * Todas las asignaciones de una persona, de cualquier periodo.
     *
     * Sustituye a findExpiredActive, que buscaba asignaciones "vencidas" para
     * cerrarlas y solo la usaba un metodo al que no llamaba nadie.
     *
     * La necesita el arrastre de los sobres, que se RECALCULA desde el primer
     * periodo con datos en vez de guardarse. Por eso se piden todas y no solo
     * las del periodo: el disponible de este mes depende de lo que sobro en
     * todos los anteriores.
     */
    @Query("SELECT a FROM BudgetAllocation a WHERE a.active = true "
            + "AND (a.userId = :userId "
            + "     OR (a.householdId IS NOT NULL AND a.householdId IN :householdIds)) "
            + "ORDER BY a.periodStart ASC")
    List<BudgetAllocation> findTodasParaElArrastre(@Param("userId") Long userId,
                                                   @Param("householdIds") List<Long> householdIds);

    List<BudgetAllocation> findByUserIdAndActiveTrueOrderByPeriodStartAsc(Long userId);
}

package com.ohchurus.budget.repository;

import com.ohchurus.budget.entity.Movement;
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
public interface MovementRepository extends JpaRepository<Movement, Long> {

    Optional<Movement> findByIdAndActiveTrue(Long id);

    @Query("SELECT m FROM Movement m WHERE m.active = true " +
            "AND (:userId IS NULL OR m.userId = :userId) " +
            "AND (:categoryId IS NULL OR m.categoryId = :categoryId) " +
            "AND (CAST(:startDate AS java.time.LocalDate) IS NULL OR m.date >= :startDate) " +
            "AND (CAST(:endDate AS java.time.LocalDate) IS NULL OR m.date <= :endDate) " +
            "AND (:confirmed IS NULL OR m.confirmed = :confirmed) " +
            "ORDER BY m.date DESC")
    Page<Movement> findAllWithFilters(@Param("userId") Long userId,
                                      @Param("categoryId") Long categoryId,
                                      @Param("startDate") LocalDate startDate,
                                      @Param("endDate") LocalDate endDate,
                                      @Param("confirmed") Boolean confirmed,
                                      Pageable pageable);

    List<Movement> findByUserIdAndDateBetweenAndActiveTrue(Long userId, LocalDate startDate, LocalDate endDate);

    List<Movement> findByUserIdAndDateBetweenAndConfirmedTrueAndActiveTrue(Long userId, LocalDate startDate, LocalDate endDate);

    List<Movement> findByUserIdAndDateBetweenAndConfirmedFalseAndActiveTrue(Long userId, LocalDate startDate, LocalDate endDate);

    List<Movement> findByScheduledMovementIdAndConfirmedFalseAndActiveTrue(Long scheduledMovementId);

    /**
     * TODAS las ocurrencias ya generadas de un programado, vivas y muertas.
     *
     * Sin filtro de active, y es deliberado: borrar un pendiente significa
     * "omite esta ocurrencia". Antes reaparecia en el siguiente refresco del
     * panel y no habia forma de saltarse un mes; la fila desactivada es la
     * lapida que impide que resucite.
     *
     * Se traen de una vez en lugar de preguntar ocurrencia por ocurrencia
     * porque desde que las frecuencias sub-mensuales generan lo que prometen,
     * un programado diario tiene cientos de ocurrencias y el panel llama a
     * generate-pending cada vez que se abre.
     */
    List<Movement> findByScheduledMovementId(Long scheduledMovementId);

    List<Movement> findByUserIdAndConfirmedFalseAndActiveTrue(Long userId);

    boolean existsByCategoryIdAndActiveTrue(Long categoryId);

    /** Todos los movimientos vivos de una cuenta. Los filtra Computables. */
    List<Movement> findByAccountIdAndActiveTrue(Long accountId);

    /** ¿Queda algo colgando de esta cuenta? Se usa para no dejarla huerfana al borrarla. */
    boolean existsByAccountIdAndActiveTrue(Long accountId);


    // Household-aware queries: include movements from ALL household members in shared categories
    @Query("SELECT m FROM Movement m WHERE m.active = true AND m.date BETWEEN :start AND :end " +
            "AND m.confirmed = true " +
            "AND (m.userId = :userId OR (m.categoryId IN " +
            "  (SELECT c.id FROM Category c WHERE c.householdId IN :householdIds AND c.active = true)))")
    List<Movement> findHouseholdConfirmed(@Param("userId") Long userId,
                                          @Param("householdIds") List<Long> householdIds,
                                          @Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("SELECT m FROM Movement m WHERE m.active = true AND m.date BETWEEN :start AND :end " +
            "AND m.confirmed = false " +
            "AND (m.userId = :userId OR (m.categoryId IN " +
            "  (SELECT c.id FROM Category c WHERE c.householdId IN :householdIds AND c.active = true)))")
    List<Movement> findHouseholdPending(@Param("userId") Long userId,
                                        @Param("householdIds") List<Long> householdIds,
                                        @Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("SELECT m FROM Movement m WHERE m.active = true AND m.confirmed = false " +
            "AND (m.userId = :userId OR (m.categoryId IN " +
            "  (SELECT c.id FROM Category c WHERE c.householdId IN :householdIds AND c.active = true)))")
    List<Movement> findHouseholdAllPending(@Param("userId") Long userId,
                                           @Param("householdIds") List<Long> householdIds);

    // Household-aware: all movements (paginated) for listing
    @Query(value = "SELECT m FROM Movement m WHERE m.active = true " +
            "AND (m.userId = :userId OR (m.categoryId IN " +
            "  (SELECT c.id FROM Category c WHERE c.householdId IN :householdIds AND c.active = true))) " +
            "AND (:categoryId IS NULL OR m.categoryId = :categoryId) " +
            "AND (CAST(:startDate AS java.time.LocalDate) IS NULL OR m.date >= :startDate) " +
            "AND (CAST(:endDate AS java.time.LocalDate) IS NULL OR m.date <= :endDate) " +
            "AND (:confirmed IS NULL OR m.confirmed = :confirmed) " +
            "ORDER BY m.date DESC",
            countQuery = "SELECT COUNT(m) FROM Movement m WHERE m.active = true " +
            "AND (m.userId = :userId OR (m.categoryId IN " +
            "  (SELECT c.id FROM Category c WHERE c.householdId IN :householdIds AND c.active = true))) " +
            "AND (:categoryId IS NULL OR m.categoryId = :categoryId) " +
            "AND (CAST(:startDate AS java.time.LocalDate) IS NULL OR m.date >= :startDate) " +
            "AND (CAST(:endDate AS java.time.LocalDate) IS NULL OR m.date <= :endDate) " +
            "AND (:confirmed IS NULL OR m.confirmed = :confirmed)")
    Page<Movement> findAllWithFiltersAndHousehold(@Param("userId") Long userId,
                                                   @Param("householdIds") List<Long> householdIds,
                                                   @Param("categoryId") Long categoryId,
                                                   @Param("startDate") LocalDate startDate,
                                                   @Param("endDate") LocalDate endDate,
                                                   @Param("confirmed") Boolean confirmed,
                                                   Pageable pageable);

    // Household-aware: all movements by period (no pagination)
    @Query("SELECT m FROM Movement m WHERE m.active = true AND m.date BETWEEN :start AND :end " +
            "AND (m.userId = :userId OR (m.categoryId IN " +
            "  (SELECT c.id FROM Category c WHERE c.householdId IN :householdIds AND c.active = true))) " +
            "ORDER BY m.date DESC")
    List<Movement> findHouseholdByPeriod(@Param("userId") Long userId,
                                         @Param("householdIds") List<Long> householdIds,
                                         @Param("start") LocalDate start, @Param("end") LocalDate end);

    // Children of a parent movement
    List<Movement> findByParentMovementIdAndActiveTrue(Long parentMovementId);

    // Find by transferPairId
    Optional<Movement> findByTransferPairIdAndActiveTrue(Long transferPairId);
}

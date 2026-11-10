package com.ohchurus.budget.repository;

import com.ohchurus.budget.entity.Card;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CardRepository extends JpaRepository<Card, Long> {

    Optional<Card> findByIdAndActiveTrue(Long id);

    /** Las mias y las de mis hogares, ordenadas por nombre. */
    @Query("SELECT c FROM Card c WHERE c.active = true AND (c.userId = :userId "
            + "OR (c.householdId IS NOT NULL AND c.householdId IN :householdIds)) ORDER BY c.name")
    List<Card> visibles(@Param("userId") Long userId, @Param("householdIds") List<Long> householdIds);

    List<Card> findByUserIdAndActiveTrueOrderByNameAsc(Long userId);
}

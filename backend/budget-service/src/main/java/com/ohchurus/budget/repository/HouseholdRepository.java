package com.ohchurus.budget.repository;

import com.ohchurus.budget.entity.Household;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface HouseholdRepository extends JpaRepository<Household, Long> {

    Optional<Household> findByIdAndActiveTrue(Long id);
}

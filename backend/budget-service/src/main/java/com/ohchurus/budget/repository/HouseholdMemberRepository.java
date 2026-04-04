package com.ohchurus.budget.repository;

import com.ohchurus.budget.entity.HouseholdMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface HouseholdMemberRepository extends JpaRepository<HouseholdMember, Long> {

    List<HouseholdMember> findByHouseholdIdAndActiveTrue(Long householdId);

    List<HouseholdMember> findByUserIdAndActiveTrue(Long userId);

    Optional<HouseholdMember> findByHouseholdIdAndUserIdAndActiveTrue(Long householdId, Long userId);

    boolean existsByHouseholdIdAndUserIdAndActiveTrue(Long householdId, Long userId);
}

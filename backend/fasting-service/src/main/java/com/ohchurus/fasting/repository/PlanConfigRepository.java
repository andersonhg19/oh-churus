package com.ohchurus.fasting.repository;

import com.ohchurus.fasting.entity.FastingPlanConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlanConfigRepository extends JpaRepository<FastingPlanConfig, Long> {
    Optional<FastingPlanConfig> findByUserIdAndActiveTrue(Long userId);
}

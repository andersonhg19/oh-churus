package com.ohchurus.fasting.service.impl;

import com.ohchurus.fasting.entity.FastingPlanConfig;
import com.ohchurus.fasting.enums.PlanType;
import com.ohchurus.fasting.repository.PlanConfigRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LoadData implements CommandLineRunner {

    private final PlanConfigRepository planConfigRepository;

    public LoadData(PlanConfigRepository planConfigRepository) {
        this.planConfigRepository = planConfigRepository;
    }

    @Override
    public void run(String... args) {
        if (planConfigRepository.count() > 0) {
            log.info("Fasting data already exists, skipping seed");
            return;
        }

        // Default plan for Anderson (16:8)
        planConfigRepository.save(FastingPlanConfig.builder()
                .userId(3L)
                .planType(PlanType.PLAN_16_8)
                .fastingHours(16)
                .eatingHours(8)
                .suggestedStartTime("20:00")
                .remindersEnabled(false)
                .active(true)
                .build());

        log.info("Fasting seed: default 16:8 plan for Anderson");
    }
}

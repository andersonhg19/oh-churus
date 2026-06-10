package com.ohchurus.fasting.service;

import com.ohchurus.fasting.entity.FastingPlanConfig;
import com.ohchurus.fasting.enums.PlanType;
import com.ohchurus.fasting.repository.PlanConfigRepository;
import com.ohchurus.fasting.service.impl.LoadData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LoadData (fasting seed)")
class LoadDataTest {

    @Mock
    private PlanConfigRepository planConfigRepository;

    @InjectMocks
    private LoadData loadData;

    @Test
    @DisplayName("Should seed default 16:8 plan when repository is empty")
    void shouldSeedWhenEmpty() {
        when(planConfigRepository.count()).thenReturn(0L);

        loadData.run();

        verify(planConfigRepository).save(argThat((FastingPlanConfig c) ->
                c.getPlanType() == PlanType.PLAN_16_8
                        && c.getFastingHours() == 16
                        && c.getEatingHours() == 8
                        && c.getUserId() == 3L));
    }

    @Test
    @DisplayName("Should skip seeding when data already exists")
    void shouldSkipWhenNotEmpty() {
        when(planConfigRepository.count()).thenReturn(5L);

        loadData.run();

        verify(planConfigRepository, never()).save(any());
    }
}

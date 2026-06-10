package com.ohchurus.budget.service;

import com.ohchurus.budget.entity.Category;
import com.ohchurus.budget.entity.Household;
import com.ohchurus.budget.entity.HouseholdMember;
import com.ohchurus.budget.entity.Movement;
import com.ohchurus.budget.entity.ScheduledMovement;
import com.ohchurus.budget.repository.CategoryRepository;
import com.ohchurus.budget.repository.HouseholdMemberRepository;
import com.ohchurus.budget.repository.HouseholdRepository;
import com.ohchurus.budget.repository.MovementRepository;
import com.ohchurus.budget.repository.ScheduledMovementRepository;
import com.ohchurus.budget.service.impl.LoadData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoadDataTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private MovementRepository movementRepository;

    @Mock
    private ScheduledMovementRepository scheduledMovementRepository;

    @Mock
    private HouseholdRepository householdRepository;

    @Mock
    private HouseholdMemberRepository householdMemberRepository;

    @InjectMocks
    private LoadData loadData;

    @Test
    @DisplayName("Should seed data when database is empty")
    void shouldSeedWhenEmpty() {
        ReflectionTestUtils.setField(loadData, "seedDataEnabled", true);
        when(categoryRepository.count()).thenReturn(0L);
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> {
            Category cat = invocation.getArgument(0);
            cat.setId((long) (Math.random() * 10000));
            return cat;
        });
        when(householdRepository.save(any(Household.class))).thenAnswer(invocation -> {
            Household h = invocation.getArgument(0);
            h.setId(1L);
            return h;
        });
        when(householdMemberRepository.save(any(HouseholdMember.class))).thenAnswer(invocation -> {
            HouseholdMember m = invocation.getArgument(0);
            m.setId((long) (Math.random() * 10000));
            return m;
        });
        when(movementRepository.save(any(Movement.class))).thenAnswer(invocation -> {
            Movement m = invocation.getArgument(0);
            m.setId((long) (Math.random() * 10000));
            return m;
        });
        when(scheduledMovementRepository.save(any(ScheduledMovement.class))).thenAnswer(invocation -> {
            ScheduledMovement s = invocation.getArgument(0);
            s.setId((long) (Math.random() * 10000));
            return s;
        });

        loadData.run();

        // 4 users with categories: demo(25) + anderson(11) + samy(1) = 37 categories minimum
        verify(categoryRepository, atLeast(1)).save(any(Category.class));
        verify(movementRepository, atLeast(1)).save(any(Movement.class));
        verify(scheduledMovementRepository, atLeast(1)).save(any(ScheduledMovement.class));
        verify(householdRepository, atLeast(1)).save(any(Household.class));
        verify(householdMemberRepository, atLeast(1)).save(any(HouseholdMember.class));
    }

    @Test
    @DisplayName("Should skip seed when categories exist")
    void shouldSkipWhenCategoriesExist() {
        ReflectionTestUtils.setField(loadData, "seedDataEnabled", true);
        when(categoryRepository.count()).thenReturn(25L);

        loadData.run();

        verify(categoryRepository, never()).save(any(Category.class));
        verify(movementRepository, never()).save(any(Movement.class));
    }

    @Test
    @DisplayName("Should skip seed when disabled")
    void shouldSkipWhenDisabled() {
        ReflectionTestUtils.setField(loadData, "seedDataEnabled", false);

        loadData.run();

        verify(categoryRepository, never()).count();
        verify(categoryRepository, never()).save(any(Category.class));
    }
}

package com.ohchurus.budget.service;

import com.ohchurus.budget.dto.output.ResultDTO;
import com.ohchurus.budget.dto.output.ResultMovementDTO;
import com.ohchurus.budget.entity.Category;
import com.ohchurus.budget.entity.Movement;
import com.ohchurus.budget.enums.CategoryType;
import com.ohchurus.budget.mapper.MovementMapper;
import com.ohchurus.budget.repository.CategoryRepository;
import com.ohchurus.budget.repository.MovementRepository;
import com.ohchurus.budget.repository.ScheduledMovementRepository;
import com.ohchurus.budget.service.impl.DashboardServiceImpl;
import com.ohchurus.budget.service.impl.HouseholdServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DashboardServiceImplTest {

    @Mock
    private MovementRepository movementRepository;

    @Mock
    private ScheduledMovementRepository scheduledMovementRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private MovementMapper movementMapper;

    @Mock
    private ScheduledMovementService scheduledMovementService;

    @Mock
    private HouseholdServiceImpl householdService;


    @InjectMocks
    private DashboardServiceImpl dashboardService;

    private static final Long USER_ID = 1L;

    private Category incomeCategory() {
        return Category.builder()
                .id(10L).userId(USER_ID).name("Salario").type(CategoryType.INCOME)
                .icon("wallet").color("#4CAF50").active(true).build();
    }

    private Category expenseCategory() {
        return Category.builder()
                .id(20L).userId(USER_ID).name("Arriendo").type(CategoryType.EXPENSE)
                .icon("home").color("#FF0000").active(true).build();
    }

    // ===================== GET SUMMARY =====================

    // ===================== GET BY CATEGORY =====================

    // ===================== GET TREND =====================

    // ===================== GET PENDING =====================

    @Nested
    @DisplayName("GetPending")
    class GetPendingTests {

        @Test
        @DisplayName("Should return pending from current period enriched with category data")
        void shouldReturnPendingMovements() {
            when(householdService.getHouseholdIds(USER_ID)).thenReturn(Collections.emptyList());

            Movement m = Movement.builder().id(1L).userId(USER_ID).categoryId(20L)
                    .date(LocalDate.now()).amount(new BigDecimal("50000")).confirmed(false).active(true).build();
            ResultMovementDTO dto = new ResultMovementDTO();
            dto.setId(1L);
            dto.setCategoryId(20L);

            when(movementRepository.findByUserIdAndDateBetweenAndConfirmedFalseAndActiveTrue(eq(USER_ID), any(), any()))
                    .thenReturn(List.of(m));
            when(movementRepository.findByUserIdAndConfirmedFalseAndActiveTrue(USER_ID))
                    .thenReturn(List.of(m));
            when(movementMapper.toResultDTO(m)).thenReturn(dto);
            when(categoryRepository.findByIdAndActiveTrue(20L)).thenReturn(Optional.of(expenseCategory()));

            ResultDTO result = dashboardService.getPending(USER_ID, 1);
            assertTrue(result.isCorrect());

            @SuppressWarnings("unchecked")
            List<ResultMovementDTO> pending = (List<ResultMovementDTO>) result.getObject();
            assertEquals(1, pending.size());
            assertEquals("Arriendo", pending.get(0).getCategoryName());
            assertEquals("EXPENSE", pending.get(0).getCategoryType());
        }

        @Test
        @DisplayName("Should return empty when no pending")
        void shouldReturnEmptyWhenNoPending() {
            when(householdService.getHouseholdIds(USER_ID)).thenReturn(Collections.emptyList());
            when(movementRepository.findByUserIdAndDateBetweenAndConfirmedFalseAndActiveTrue(eq(USER_ID), any(), any()))
                    .thenReturn(Collections.emptyList());
            when(movementRepository.findByUserIdAndConfirmedFalseAndActiveTrue(USER_ID))
                    .thenReturn(Collections.emptyList());

            ResultDTO result = dashboardService.getPending(USER_ID, 1);
            assertTrue(result.isCorrect());

            @SuppressWarnings("unchecked")
            List<?> pending = (List<?>) result.getObject();
            assertTrue(pending.isEmpty());
        }

        @Test
        @DisplayName("Should handle exception gracefully")
        void shouldHandleException() {
            when(householdService.getHouseholdIds(USER_ID)).thenThrow(new RuntimeException("DB error"));

            ResultDTO result = dashboardService.getPending(USER_ID, 1);
            assertFalse(result.isCorrect());
            assertEquals(500, result.getErrorCode());
        }
    }

    // ===================== GET SPLIT SUMMARY =====================

    private Category sharedExpenseCategory() {
        return Category.builder()
                .id(30L).userId(USER_ID).name("Mercado").type(CategoryType.EXPENSE)
                .householdId(100L).active(true).build();
    }

    private Category sharedIncomeCategory() {
        return Category.builder()
                .id(40L).userId(USER_ID).name("Aporte").type(CategoryType.INCOME)
                .householdId(100L).active(true).build();
    }

    // ===================== HOUSEHOLD-AWARE BRANCHES =====================

    @Nested
    @DisplayName("Household-aware branches")
    class HouseholdBranchTests {

        @Test
        @DisplayName("getPending should use household queries when user belongs to households")
        void pendingUsesHouseholdQueries() {
            when(householdService.getHouseholdIds(USER_ID)).thenReturn(List.of(100L));
            when(movementRepository.findHouseholdPending(eq(USER_ID), anyList(), any(), any()))
                    .thenReturn(Collections.emptyList());
            when(movementRepository.findHouseholdAllPending(eq(USER_ID), anyList()))
                    .thenReturn(Collections.emptyList());

            ResultDTO result = dashboardService.getPending(USER_ID, 1, LocalDate.now());

            assertTrue(result.isCorrect());
            verify(movementRepository).findHouseholdPending(eq(USER_ID), anyList(), any(), any());
        }
    }
}

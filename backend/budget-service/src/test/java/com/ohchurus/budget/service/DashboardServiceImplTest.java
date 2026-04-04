package com.ohchurus.budget.service;

import com.ohchurus.budget.dto.output.DashboardSummaryDTO;
import com.ohchurus.budget.dto.output.ResultDTO;
import com.ohchurus.budget.dto.output.ResultMovementDTO;
import com.ohchurus.budget.entity.Category;
import com.ohchurus.budget.entity.Movement;
import com.ohchurus.budget.entity.ScheduledMovement;
import com.ohchurus.budget.enums.CategoryType;
import com.ohchurus.budget.mapper.MovementMapper;
import com.ohchurus.budget.repository.CategoryRepository;
import com.ohchurus.budget.repository.MovementRepository;
import com.ohchurus.budget.repository.ScheduledMovementRepository;
import com.ohchurus.budget.service.impl.DashboardServiceImpl;
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

    @InjectMocks
    private DashboardServiceImpl dashboardService;

    private static final Long USER_ID = 1L;

    private Category incomeCategory() {
        Category c = new Category();
        c.setId(10L);
        c.setType(CategoryType.INCOME);
        c.setName("Salario");
        c.setIcon("wallet");
        c.setColor("#4CAF50");
        return c;
    }

    private Category expenseCategory() {
        Category c = new Category();
        c.setId(20L);
        c.setType(CategoryType.EXPENSE);
        c.setName("Arriendo");
        c.setIcon("home");
        c.setColor("#FF0000");
        return c;
    }

    // ===================== GET SUMMARY =====================

    @Nested
    @DisplayName("GetSummary")
    class GetSummaryTests {

        @Test
        @DisplayName("Should separate income and expense correctly")
        void shouldReturnSummaryWithData() {
            Movement income1 = Movement.builder()
                    .id(1L).userId(USER_ID).categoryId(10L)
                    .amount(new BigDecimal("3500000")).confirmed(true).build();
            Movement expense1 = Movement.builder()
                    .id(2L).userId(USER_ID).categoryId(20L)
                    .amount(new BigDecimal("1500000")).confirmed(true).build();
            Movement pending1 = Movement.builder()
                    .id(3L).userId(USER_ID).categoryId(20L).date(LocalDate.now())
                    .amount(new BigDecimal("250000")).confirmed(false).build();

            when(movementRepository.findByUserIdAndDateBetweenAndConfirmedTrueAndActiveTrue(eq(USER_ID), any(), any()))
                    .thenReturn(List.of(income1, expense1));
            when(movementRepository.findByUserIdAndDateBetweenAndConfirmedFalseAndActiveTrue(eq(USER_ID), any(), any()))
                    .thenReturn(List.of(pending1));
            when(movementRepository.findByUserIdAndConfirmedFalseAndActiveTrue(USER_ID))
                    .thenReturn(Collections.emptyList());
            when(scheduledMovementRepository.findActiveInPeriod(eq(USER_ID), any(), any()))
                    .thenReturn(Collections.emptyList());
            when(categoryRepository.findByIdAndActiveTrue(10L)).thenReturn(Optional.of(incomeCategory()));
            when(categoryRepository.findByIdAndActiveTrue(20L)).thenReturn(Optional.of(expenseCategory()));

            ResultDTO result = dashboardService.getSummary(USER_ID, 1);

            assertTrue(result.isCorrect());
            DashboardSummaryDTO summary = (DashboardSummaryDTO) result.getObject();
            assertEquals(new BigDecimal("3500000"), summary.getTotalIncome());
            assertEquals(new BigDecimal("1500000"), summary.getTotalExpense());
            assertEquals(new BigDecimal("2000000"), summary.getBalance());
            assertEquals(1, summary.getPendingCount());
            assertEquals(new BigDecimal("250000"), summary.getPendingAmount());
        }

        @Test
        @DisplayName("Should return zeros when no data")
        void shouldReturnSummaryWithNoData() {
            when(movementRepository.findByUserIdAndDateBetweenAndConfirmedTrueAndActiveTrue(eq(USER_ID), any(), any()))
                    .thenReturn(Collections.emptyList());
            when(movementRepository.findByUserIdAndDateBetweenAndConfirmedFalseAndActiveTrue(eq(USER_ID), any(), any()))
                    .thenReturn(Collections.emptyList());
            when(scheduledMovementRepository.findActiveInPeriod(eq(USER_ID), any(), any()))
                    .thenReturn(Collections.emptyList());

            ResultDTO result = dashboardService.getSummary(USER_ID, 1);
            assertTrue(result.isCorrect());
            DashboardSummaryDTO summary = (DashboardSummaryDTO) result.getObject();
            assertEquals(BigDecimal.ZERO, summary.getTotalIncome());
            assertEquals(BigDecimal.ZERO, summary.getTotalExpense());
            assertEquals(BigDecimal.ZERO, summary.getBalance());
            assertEquals(BigDecimal.ZERO, summary.getBudgetTotal());
        }

        @Test
        @DisplayName("Balance should be negative when expenses exceed income")
        void shouldCalculateNegativeBalance() {
            Movement income = Movement.builder().id(1L).userId(USER_ID).categoryId(10L)
                    .amount(new BigDecimal("1000000")).confirmed(true).build();
            Movement expense = Movement.builder().id(2L).userId(USER_ID).categoryId(20L)
                    .amount(new BigDecimal("2000000")).confirmed(true).build();

            when(movementRepository.findByUserIdAndDateBetweenAndConfirmedTrueAndActiveTrue(eq(USER_ID), any(), any()))
                    .thenReturn(List.of(income, expense));
            when(movementRepository.findByUserIdAndDateBetweenAndConfirmedFalseAndActiveTrue(eq(USER_ID), any(), any()))
                    .thenReturn(Collections.emptyList());
            when(scheduledMovementRepository.findActiveInPeriod(eq(USER_ID), any(), any()))
                    .thenReturn(Collections.emptyList());
            when(categoryRepository.findByIdAndActiveTrue(10L)).thenReturn(Optional.of(incomeCategory()));
            when(categoryRepository.findByIdAndActiveTrue(20L)).thenReturn(Optional.of(expenseCategory()));

            ResultDTO result = dashboardService.getSummary(USER_ID, 1);
            DashboardSummaryDTO summary = (DashboardSummaryDTO) result.getObject();
            assertEquals(new BigDecimal("-1000000"), summary.getBalance());
        }

        @Test
        @DisplayName("BudgetTotal should only sum EXPENSE scheduled movements")
        void shouldCalculateBudgetTotalOnlyExpenses() {
            ScheduledMovement incomeScheduled = ScheduledMovement.builder()
                    .id(1L).userId(USER_ID).categoryId(10L).amount(new BigDecimal("5000000")).build();
            ScheduledMovement expenseScheduled = ScheduledMovement.builder()
                    .id(2L).userId(USER_ID).categoryId(20L).amount(new BigDecimal("1500000")).build();

            when(movementRepository.findByUserIdAndDateBetweenAndConfirmedTrueAndActiveTrue(eq(USER_ID), any(), any()))
                    .thenReturn(Collections.emptyList());
            when(movementRepository.findByUserIdAndDateBetweenAndConfirmedFalseAndActiveTrue(eq(USER_ID), any(), any()))
                    .thenReturn(Collections.emptyList());
            when(scheduledMovementRepository.findActiveInPeriod(eq(USER_ID), any(), any()))
                    .thenReturn(List.of(incomeScheduled, expenseScheduled));
            when(categoryRepository.findByIdAndActiveTrue(10L)).thenReturn(Optional.of(incomeCategory()));
            when(categoryRepository.findByIdAndActiveTrue(20L)).thenReturn(Optional.of(expenseCategory()));

            ResultDTO result = dashboardService.getSummary(USER_ID, 1);
            DashboardSummaryDTO summary = (DashboardSummaryDTO) result.getObject();
            // Solo debe sumar el programado de EXPENSE, no el de INCOME
            assertEquals(new BigDecimal("1500000"), summary.getBudgetTotal());
        }

        @Test
        @DisplayName("Should include all pending from current period plus old periods")
        void shouldIncludeAllPendingFromPeriod() {
            Movement futurePending = Movement.builder()
                    .id(1L).userId(USER_ID).categoryId(20L)
                    .date(LocalDate.now().plusDays(5))
                    .amount(new BigDecimal("100000")).confirmed(false).build();
            Movement todayPending = Movement.builder()
                    .id(2L).userId(USER_ID).categoryId(20L)
                    .date(LocalDate.now())
                    .amount(new BigDecimal("200000")).confirmed(false).build();

            when(movementRepository.findByUserIdAndDateBetweenAndConfirmedTrueAndActiveTrue(eq(USER_ID), any(), any()))
                    .thenReturn(Collections.emptyList());
            when(movementRepository.findByUserIdAndDateBetweenAndConfirmedFalseAndActiveTrue(eq(USER_ID), any(), any()))
                    .thenReturn(List.of(futurePending, todayPending));
            when(movementRepository.findByUserIdAndConfirmedFalseAndActiveTrue(USER_ID))
                    .thenReturn(Collections.emptyList());
            when(scheduledMovementRepository.findActiveInPeriod(eq(USER_ID), any(), any()))
                    .thenReturn(Collections.emptyList());

            ResultDTO result = dashboardService.getSummary(USER_ID, 1);
            DashboardSummaryDTO summary = (DashboardSummaryDTO) result.getObject();
            // Ambos cuentan: todos los del periodo actual se muestran
            assertEquals(2, summary.getPendingCount());
            assertEquals(new BigDecimal("300000"), summary.getPendingAmount());
        }

        @Test
        @DisplayName("Should handle exception gracefully")
        void shouldHandleException() {
            when(movementRepository.findByUserIdAndDateBetweenAndConfirmedTrueAndActiveTrue(eq(USER_ID), any(), any()))
                    .thenThrow(new RuntimeException("DB error"));

            ResultDTO result = dashboardService.getSummary(USER_ID, 1);
            assertFalse(result.isCorrect());
            assertEquals(500, result.getErrorCode());
        }
    }

    // ===================== GET BY CATEGORY =====================

    @Nested
    @DisplayName("GetByCategory")
    class GetByCategoryTests {

        @Test
        @DisplayName("Should group by category with type")
        void shouldGroupByCategory() {
            Movement m1 = Movement.builder().id(1L).userId(USER_ID).categoryId(10L)
                    .amount(new BigDecimal("100000")).confirmed(true).build();
            Movement m2 = Movement.builder().id(2L).userId(USER_ID).categoryId(20L)
                    .amount(new BigDecimal("200000")).confirmed(true).build();

            when(movementRepository.findByUserIdAndDateBetweenAndConfirmedTrueAndActiveTrue(eq(USER_ID), any(), any()))
                    .thenReturn(List.of(m1, m2));
            when(categoryRepository.findByIdAndActiveTrue(10L)).thenReturn(Optional.of(incomeCategory()));
            when(categoryRepository.findByIdAndActiveTrue(20L)).thenReturn(Optional.of(expenseCategory()));

            ResultDTO result = dashboardService.getByCategory(USER_ID, 1);
            assertTrue(result.isCorrect());

            @SuppressWarnings("unchecked")
            List<DashboardSummaryDTO.CategorySummary> categories =
                    (List<DashboardSummaryDTO.CategorySummary>) result.getObject();
            assertEquals(2, categories.size());
        }

        @Test
        @DisplayName("Should return empty when no movements")
        void shouldReturnEmptyWhenNoMovements() {
            when(movementRepository.findByUserIdAndDateBetweenAndConfirmedTrueAndActiveTrue(eq(USER_ID), any(), any()))
                    .thenReturn(Collections.emptyList());

            ResultDTO result = dashboardService.getByCategory(USER_ID, 1);
            assertTrue(result.isCorrect());
        }

        @Test
        @DisplayName("Should handle exception gracefully")
        void shouldHandleException() {
            when(movementRepository.findByUserIdAndDateBetweenAndConfirmedTrueAndActiveTrue(eq(USER_ID), any(), any()))
                    .thenThrow(new RuntimeException("DB error"));

            ResultDTO result = dashboardService.getByCategory(USER_ID, 1);
            assertFalse(result.isCorrect());
            assertEquals(500, result.getErrorCode());
        }
    }

    // ===================== GET TREND =====================

    @Nested
    @DisplayName("GetTrend")
    class GetTrendTests {

        @Test
        @DisplayName("Should calculate trend with income and expense")
        void shouldCalculateTrendWithBothTypes() {
            Movement currentIncome = Movement.builder().id(1L).userId(USER_ID).categoryId(10L)
                    .amount(new BigDecimal("500000")).confirmed(true).build();
            Movement currentExpense = Movement.builder().id(2L).userId(USER_ID).categoryId(20L)
                    .amount(new BigDecimal("200000")).confirmed(true).build();
            Movement previousIncome = Movement.builder().id(3L).userId(USER_ID).categoryId(10L)
                    .amount(new BigDecimal("400000")).confirmed(true).build();
            Movement previousExpense = Movement.builder().id(4L).userId(USER_ID).categoryId(20L)
                    .amount(new BigDecimal("100000")).confirmed(true).build();

            when(movementRepository.findByUserIdAndDateBetweenAndConfirmedTrueAndActiveTrue(eq(USER_ID), any(), any()))
                    .thenReturn(List.of(currentIncome, currentExpense))
                    .thenReturn(List.of(previousIncome, previousExpense));
            when(categoryRepository.findByIdAndActiveTrue(10L)).thenReturn(Optional.of(incomeCategory()));
            when(categoryRepository.findByIdAndActiveTrue(20L)).thenReturn(Optional.of(expenseCategory()));

            ResultDTO result = dashboardService.getTrend(USER_ID, 1);
            assertTrue(result.isCorrect());
            DashboardSummaryDTO.TrendDTO trend = (DashboardSummaryDTO.TrendDTO) result.getObject();
            assertEquals(new BigDecimal("500000"), trend.getCurrentIncome());
            assertEquals(new BigDecimal("200000"), trend.getCurrentExpense());
            assertEquals(new BigDecimal("400000"), trend.getPreviousIncome());
            assertEquals(new BigDecimal("100000"), trend.getPreviousExpense());
            assertNotNull(trend.getChangePercentage());
        }

        @Test
        @DisplayName("Should handle zero previous period")
        void shouldHandleZeroPreviousPeriod() {
            when(movementRepository.findByUserIdAndDateBetweenAndConfirmedTrueAndActiveTrue(eq(USER_ID), any(), any()))
                    .thenReturn(Collections.emptyList())
                    .thenReturn(Collections.emptyList());

            ResultDTO result = dashboardService.getTrend(USER_ID, 1);
            assertTrue(result.isCorrect());
            DashboardSummaryDTO.TrendDTO trend = (DashboardSummaryDTO.TrendDTO) result.getObject();
            assertEquals(BigDecimal.ZERO, trend.getChangePercentage());
        }

        @Test
        @DisplayName("Should handle exception gracefully")
        void shouldHandleException() {
            when(movementRepository.findByUserIdAndDateBetweenAndConfirmedTrueAndActiveTrue(eq(USER_ID), any(), any()))
                    .thenThrow(new RuntimeException("DB error"));

            ResultDTO result = dashboardService.getTrend(USER_ID, 1);
            assertFalse(result.isCorrect());
            assertEquals(500, result.getErrorCode());
        }
    }

    // ===================== GET PENDING =====================

    @Nested
    @DisplayName("GetPending")
    class GetPendingTests {

        @Test
        @DisplayName("Should return pending from current period enriched with category data")
        void shouldReturnPendingMovements() {
            Movement m = Movement.builder().id(1L).userId(USER_ID).categoryId(20L)
                    .date(LocalDate.now()).amount(new BigDecimal("50000")).confirmed(false).build();
            ResultMovementDTO dto = new ResultMovementDTO();
            dto.setId(1L);

            when(movementRepository.findByUserIdAndDateBetweenAndConfirmedFalseAndActiveTrue(eq(USER_ID), any(), any()))
                    .thenReturn(List.of(m));
            when(movementRepository.findByUserIdAndConfirmedFalseAndActiveTrue(USER_ID))
                    .thenReturn(Collections.emptyList());
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
        @DisplayName("Should include future pending from current period")
        void shouldIncludeFuturePendingFromCurrentPeriod() {
            Movement future = Movement.builder().id(1L).userId(USER_ID).categoryId(20L)
                    .date(LocalDate.now().plusDays(5)).amount(new BigDecimal("50000")).confirmed(false).build();
            Movement today = Movement.builder().id(2L).userId(USER_ID).categoryId(20L)
                    .date(LocalDate.now()).amount(new BigDecimal("30000")).confirmed(false).build();

            ResultMovementDTO dto1 = new ResultMovementDTO();
            dto1.setId(1L);
            ResultMovementDTO dto2 = new ResultMovementDTO();
            dto2.setId(2L);

            when(movementRepository.findByUserIdAndDateBetweenAndConfirmedFalseAndActiveTrue(eq(USER_ID), any(), any()))
                    .thenReturn(List.of(future, today));
            when(movementRepository.findByUserIdAndConfirmedFalseAndActiveTrue(USER_ID))
                    .thenReturn(Collections.emptyList());
            when(movementMapper.toResultDTO(future)).thenReturn(dto1);
            when(movementMapper.toResultDTO(today)).thenReturn(dto2);
            when(categoryRepository.findByIdAndActiveTrue(20L)).thenReturn(Optional.of(expenseCategory()));

            ResultDTO result = dashboardService.getPending(USER_ID, 1);
            assertTrue(result.isCorrect());

            @SuppressWarnings("unchecked")
            List<ResultMovementDTO> pending = (List<ResultMovementDTO>) result.getObject();
            assertEquals(2, pending.size()); // Ambos: futuro y hoy del periodo actual
        }

        @Test
        @DisplayName("Should return empty when no pending")
        void shouldReturnEmptyWhenNoPending() {
            when(movementRepository.findByUserIdAndConfirmedFalseAndActiveTrue(USER_ID))
                    .thenReturn(Collections.emptyList());

            ResultDTO result = dashboardService.getPending(USER_ID, 1);
            assertTrue(result.isCorrect());
        }

        @Test
        @DisplayName("Should handle exception gracefully")
        void shouldHandleException() {
            when(movementRepository.findByUserIdAndConfirmedFalseAndActiveTrue(USER_ID))
                    .thenThrow(new RuntimeException("DB error"));

            ResultDTO result = dashboardService.getPending(USER_ID, 1);
            assertFalse(result.isCorrect());
            assertEquals(500, result.getErrorCode());
        }
    }
}

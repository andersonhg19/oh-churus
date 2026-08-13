package com.ohchurus.budget.service;

import com.ohchurus.budget.dto.output.DashboardSummaryDTO;
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

    /* El reparto entre personas entro con la ola 3. Aqui se le dice "este
       gasto no se reparte", que es el caso de la inmensa mayoria, para poder
       seguir probando la LOGICA. Que la regla de oro se cumpla —120.000 en la
       cuenta, 40.000 en la categoria— lo demuestra LaReglaDeOroDelRepartoTest,
       que levanta la app entera con tres personas de verdad. */
    @Mock
    private com.ohchurus.budget.service.impl.RepartoDeGastos reparto;

    @BeforeEach
    void sinRepartoPorDefecto() {
        org.mockito.Mockito.lenient().when(reparto.misPartes(
                org.mockito.ArgumentMatchers.anyCollection(),
                org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> {
            java.util.Collection<com.ohchurus.budget.entity.Movement> ms = inv.getArgument(0);
            java.util.Map<Long, java.math.BigDecimal> partes = new java.util.HashMap<>();
            ms.forEach(m -> partes.put(m.getId(),
                    com.ohchurus.budget.util.Computables.importe(m)));
            return partes;
        });
        org.mockito.Mockito.lenient().when(reparto.miParte(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenAnswer(inv ->
                com.ohchurus.budget.util.Computables.importe(inv.getArgument(0)));
    }

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

    @Nested
    @DisplayName("GetSummary")
    class GetSummaryTests {

        @Test
        @DisplayName("Should separate income and expense correctly")
        void shouldReturnSummaryWithData() {
            when(householdService.getHouseholdIds(USER_ID)).thenReturn(Collections.emptyList());

            Movement income1 = Movement.builder()
                    .id(1L).userId(USER_ID).categoryId(10L)
                    .date(LocalDate.now()).amount(new BigDecimal("3500000")).confirmed(true).active(true).build();
            Movement expense1 = Movement.builder()
                    .id(2L).userId(USER_ID).categoryId(20L)
                    .date(LocalDate.now()).amount(new BigDecimal("1500000")).confirmed(true).active(true).build();
            Movement pending1 = Movement.builder()
                    .id(3L).userId(USER_ID).categoryId(20L).date(LocalDate.now())
                    .amount(new BigDecimal("250000")).confirmed(false).active(true).build();

            when(movementRepository.findByUserIdAndDateBetweenAndConfirmedTrueAndActiveTrue(eq(USER_ID), any(), any()))
                    .thenReturn(List.of(income1, expense1));
            when(movementRepository.findByUserIdAndDateBetweenAndConfirmedFalseAndActiveTrue(eq(USER_ID), any(), any()))
                    .thenReturn(List.of(pending1));
            when(movementRepository.findByUserIdAndConfirmedFalseAndActiveTrue(USER_ID))
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
            when(householdService.getHouseholdIds(USER_ID)).thenReturn(Collections.emptyList());
            when(movementRepository.findByUserIdAndDateBetweenAndConfirmedTrueAndActiveTrue(eq(USER_ID), any(), any()))
                    .thenReturn(Collections.emptyList());
            when(movementRepository.findByUserIdAndDateBetweenAndConfirmedFalseAndActiveTrue(eq(USER_ID), any(), any()))
                    .thenReturn(Collections.emptyList());
            when(movementRepository.findByUserIdAndConfirmedFalseAndActiveTrue(USER_ID))
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
            when(householdService.getHouseholdIds(USER_ID)).thenReturn(Collections.emptyList());

            Movement income = Movement.builder().id(1L).userId(USER_ID).categoryId(10L)
                    .date(LocalDate.now()).amount(new BigDecimal("1000000")).confirmed(true).active(true).build();
            Movement expense = Movement.builder().id(2L).userId(USER_ID).categoryId(20L)
                    .date(LocalDate.now()).amount(new BigDecimal("2000000")).confirmed(true).active(true).build();

            when(movementRepository.findByUserIdAndDateBetweenAndConfirmedTrueAndActiveTrue(eq(USER_ID), any(), any()))
                    .thenReturn(List.of(income, expense));
            when(movementRepository.findByUserIdAndDateBetweenAndConfirmedFalseAndActiveTrue(eq(USER_ID), any(), any()))
                    .thenReturn(Collections.emptyList());
            when(movementRepository.findByUserIdAndConfirmedFalseAndActiveTrue(USER_ID))
                    .thenReturn(Collections.emptyList());
            when(categoryRepository.findByIdAndActiveTrue(10L)).thenReturn(Optional.of(incomeCategory()));
            when(categoryRepository.findByIdAndActiveTrue(20L)).thenReturn(Optional.of(expenseCategory()));

            ResultDTO result = dashboardService.getSummary(USER_ID, 1);
            DashboardSummaryDTO summary = (DashboardSummaryDTO) result.getObject();
            assertEquals(new BigDecimal("-1000000"), summary.getBalance());
        }

        @Test
        @DisplayName("BudgetTotal should only sum EXPENSE movements (confirmed+pending), excluding children and transfers")
        void shouldCalculateBudgetTotalOnlyExpenses() {
            when(householdService.getHouseholdIds(USER_ID)).thenReturn(Collections.emptyList());

            Movement confirmedIncome = Movement.builder().id(1L).userId(USER_ID).categoryId(10L)
                    .date(LocalDate.now()).amount(new BigDecimal("5000000")).confirmed(true).active(true).build();
            Movement confirmedExpense = Movement.builder().id(2L).userId(USER_ID).categoryId(20L)
                    .date(LocalDate.now()).amount(new BigDecimal("1500000")).confirmed(true).active(true).build();
            Movement pendingExpense = Movement.builder().id(3L).userId(USER_ID).categoryId(20L)
                    .date(LocalDate.now()).amount(new BigDecimal("500000")).confirmed(false).active(true).build();

            when(movementRepository.findByUserIdAndDateBetweenAndConfirmedTrueAndActiveTrue(eq(USER_ID), any(), any()))
                    .thenReturn(List.of(confirmedIncome, confirmedExpense));
            when(movementRepository.findByUserIdAndDateBetweenAndConfirmedFalseAndActiveTrue(eq(USER_ID), any(), any()))
                    .thenReturn(List.of(pendingExpense));
            when(movementRepository.findByUserIdAndConfirmedFalseAndActiveTrue(USER_ID))
                    .thenReturn(List.of(pendingExpense));
            when(categoryRepository.findByIdAndActiveTrue(10L)).thenReturn(Optional.of(incomeCategory()));
            when(categoryRepository.findByIdAndActiveTrue(20L)).thenReturn(Optional.of(expenseCategory()));

            ResultDTO result = dashboardService.getSummary(USER_ID, 1);
            DashboardSummaryDTO summary = (DashboardSummaryDTO) result.getObject();
            // budgetTotal = confirmedExpense(1500000) + pendingExpense(500000) = 2000000
            assertEquals(new BigDecimal("2000000"), summary.getBudgetTotal());
        }

        @Test
        @DisplayName("Should include all pending from current period plus old periods")
        void shouldIncludeAllPendingFromPeriod() {
            when(householdService.getHouseholdIds(USER_ID)).thenReturn(Collections.emptyList());

            Movement pendingCurrent = Movement.builder()
                    .id(1L).userId(USER_ID).categoryId(20L)
                    .date(LocalDate.now()).amount(new BigDecimal("200000")).confirmed(false).active(true).build();
            Movement pendingOld = Movement.builder()
                    .id(2L).userId(USER_ID).categoryId(20L)
                    .date(LocalDate.now().minusMonths(2)).amount(new BigDecimal("100000")).confirmed(false).active(true).build();

            when(movementRepository.findByUserIdAndDateBetweenAndConfirmedTrueAndActiveTrue(eq(USER_ID), any(), any()))
                    .thenReturn(Collections.emptyList());
            when(movementRepository.findByUserIdAndDateBetweenAndConfirmedFalseAndActiveTrue(eq(USER_ID), any(), any()))
                    .thenReturn(List.of(pendingCurrent));
            when(movementRepository.findByUserIdAndConfirmedFalseAndActiveTrue(USER_ID))
                    .thenReturn(List.of(pendingOld, pendingCurrent));
            when(categoryRepository.findByIdAndActiveTrue(20L)).thenReturn(Optional.of(expenseCategory()));

            ResultDTO result = dashboardService.getSummary(USER_ID, 1);
            DashboardSummaryDTO summary = (DashboardSummaryDTO) result.getObject();
            assertEquals(2, summary.getPendingCount());
            assertEquals(new BigDecimal("300000"), summary.getPendingAmount());
        }

        @Test
        @DisplayName("Should handle exception gracefully")
        void shouldHandleException() {
            when(householdService.getHouseholdIds(USER_ID)).thenThrow(new RuntimeException("DB error"));

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
            when(householdService.getHouseholdIds(USER_ID)).thenReturn(Collections.emptyList());

            Movement m1 = Movement.builder().id(1L).userId(USER_ID).categoryId(10L)
                    .date(LocalDate.now()).amount(new BigDecimal("100000")).confirmed(true).active(true).build();
            Movement m2 = Movement.builder().id(2L).userId(USER_ID).categoryId(20L)
                    .date(LocalDate.now()).amount(new BigDecimal("200000")).confirmed(true).active(true).build();

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
            when(householdService.getHouseholdIds(USER_ID)).thenReturn(Collections.emptyList());
            when(movementRepository.findByUserIdAndDateBetweenAndConfirmedTrueAndActiveTrue(eq(USER_ID), any(), any()))
                    .thenReturn(Collections.emptyList());

            ResultDTO result = dashboardService.getByCategory(USER_ID, 1);
            assertTrue(result.isCorrect());

            @SuppressWarnings("unchecked")
            List<?> categories = (List<?>) result.getObject();
            assertTrue(categories.isEmpty());
        }

        @Test
        @DisplayName("Should handle exception gracefully")
        void shouldHandleException() {
            when(householdService.getHouseholdIds(USER_ID)).thenThrow(new RuntimeException("DB error"));

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
            when(householdService.getHouseholdIds(USER_ID)).thenReturn(Collections.emptyList());

            Movement currentIncome = Movement.builder().id(1L).userId(USER_ID).categoryId(10L)
                    .date(LocalDate.now()).amount(new BigDecimal("500000")).confirmed(true).active(true).build();
            Movement currentExpense = Movement.builder().id(2L).userId(USER_ID).categoryId(20L)
                    .date(LocalDate.now()).amount(new BigDecimal("200000")).confirmed(true).active(true).build();
            Movement previousIncome = Movement.builder().id(3L).userId(USER_ID).categoryId(10L)
                    .date(LocalDate.now().minusMonths(1)).amount(new BigDecimal("400000")).confirmed(true).active(true).build();
            Movement previousExpense = Movement.builder().id(4L).userId(USER_ID).categoryId(20L)
                    .date(LocalDate.now().minusMonths(1)).amount(new BigDecimal("100000")).confirmed(true).active(true).build();

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
            when(householdService.getHouseholdIds(USER_ID)).thenReturn(Collections.emptyList());
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
            when(householdService.getHouseholdIds(USER_ID)).thenThrow(new RuntimeException("DB error"));

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

    @Nested
    @DisplayName("GetSplitSummary")
    class GetSplitSummaryTests {

        @Test
        @DisplayName("Should split personal vs shared, exclude transfers from total, count pending per scope")
        void shouldSplitConfirmedAndPending() {
            when(householdService.getHouseholdIds(USER_ID)).thenReturn(Collections.emptyList());

            Movement persIncome = Movement.builder().id(1L).userId(USER_ID).categoryId(10L)
                    .date(LocalDate.now()).amount(new BigDecimal("500000")).confirmed(true).active(true).build();
            Movement persExpense = Movement.builder().id(2L).userId(USER_ID).categoryId(20L)
                    .date(LocalDate.now()).amount(new BigDecimal("200000")).confirmed(true).active(true).build();
            Movement sharedIncome = Movement.builder().id(3L).userId(USER_ID).categoryId(40L)
                    .date(LocalDate.now()).amount(new BigDecimal("300000")).confirmed(true).active(true).build();
            Movement sharedExpense = Movement.builder().id(4L).userId(USER_ID).categoryId(30L)
                    .date(LocalDate.now()).amount(new BigDecimal("100000")).confirmed(true).active(true).build();
            Movement transferOut = Movement.builder().id(5L).userId(USER_ID).categoryId(30L)
                    .date(LocalDate.now()).amount(new BigDecimal("50000")).isTransfer(true).confirmed(true).active(true).build();
            Movement transferIn = Movement.builder().id(6L).userId(USER_ID).categoryId(20L)
                    .date(LocalDate.now()).amount(new BigDecimal("50000")).isTransfer(true).confirmed(true).active(true).build();

            Movement persPending = Movement.builder().id(7L).userId(USER_ID).categoryId(20L)
                    .date(LocalDate.now()).amount(new BigDecimal("10000")).confirmed(false).active(true).build();
            Movement sharedPending = Movement.builder().id(8L).userId(USER_ID).categoryId(30L)
                    .date(LocalDate.now()).amount(new BigDecimal("20000")).confirmed(false).active(true).build();

            when(movementRepository.findByUserIdAndDateBetweenAndConfirmedTrueAndActiveTrue(eq(USER_ID), any(), any()))
                    .thenReturn(List.of(persIncome, persExpense, sharedIncome, sharedExpense, transferOut, transferIn));
            when(movementRepository.findByUserIdAndDateBetweenAndConfirmedFalseAndActiveTrue(eq(USER_ID), any(), any()))
                    .thenReturn(List.of(persPending, sharedPending));
            when(movementRepository.findByUserIdAndConfirmedFalseAndActiveTrue(USER_ID))
                    .thenReturn(Collections.emptyList());

            when(categoryRepository.findByIdAndActiveTrue(10L)).thenReturn(Optional.of(incomeCategory()));
            when(categoryRepository.findByIdAndActiveTrue(20L)).thenReturn(Optional.of(expenseCategory()));
            when(categoryRepository.findByIdAndActiveTrue(40L)).thenReturn(Optional.of(sharedIncomeCategory()));
            when(categoryRepository.findByIdAndActiveTrue(30L)).thenReturn(Optional.of(sharedExpenseCategory()));

            ResultDTO result = dashboardService.getSplitSummary(USER_ID, 1, LocalDate.now());

            assertTrue(result.isCorrect());
            DashboardSummaryDTO.SplitSummary split = (DashboardSummaryDTO.SplitSummary) result.getObject();
            // personal income = 500000 + transferIn 50000
            assertEquals(new BigDecimal("550000"), split.getPersonalIncome());
            assertEquals(new BigDecimal("200000"), split.getPersonalExpense());
            // shared expense = 100000 + transferOut 50000
            assertEquals(new BigDecimal("300000"), split.getSharedIncome());
            assertEquals(new BigDecimal("150000"), split.getSharedExpense());
            // total excludes transfers
            assertEquals(new BigDecimal("800000"), split.getTotalIncome());
            assertEquals(new BigDecimal("300000"), split.getTotalExpense());
            assertEquals(1, split.getPersonalPendingCount());
            assertEquals(1, split.getSharedPendingCount());
        }

        @Test
        @DisplayName("Should handle null amounts and missing categories as personal expense")
        void shouldHandleNullsAndMissingCategory() {
            when(householdService.getHouseholdIds(USER_ID)).thenReturn(Collections.emptyList());

            Movement nullAmount = Movement.builder().id(1L).userId(USER_ID).categoryId(99L)
                    .date(LocalDate.now()).amount(null).confirmed(true).active(true).build();
            when(movementRepository.findByUserIdAndDateBetweenAndConfirmedTrueAndActiveTrue(eq(USER_ID), any(), any()))
                    .thenReturn(List.of(nullAmount));
            when(movementRepository.findByUserIdAndDateBetweenAndConfirmedFalseAndActiveTrue(eq(USER_ID), any(), any()))
                    .thenReturn(Collections.emptyList());
            when(movementRepository.findByUserIdAndConfirmedFalseAndActiveTrue(USER_ID))
                    .thenReturn(Collections.emptyList());
            when(categoryRepository.findByIdAndActiveTrue(99L)).thenReturn(Optional.empty());

            ResultDTO result = dashboardService.getSplitSummary(USER_ID, 1, LocalDate.now());

            assertTrue(result.isCorrect());
            DashboardSummaryDTO.SplitSummary split = (DashboardSummaryDTO.SplitSummary) result.getObject();
            assertEquals(BigDecimal.ZERO, split.getPersonalExpense());
        }

        @Test
        @DisplayName("Should use household queries and include old pending when user has households")
        void shouldUseHouseholdQueries() {
            when(householdService.getHouseholdIds(USER_ID)).thenReturn(List.of(100L));

            Movement oldPending = Movement.builder().id(1L).userId(USER_ID).categoryId(30L)
                    .date(LocalDate.now().minusMonths(2)).amount(new BigDecimal("5000")).confirmed(false).active(true).build();

            when(movementRepository.findHouseholdConfirmed(eq(USER_ID), anyList(), any(), any()))
                    .thenReturn(Collections.emptyList());
            when(movementRepository.findHouseholdPending(eq(USER_ID), anyList(), any(), any()))
                    .thenReturn(Collections.emptyList());
            when(movementRepository.findHouseholdAllPending(eq(USER_ID), anyList()))
                    .thenReturn(List.of(oldPending));
            when(categoryRepository.findByIdAndActiveTrue(30L)).thenReturn(Optional.of(sharedExpenseCategory()));

            ResultDTO result = dashboardService.getSplitSummary(USER_ID, 1, LocalDate.now());

            assertTrue(result.isCorrect());
            DashboardSummaryDTO.SplitSummary split = (DashboardSummaryDTO.SplitSummary) result.getObject();
            assertEquals(1, split.getSharedPendingCount());
            verify(movementRepository).findHouseholdConfirmed(eq(USER_ID), anyList(), any(), any());
        }

        @Test
        @DisplayName("Should handle exception gracefully")
        void shouldHandleException() {
            when(householdService.getHouseholdIds(USER_ID)).thenThrow(new RuntimeException("DB error"));

            ResultDTO result = dashboardService.getSplitSummary(USER_ID, 1, LocalDate.now());
            assertFalse(result.isCorrect());
            assertEquals(500, result.getErrorCode());
        }
    }

    // ===================== HOUSEHOLD-AWARE BRANCHES =====================

    @Nested
    @DisplayName("Household-aware branches")
    class HouseholdBranchTests {

        @Test
        @DisplayName("getSummary should use household queries when user belongs to households")
        void summaryUsesHouseholdQueries() {
            when(householdService.getHouseholdIds(USER_ID)).thenReturn(List.of(100L));
            when(movementRepository.findHouseholdConfirmed(eq(USER_ID), anyList(), any(), any()))
                    .thenReturn(Collections.emptyList());
            when(movementRepository.findHouseholdPending(eq(USER_ID), anyList(), any(), any()))
                    .thenReturn(Collections.emptyList());
            when(movementRepository.findHouseholdAllPending(eq(USER_ID), anyList()))
                    .thenReturn(Collections.emptyList());

            ResultDTO result = dashboardService.getSummary(USER_ID, 1, LocalDate.now());

            assertTrue(result.isCorrect());
            verify(movementRepository).findHouseholdConfirmed(eq(USER_ID), anyList(), any(), any());
        }

        @Test
        @DisplayName("getByCategory should use household query when user belongs to households")
        void byCategoryUsesHouseholdQuery() {
            when(householdService.getHouseholdIds(USER_ID)).thenReturn(List.of(100L));
            when(movementRepository.findHouseholdConfirmed(eq(USER_ID), anyList(), any(), any()))
                    .thenReturn(Collections.emptyList());

            ResultDTO result = dashboardService.getByCategory(USER_ID, 1, LocalDate.now());

            assertTrue(result.isCorrect());
            verify(movementRepository).findHouseholdConfirmed(eq(USER_ID), anyList(), any(), any());
        }

        @Test
        @DisplayName("getTrend should use household queries when user belongs to households")
        void trendUsesHouseholdQueries() {
            when(householdService.getHouseholdIds(USER_ID)).thenReturn(List.of(100L));
            when(movementRepository.findHouseholdConfirmed(eq(USER_ID), anyList(), any(), any()))
                    .thenReturn(Collections.emptyList());

            ResultDTO result = dashboardService.getTrend(USER_ID, 1);

            assertTrue(result.isCorrect());
            verify(movementRepository, atLeastOnce()).findHouseholdConfirmed(eq(USER_ID), anyList(), any(), any());
        }

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

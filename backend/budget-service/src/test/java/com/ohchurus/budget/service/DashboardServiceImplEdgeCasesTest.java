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
@DisplayName("DashboardServiceImpl - edge cases / branches")
class DashboardServiceImplEdgeCasesTest {

    @Mock private MovementRepository movementRepository;
    @Mock private ScheduledMovementRepository scheduledMovementRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private MovementMapper movementMapper;
    @Mock private ScheduledMovementService scheduledMovementService;
    @Mock private HouseholdServiceImpl householdService;

    @InjectMocks
    private DashboardServiceImpl service;

    private static final Long USER_ID = 1L;

    private Category income() {
        return Category.builder().id(10L).userId(USER_ID).name("Salario").type(CategoryType.INCOME).active(true).build();
    }
    private Category expense() {
        return Category.builder().id(20L).userId(USER_ID).name("Arriendo").type(CategoryType.EXPENSE).active(true).build();
    }
    private Category nullType() {
        return Category.builder().id(88L).userId(USER_ID).name("SinTipo").type(null).active(true).build();
    }

    @BeforeEach
    void noHouseholds() {
        lenient().when(householdService.getHouseholdIds(USER_ID)).thenReturn(Collections.emptyList());
    }

    @Test
    @DisplayName("getSummary should skip transfers, treat null amounts as zero, exclude children/transfers from budget")
    void getSummaryEdgeMovements() {
        Movement transfer = Movement.builder().id(1L).userId(USER_ID).categoryId(20L).date(LocalDate.now())
                .amount(new BigDecimal("999")).isTransfer(true).confirmed(true).active(true).build();
        Movement incomeNull = Movement.builder().id(2L).userId(USER_ID).categoryId(10L).date(LocalDate.now())
                .amount(null).confirmed(true).active(true).build();
        Movement child = Movement.builder().id(3L).userId(USER_ID).categoryId(20L).date(LocalDate.now())
                .amount(new BigDecimal("100")).parentMovementId(50L).confirmed(true).active(true).build();
        Movement orphanCat = Movement.builder().id(4L).userId(USER_ID).categoryId(77L).date(LocalDate.now())
                .amount(new BigDecimal("50")).confirmed(true).active(true).build();
        Movement pendingNull = Movement.builder().id(5L).userId(USER_ID).categoryId(20L).date(LocalDate.now())
                .amount(null).confirmed(false).active(true).build();

        when(movementRepository.findByUserIdAndDateBetweenAndConfirmedTrueAndActiveTrue(eq(USER_ID), any(), any()))
                .thenReturn(List.of(transfer, incomeNull, child, orphanCat));
        when(movementRepository.findByUserIdAndDateBetweenAndConfirmedFalseAndActiveTrue(eq(USER_ID), any(), any()))
                .thenReturn(List.of(pendingNull));
        when(movementRepository.findByUserIdAndConfirmedFalseAndActiveTrue(USER_ID)).thenReturn(Collections.emptyList());
        when(categoryRepository.findByIdAndActiveTrue(10L)).thenReturn(Optional.of(income()));
        when(categoryRepository.findByIdAndActiveTrue(20L)).thenReturn(Optional.of(expense()));
        when(categoryRepository.findByIdAndActiveTrue(77L)).thenReturn(Optional.empty()); // cat null -> EXPENSE default

        ResultDTO r = service.getSummary(USER_ID, 1, LocalDate.now());
        assertTrue(r.isCorrect());
        DashboardSummaryDTO s = (DashboardSummaryDTO) r.getObject();
        assertEquals(BigDecimal.ZERO, s.getTotalIncome());        // incomeNull -> 0
        assertEquals(new BigDecimal("150"), s.getTotalExpense()); // child 100 + orphan 50 (transfer excluded)
        // budget = orphanCat 50 (child excluded, transfer excluded, pendingNull adds 0)
        assertEquals(new BigDecimal("50"), s.getBudgetTotal());
        assertEquals(1, s.getPendingCount());
    }

    @Test
    @DisplayName("getSummary should NOT auto-generate pending for a non-current period")
    void getSummaryNonCurrentPeriod() {
        when(movementRepository.findByUserIdAndDateBetweenAndConfirmedTrueAndActiveTrue(eq(USER_ID), any(), any()))
                .thenReturn(Collections.emptyList());
        when(movementRepository.findByUserIdAndDateBetweenAndConfirmedFalseAndActiveTrue(eq(USER_ID), any(), any()))
                .thenReturn(Collections.emptyList());
        when(movementRepository.findByUserIdAndConfirmedFalseAndActiveTrue(USER_ID)).thenReturn(Collections.emptyList());

        ResultDTO r = service.getSummary(USER_ID, 1, LocalDate.now().minusMonths(3));
        assertTrue(r.isCorrect());
        verify(scheduledMovementService, never()).generatePending(anyLong(), anyInt());
    }

    @Test
    @DisplayName("getSummary should swallow errors from auto-generate pending")
    void getSummaryGenerateThrows() {
        doThrow(new RuntimeException("gen failed")).when(scheduledMovementService).generatePending(eq(USER_ID), eq(1));
        when(movementRepository.findByUserIdAndDateBetweenAndConfirmedTrueAndActiveTrue(eq(USER_ID), any(), any()))
                .thenReturn(Collections.emptyList());
        when(movementRepository.findByUserIdAndDateBetweenAndConfirmedFalseAndActiveTrue(eq(USER_ID), any(), any()))
                .thenReturn(Collections.emptyList());
        when(movementRepository.findByUserIdAndConfirmedFalseAndActiveTrue(USER_ID)).thenReturn(Collections.emptyList());

        ResultDTO r = service.getSummary(USER_ID, 1, LocalDate.now());
        assertTrue(r.isCorrect()); // error swallowed, summary still produced
    }

    @Test
    @DisplayName("getByCategory should skip transfers and tolerate null amount / missing or untyped category")
    void getByCategoryEdge() {
        Movement transfer = Movement.builder().id(1L).userId(USER_ID).categoryId(20L).date(LocalDate.now())
                .amount(new BigDecimal("10")).isTransfer(true).confirmed(true).active(true).build();
        Movement nullAmt = Movement.builder().id(2L).userId(USER_ID).categoryId(88L).date(LocalDate.now())
                .amount(null).confirmed(true).active(true).build();
        Movement orphan = Movement.builder().id(3L).userId(USER_ID).categoryId(77L).date(LocalDate.now())
                .amount(new BigDecimal("30")).confirmed(true).active(true).build();

        when(movementRepository.findByUserIdAndDateBetweenAndConfirmedTrueAndActiveTrue(eq(USER_ID), any(), any()))
                .thenReturn(List.of(transfer, nullAmt, orphan));
        when(categoryRepository.findByIdAndActiveTrue(88L)).thenReturn(Optional.of(nullType()));
        when(categoryRepository.findByIdAndActiveTrue(77L)).thenReturn(Optional.empty());

        ResultDTO r = service.getByCategory(USER_ID, 1, LocalDate.now());
        assertTrue(r.isCorrect());
        @SuppressWarnings("unchecked")
        List<DashboardSummaryDTO.CategorySummary> cats = (List<DashboardSummaryDTO.CategorySummary>) r.getObject();
        // transfer excluded -> only categories 88 and 77 present
        assertEquals(2, cats.size());
    }

    @Test
    @DisplayName("getPending should tolerate null mapped DTO and missing/untyped category")
    void getPendingEdge() {
        Movement m1 = Movement.builder().id(1L).userId(USER_ID).categoryId(88L).date(LocalDate.now())
                .amount(new BigDecimal("10")).confirmed(false).active(true).build();
        Movement m2 = Movement.builder().id(2L).userId(USER_ID).categoryId(77L).date(LocalDate.now())
                .amount(new BigDecimal("20")).confirmed(false).active(true).build();
        ResultMovementDTO dto1 = new ResultMovementDTO(); dto1.setId(1L); dto1.setCategoryId(88L);

        when(movementRepository.findByUserIdAndDateBetweenAndConfirmedFalseAndActiveTrue(eq(USER_ID), any(), any()))
                .thenReturn(List.of(m1, m2));
        when(movementRepository.findByUserIdAndConfirmedFalseAndActiveTrue(USER_ID)).thenReturn(Collections.emptyList());
        when(movementMapper.toResultDTO(m1)).thenReturn(dto1);
        when(movementMapper.toResultDTO(m2)).thenReturn(null); // null DTO branch
        when(categoryRepository.findByIdAndActiveTrue(88L)).thenReturn(Optional.of(nullType()));

        ResultDTO r = service.getPending(USER_ID, 1, LocalDate.now());
        assertTrue(r.isCorrect());
        @SuppressWarnings("unchecked")
        List<ResultMovementDTO> list = (List<ResultMovementDTO>) r.getObject();
        assertEquals(2, list.size());
        assertNull(list.get(0).getCategoryType()); // untyped category
    }

    @Test
    @DisplayName("getSplitSummary should default reference date and tolerate missing categories in pending")
    void getSplitSummaryDefaultRefAndNullCat() {
        Movement confirmedNoCat = Movement.builder().id(1L).userId(USER_ID).categoryId(77L).date(LocalDate.now())
                .amount(new BigDecimal("40")).confirmed(true).active(true).build();
        Movement pendingNoCat = Movement.builder().id(2L).userId(USER_ID).categoryId(77L).date(LocalDate.now())
                .amount(new BigDecimal("15")).confirmed(false).active(true).build();

        when(movementRepository.findByUserIdAndDateBetweenAndConfirmedTrueAndActiveTrue(eq(USER_ID), any(), any()))
                .thenReturn(List.of(confirmedNoCat));
        when(movementRepository.findByUserIdAndDateBetweenAndConfirmedFalseAndActiveTrue(eq(USER_ID), any(), any()))
                .thenReturn(List.of(pendingNoCat));
        when(movementRepository.findByUserIdAndConfirmedFalseAndActiveTrue(USER_ID)).thenReturn(Collections.emptyList());
        when(categoryRepository.findByIdAndActiveTrue(77L)).thenReturn(Optional.empty());

        ResultDTO r = service.getSplitSummary(USER_ID, 1, null); // null reference date
        assertTrue(r.isCorrect());
        DashboardSummaryDTO.SplitSummary s = (DashboardSummaryDTO.SplitSummary) r.getObject();
        // missing category -> treated as personal expense
        assertEquals(new BigDecimal("40"), s.getPersonalExpense());
        assertEquals(1, s.getPersonalPendingCount());
    }

    @Test
    @DisplayName("getTrend should skip transfers and treat null amounts as zero")
    void getTrendEdge() {
        Movement transfer = Movement.builder().id(1L).userId(USER_ID).categoryId(20L).date(LocalDate.now())
                .amount(new BigDecimal("99")).isTransfer(true).confirmed(true).active(true).build();
        Movement nullAmt = Movement.builder().id(2L).userId(USER_ID).categoryId(20L).date(LocalDate.now())
                .amount(null).confirmed(true).active(true).build();

        when(movementRepository.findByUserIdAndDateBetweenAndConfirmedTrueAndActiveTrue(eq(USER_ID), any(), any()))
                .thenReturn(List.of(transfer, nullAmt))
                .thenReturn(Collections.emptyList());
        when(categoryRepository.findByIdAndActiveTrue(20L)).thenReturn(Optional.of(expense()));

        ResultDTO r = service.getTrend(USER_ID, 1);
        assertTrue(r.isCorrect());
        DashboardSummaryDTO.TrendDTO trend = (DashboardSummaryDTO.TrendDTO) r.getObject();
        assertEquals(BigDecimal.ZERO, trend.getCurrentExpense()); // transfer skipped, null -> 0
    }
}

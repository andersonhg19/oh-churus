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

}

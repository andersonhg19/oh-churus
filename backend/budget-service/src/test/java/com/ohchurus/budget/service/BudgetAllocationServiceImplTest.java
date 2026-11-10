package com.ohchurus.budget.service;

import com.ohchurus.budget.dto.output.ResultDTO;
import com.ohchurus.budget.entity.BudgetAllocation;
import com.ohchurus.budget.entity.Category;
import com.ohchurus.budget.entity.Movement;
import com.ohchurus.budget.enums.CategoryType;
import com.ohchurus.budget.repository.BudgetAllocationRepository;
import com.ohchurus.budget.repository.CategoryRepository;
import com.ohchurus.budget.repository.MovementRepository;
import com.ohchurus.budget.service.impl.BudgetAllocationServiceImpl;
import com.ohchurus.budget.service.impl.HouseholdServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BudgetAllocationServiceImpl")
class BudgetAllocationServiceImplTest {

    @Mock
    private BudgetAllocationRepository allocationRepository;

    @Mock
    private MovementRepository movementRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private HouseholdServiceImpl householdService;
    /* El control de acceso es una preocupacion aparte: aqui se le dice que si
       para poder probar la LOGICA. Que diga que no cuando toca lo comprueba
       AislamientoEntreUsuariosTest, que levanta la app entera con dos usuarios
       de verdad; un mock nunca podria demostrarlo. */
    @Mock
    private com.ohchurus.budget.util.ControlAcceso acceso;

    @BeforeEach
    void permitirAccesoEnLasPruebasDeLogica() {
        org.mockito.Mockito.lenient().when(acceso.puedeVer(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenReturn(true);
        org.mockito.Mockito.lenient().when(acceso.puedeVerCategoria(
                org.mockito.ArgumentMatchers.any())).thenReturn(true);
        org.mockito.Mockito.lenient().when(acceso.esMio(
                org.mockito.ArgumentMatchers.any())).thenReturn(true);
        org.mockito.Mockito.lenient().when(acceso.esDeMiHogar(
                org.mockito.ArgumentMatchers.any())).thenReturn(true);
    }
    /* La creacion toma el dueno del TOKEN, no del cuerpo: era la ultima
       puerta por la que se podia plantar una categoria o un movimiento dentro
       de la cuenta de otro. Aqui se planta la identidad para poder seguir
       probando la logica. */
    @org.junit.jupiter.api.BeforeEach
    void plantarIdentidadDelDueno() {
        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                "dueno@ohchurus.com", null, java.util.Collections.emptyList());
        auth.setDetails(1L);
        org.springframework.security.core.context.SecurityContextHolder.getContext()
                .setAuthentication(auth);
    }

    @org.junit.jupiter.api.AfterEach
    void limpiarIdentidadDelDueno() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }




    @InjectMocks
    private BudgetAllocationServiceImpl service;

    private static final Long USER_ID = 1L;
    private static final LocalDate REF = LocalDate.of(2026, 3, 15);

    private Category expenseCategory(Long id) {
        return Category.builder().id(id).userId(USER_ID).name("Arriendo")
                .type(CategoryType.EXPENSE).icon("home").color("#FF0000").active(true).build();
    }

    private Category incomeCategory(Long id) {
        return Category.builder().id(id).userId(USER_ID).name("Salario")
                .type(CategoryType.INCOME).active(true).build();
    }

    private BudgetAllocation allocation(Long id, Long categoryId, BigDecimal amount) {
        return BudgetAllocation.builder()
                .id(id).userId(USER_ID).categoryId(categoryId)
                .periodStart(LocalDate.of(2026, 3, 1)).periodEnd(LocalDate.of(2026, 3, 31))
                .allocatedAmount(amount).active(true).build();
    }

    @Nested
    @DisplayName("save")
    class SaveTests {

        @Test
        @DisplayName("Should create a new allocation when none exists for the period")
        void shouldCreateNew() {
            when(categoryRepository.findByIdAndActiveTrue(20L)).thenReturn(Optional.of(expenseCategory(20L)));
            when(allocationRepository.findByCategoryIdAndPeriodStartAndActiveTrue(eq(20L), any()))
                    .thenReturn(Optional.empty());
            when(allocationRepository.save(any(BudgetAllocation.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            ResultDTO result = service.save(USER_ID, 20L, new BigDecimal("500000"), "nota", 1, REF);

            assertTrue(result.isCorrect());
            BudgetAllocation saved = (BudgetAllocation) result.getObject();
            assertEquals(new BigDecimal("500000"), saved.getAllocatedAmount());
        }

        @Test
        @DisplayName("Should update (upsert) an existing allocation for the same category+period")
        void shouldUpsertExisting() {
            BudgetAllocation existing = allocation(5L, 20L, new BigDecimal("100000"));
            when(categoryRepository.findByIdAndActiveTrue(20L)).thenReturn(Optional.of(expenseCategory(20L)));
            when(allocationRepository.findByCategoryIdAndPeriodStartAndActiveTrue(eq(20L), any()))
                    .thenReturn(Optional.of(existing));
            when(allocationRepository.save(any(BudgetAllocation.class))).thenAnswer(inv -> inv.getArgument(0));

            ResultDTO result = service.save(USER_ID, 20L, new BigDecimal("750000"), "actualizada", 1, REF);

            assertTrue(result.isCorrect());
            assertEquals(new BigDecimal("750000"), existing.getAllocatedAmount());
            assertEquals("actualizada", existing.getNotes());
        }

        @Test
        @DisplayName("Should default reference date to today when null")
        void shouldDefaultReferenceDate() {
            when(categoryRepository.findByIdAndActiveTrue(20L)).thenReturn(Optional.of(expenseCategory(20L)));
            when(allocationRepository.findByCategoryIdAndPeriodStartAndActiveTrue(eq(20L), any()))
                    .thenReturn(Optional.empty());
            when(allocationRepository.save(any(BudgetAllocation.class))).thenAnswer(inv -> inv.getArgument(0));

            ResultDTO result = service.save(USER_ID, 20L, new BigDecimal("1000"), null, 1, null);

            assertTrue(result.isCorrect());
        }

        @Test
        @DisplayName("Should return 404 when category does not exist")
        void shouldReturnNotFound() {
            when(categoryRepository.findByIdAndActiveTrue(99L)).thenReturn(Optional.empty());

            ResultDTO result = service.save(USER_ID, 99L, new BigDecimal("500000"), null, 1, REF);

            assertFalse(result.isCorrect());
            assertEquals(404, result.getErrorCode());
        }

        @Test
        @DisplayName("Should return 500 when persistence fails")
        void shouldHandleException() {
            when(categoryRepository.findByIdAndActiveTrue(20L)).thenReturn(Optional.of(expenseCategory(20L)));
            when(allocationRepository.findByCategoryIdAndPeriodStartAndActiveTrue(eq(20L), any()))
                    .thenThrow(new RuntimeException("DB error"));

            ResultDTO result = service.save(USER_ID, 20L, new BigDecimal("500000"), null, 1, REF);

            assertFalse(result.isCorrect());
            assertEquals(500, result.getErrorCode());
        }
    }

    @Nested
    @DisplayName("list")
    class ListTests {

        @Test
        @DisplayName("Should list personal allocations enriched with category info when no household")
        @SuppressWarnings("unchecked")
        void shouldListPersonal() {
            when(householdService.getHouseholdIds(USER_ID)).thenReturn(Collections.emptyList());
            when(allocationRepository.findByUserIdAndPeriodStartAndActiveTrue(eq(USER_ID), any()))
                    .thenReturn(List.of(allocation(1L, 20L, new BigDecimal("500000"))));
            when(categoryRepository.findByIdAndActiveTrue(20L)).thenReturn(Optional.of(expenseCategory(20L)));

            ResultDTO result = service.list(USER_ID, 1, REF);

            assertTrue(result.isCorrect());
            List<Map<String, Object>> list = (List<Map<String, Object>>) result.getObject();
            assertEquals(1, list.size());
            assertEquals("Arriendo", list.get(0).get("categoryName"));
            assertEquals(false, list.get(0).get("shared"));
        }

        @Test
        @DisplayName("Should use household query when user belongs to households")
        @SuppressWarnings("unchecked")
        void shouldListHousehold() {
            when(householdService.getHouseholdIds(USER_ID)).thenReturn(List.of(100L));
            when(allocationRepository.findAllForUserAndHousehold(eq(USER_ID), anyList(), any()))
                    .thenReturn(List.of(allocation(1L, 20L, new BigDecimal("500000"))));
            when(categoryRepository.findByIdAndActiveTrue(20L)).thenReturn(Optional.of(expenseCategory(20L)));

            ResultDTO result = service.list(USER_ID, 1, REF);

            assertTrue(result.isCorrect());
            assertEquals(1, ((List<Map<String, Object>>) result.getObject()).size());
            verify(allocationRepository).findAllForUserAndHousehold(eq(USER_ID), anyList(), any());
        }

        @Test
        @DisplayName("Should return 500 when query fails")
        void shouldHandleException() {
            when(householdService.getHouseholdIds(USER_ID)).thenThrow(new RuntimeException("DB error"));

            ResultDTO result = service.list(USER_ID, 1, REF);

            assertFalse(result.isCorrect());
            assertEquals(500, result.getErrorCode());
        }
    }

    @Nested
    @DisplayName("delete")
    class DeleteTests {

        @Test
        @DisplayName("Should soft-delete an existing allocation")
        void shouldDelete() {
            BudgetAllocation a = allocation(7L, 20L, new BigDecimal("100"));
            when(allocationRepository.findByIdAndActiveTrue(7L)).thenReturn(Optional.of(a));

            ResultDTO result = service.delete(7L);

            assertTrue(result.isCorrect());
            assertFalse(a.getActive());
            verify(allocationRepository).save(a);
        }

        @Test
        @DisplayName("Should return 404 when allocation not found")
        void shouldReturnNotFound() {
            when(allocationRepository.findByIdAndActiveTrue(7L)).thenReturn(Optional.empty());

            ResultDTO result = service.delete(7L);

            assertFalse(result.isCorrect());
            assertEquals(404, result.getErrorCode());
        }
    }

    /*
     * Aqui vivian tres pruebas de autoCloseExpired(), y merecen una lapida
     * porque son un ejemplo perfecto de cobertura que no protege nada.
     *
     * Comprobaban con todo detalle que el metodo marcaba las asignaciones
     * vencidas como "CLOSED", que aguantaba una lista vacia y que devolvia 500
     * ante un fallo de base de datos. Las tres pasaban. Y las tres probaban
     * codigo que NADIE PODIA EJECUTAR: autoCloseExpired no tenia endpoint ni
     * @Scheduled, asi que ese estado "CLOSED" no se escribia jamas en
     * produccion y la unica consulta que filtraba por status = 'ACTIVE'
     * devolvia siempre todas las filas.
     *
     * Tres pruebas verdes defendiendo un concepto que no existia. Se borran
     * con el metodo y la columna (V7). Lo que de verdad hacia falta —que el
     * presupuesto del mes que viene tenga en cuenta lo del mes pasado— lo
     * resuelve el arrastre de los sobres, y lo prueba SobresTest.
     */
}

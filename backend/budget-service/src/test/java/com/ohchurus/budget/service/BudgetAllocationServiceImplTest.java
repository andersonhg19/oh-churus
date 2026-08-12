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
                .allocatedAmount(amount).status("ACTIVE").active(true).build();
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
            assertEquals("ACTIVE", saved.getStatus());
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

    @Nested
    @DisplayName("summary (budget vs actual)")
    class SummaryTests {

        @Test
        @DisplayName("Should compute variance, execution % and favorable flag per category")
        @SuppressWarnings("unchecked")
        void shouldComputeVariance() {
            when(householdService.getHouseholdIds(USER_ID)).thenReturn(Collections.emptyList());
            when(allocationRepository.findByUserIdAndPeriodStartAndActiveTrue(eq(USER_ID), any()))
                    .thenReturn(List.of(allocation(1L, 20L, new BigDecimal("1000000"))));

            Movement spent = Movement.builder().id(1L).userId(USER_ID).categoryId(20L)
                    .date(REF).amount(new BigDecimal("400000")).confirmed(true).active(true).build();
            when(movementRepository.findByUserIdAndDateBetweenAndConfirmedTrueAndActiveTrue(eq(USER_ID), any(), any()))
                    .thenReturn(List.of(spent));
            when(categoryRepository.findByIdAndActiveTrue(20L)).thenReturn(Optional.of(expenseCategory(20L)));

            ResultDTO result = service.summary(USER_ID, 1, REF);

            assertTrue(result.isCorrect());
            Map<String, Object> report = (Map<String, Object>) result.getObject();
            assertEquals(new BigDecimal("1000000"), report.get("totalBudgeted"));
            assertEquals(new BigDecimal("400000"), report.get("totalActual"));
            assertEquals(new BigDecimal("600000"), report.get("totalVariance"));

            List<Map<String, Object>> items = (List<Map<String, Object>>) report.get("categories");
            Map<String, Object> item = items.get(0);
            assertEquals(new BigDecimal("600000"), item.get("variance"));
            assertEquals(40.0, item.get("executionPct"));
            assertEquals(true, item.get("favorable"));
            assertEquals("Arriendo", item.get("categoryName"));
        }

        @Test
        @DisplayName("Should flag unfavorable when actual exceeds budget and ignore transfers / income")
        @SuppressWarnings("unchecked")
        void shouldFlagUnfavorableAndIgnoreTransfers() {
            when(householdService.getHouseholdIds(USER_ID)).thenReturn(Collections.emptyList());
            when(allocationRepository.findByUserIdAndPeriodStartAndActiveTrue(eq(USER_ID), any()))
                    .thenReturn(List.of(allocation(1L, 20L, new BigDecimal("100000"))));

            Movement overspend = Movement.builder().id(1L).userId(USER_ID).categoryId(20L)
                    .date(REF).amount(new BigDecimal("150000")).confirmed(true).active(true).build();
            Movement transfer = Movement.builder().id(2L).userId(USER_ID).categoryId(20L)
                    .date(REF).amount(new BigDecimal("999")).isTransfer(true).confirmed(true).active(true).build();
            Movement income = Movement.builder().id(3L).userId(USER_ID).categoryId(10L)
                    .date(REF).amount(new BigDecimal("777")).confirmed(true).active(true).build();

            when(movementRepository.findByUserIdAndDateBetweenAndConfirmedTrueAndActiveTrue(eq(USER_ID), any(), any()))
                    .thenReturn(List.of(overspend, transfer, income));
            when(categoryRepository.findByIdAndActiveTrue(20L)).thenReturn(Optional.of(expenseCategory(20L)));
            when(categoryRepository.findByIdAndActiveTrue(10L)).thenReturn(Optional.of(incomeCategory(10L)));

            ResultDTO result = service.summary(USER_ID, 1, REF);

            Map<String, Object> report = (Map<String, Object>) result.getObject();
            List<Map<String, Object>> items = (List<Map<String, Object>>) report.get("categories");
            Map<String, Object> item = items.get(0);
            // actual only counts the EXPENSE non-transfer movement (150000)
            assertEquals(new BigDecimal("150000"), item.get("actualAmount"));
            assertEquals(new BigDecimal("-50000"), item.get("variance"));
            assertEquals(false, item.get("favorable"));
        }

        @Test
        @DisplayName("Should use household movements when user belongs to households")
        void shouldUseHouseholdMovements() {
            when(householdService.getHouseholdIds(USER_ID)).thenReturn(List.of(100L));
            when(allocationRepository.findAllForUserAndHousehold(eq(USER_ID), anyList(), any()))
                    .thenReturn(Collections.emptyList());
            when(movementRepository.findHouseholdConfirmed(eq(USER_ID), anyList(), any(), any()))
                    .thenReturn(Collections.emptyList());

            ResultDTO result = service.summary(USER_ID, 1, REF);

            assertTrue(result.isCorrect());
            verify(movementRepository).findHouseholdConfirmed(eq(USER_ID), anyList(), any(), any());
        }

        @Test
        @DisplayName("Should return 500 when query fails")
        void shouldHandleException() {
            when(householdService.getHouseholdIds(USER_ID)).thenThrow(new RuntimeException("DB error"));

            ResultDTO result = service.summary(USER_ID, 1, REF);

            assertFalse(result.isCorrect());
            assertEquals(500, result.getErrorCode());
        }
    }

    @Nested
    @DisplayName("transfer")
    class TransferTests {

        private Category sharedCategory(Long id) {
            return Category.builder().id(id).userId(USER_ID).name("Compartida")
                    .type(CategoryType.EXPENSE).householdId(100L).active(true).build();
        }

        @Test
        @DisplayName("Should create the expense/income pair and link them")
        @SuppressWarnings("unchecked")
        void shouldCreateTransfer() {
            when(categoryRepository.findByIdAndActiveTrue(20L)).thenReturn(Optional.of(sharedCategory(20L)));
            when(categoryRepository.findByIdAndActiveTrue(30L)).thenReturn(Optional.of(expenseCategory(30L)));
            when(movementRepository.save(any(Movement.class)))
                    .thenAnswer(inv -> {
                        Movement m = inv.getArgument(0);
                        if (m.getId() == null) m.setId(m.getTransferPairId() == null ? 101L : 102L);
                        return m;
                    });

            ResultDTO result = service.transfer(USER_ID, 20L, 30L, new BigDecimal("50000"), "Disponibilizar");

            assertTrue(result.isCorrect());
            Map<String, Object> data = (Map<String, Object>) result.getObject();
            assertEquals(new BigDecimal("50000"), data.get("amount"));
            // expense + income + relink = 3 saves
            verify(movementRepository, times(3)).save(any(Movement.class));
        }

        @Test
        @DisplayName("Should default description when null")
        void shouldDefaultDescription() {
            when(categoryRepository.findByIdAndActiveTrue(20L)).thenReturn(Optional.of(sharedCategory(20L)));
            when(categoryRepository.findByIdAndActiveTrue(30L)).thenReturn(Optional.of(expenseCategory(30L)));
            when(movementRepository.save(any(Movement.class))).thenAnswer(inv -> {
                Movement m = inv.getArgument(0);
                if (m.getId() == null) m.setId(101L);
                return m;
            });

            ResultDTO result = service.transfer(USER_ID, 20L, 30L, new BigDecimal("50000"), null);

            assertTrue(result.isCorrect());
            verify(movementRepository, atLeastOnce()).save(argThat(m -> "Disponibilizar".equals(m.getDescription())));
        }

        @Test
        @DisplayName("Should return 404 when source category not found")
        void shouldRejectMissingSource() {
            when(categoryRepository.findByIdAndActiveTrue(20L)).thenReturn(Optional.empty());
            when(categoryRepository.findByIdAndActiveTrue(30L)).thenReturn(Optional.of(expenseCategory(30L)));

            ResultDTO result = service.transfer(USER_ID, 20L, 30L, new BigDecimal("50000"), null);

            assertFalse(result.isCorrect());
            assertEquals(404, result.getErrorCode());
        }

        @Test
        @DisplayName("Should return 404 when destination category not found")
        void shouldRejectMissingDestination() {
            when(categoryRepository.findByIdAndActiveTrue(20L)).thenReturn(Optional.of(sharedCategory(20L)));
            when(categoryRepository.findByIdAndActiveTrue(30L)).thenReturn(Optional.empty());

            ResultDTO result = service.transfer(USER_ID, 20L, 30L, new BigDecimal("50000"), null);

            assertFalse(result.isCorrect());
            assertEquals(404, result.getErrorCode());
        }

        @Test
        @DisplayName("Should reject non-positive amount")
        void shouldRejectInvalidAmount() {
            when(categoryRepository.findByIdAndActiveTrue(20L)).thenReturn(Optional.of(sharedCategory(20L)));
            when(categoryRepository.findByIdAndActiveTrue(30L)).thenReturn(Optional.of(expenseCategory(30L)));

            ResultDTO zero = service.transfer(USER_ID, 20L, 30L, BigDecimal.ZERO, null);
            assertFalse(zero.isCorrect());
            assertEquals(400, zero.getErrorCode());

            ResultDTO nullAmount = service.transfer(USER_ID, 20L, 30L, null, null);
            assertFalse(nullAmount.isCorrect());
            assertEquals(400, nullAmount.getErrorCode());
        }

        @Test
        @DisplayName("Should reject when source is not a shared category")
        void shouldRejectPersonalSource() {
            when(categoryRepository.findByIdAndActiveTrue(20L)).thenReturn(Optional.of(expenseCategory(20L)));
            when(categoryRepository.findByIdAndActiveTrue(30L)).thenReturn(Optional.of(expenseCategory(30L)));

            ResultDTO result = service.transfer(USER_ID, 20L, 30L, new BigDecimal("50000"), null);

            assertFalse(result.isCorrect());
            assertEquals(400, result.getErrorCode());
        }

        @Test
        @DisplayName("Should reject when destination is a shared category")
        void shouldRejectSharedDestination() {
            when(categoryRepository.findByIdAndActiveTrue(20L)).thenReturn(Optional.of(sharedCategory(20L)));
            when(categoryRepository.findByIdAndActiveTrue(30L)).thenReturn(Optional.of(sharedCategory(30L)));

            ResultDTO result = service.transfer(USER_ID, 20L, 30L, new BigDecimal("50000"), null);

            assertFalse(result.isCorrect());
            assertEquals(400, result.getErrorCode());
        }

        @Test
        @DisplayName("Should reject when destination belongs to another user")
        void shouldRejectForeignDestination() {
            Category foreign = Category.builder().id(30L).userId(999L).name("Otra")
                    .type(CategoryType.EXPENSE).active(true).build();
            when(categoryRepository.findByIdAndActiveTrue(20L)).thenReturn(Optional.of(sharedCategory(20L)));
            when(categoryRepository.findByIdAndActiveTrue(30L)).thenReturn(Optional.of(foreign));

            ResultDTO result = service.transfer(USER_ID, 20L, 30L, new BigDecimal("50000"), null);

            assertFalse(result.isCorrect());
            assertEquals(403, result.getErrorCode());
        }

        @Test
        @DisplayName("Should return 500 when persistence fails")
        void shouldHandleException() {
            when(categoryRepository.findByIdAndActiveTrue(20L)).thenReturn(Optional.of(sharedCategory(20L)));
            when(categoryRepository.findByIdAndActiveTrue(30L)).thenReturn(Optional.of(expenseCategory(30L)));
            when(movementRepository.save(any(Movement.class))).thenThrow(new RuntimeException("DB error"));

            ResultDTO result = service.transfer(USER_ID, 20L, 30L, new BigDecimal("50000"), null);

            assertFalse(result.isCorrect());
            assertEquals(500, result.getErrorCode());
        }
    }

    @Nested
    @DisplayName("consolidated")
    class ConsolidatedTests {

        private Category sharedIncome(Long id) {
            return Category.builder().id(id).userId(USER_ID).name("Aporte")
                    .type(CategoryType.INCOME).householdId(100L).active(true).build();
        }

        private Category sharedExpense(Long id) {
            return Category.builder().id(id).userId(USER_ID).name("Mercado")
                    .type(CategoryType.EXPENSE).householdId(100L).active(true).build();
        }

        @Test
        @DisplayName("Should classify shared/personal income, expense and transfers and report SUPERAVIT")
        @SuppressWarnings("unchecked")
        void shouldClassifyAndReportSurplus() {
            when(householdService.getHouseholdIds(USER_ID)).thenReturn(Collections.emptyList());

            Movement sIncome = Movement.builder().id(1L).categoryId(40L).userId(USER_ID)
                    .date(REF).amount(new BigDecimal("1000000")).confirmed(true).active(true).build();
            Movement sExpense = Movement.builder().id(2L).categoryId(41L).userId(USER_ID)
                    .date(REF).amount(new BigDecimal("300000")).confirmed(true).active(true).build();
            Movement pIncome = Movement.builder().id(3L).categoryId(10L).userId(USER_ID)
                    .date(REF).amount(new BigDecimal("500000")).confirmed(true).active(true).build();
            Movement pExpense = Movement.builder().id(4L).categoryId(20L).userId(USER_ID)
                    .date(REF).amount(new BigDecimal("200000")).confirmed(true).active(true).build();
            Movement transferShared = Movement.builder().id(5L).categoryId(41L).userId(USER_ID)
                    .date(REF).amount(new BigDecimal("50000")).isTransfer(true).confirmed(true).active(true).build();
            Movement transferPersonal = Movement.builder().id(6L).categoryId(20L).userId(USER_ID)
                    .date(REF).amount(new BigDecimal("50000")).isTransfer(true).confirmed(true).active(true).build();
            Movement nullAmount = Movement.builder().id(7L).categoryId(20L).userId(USER_ID)
                    .date(REF).amount(null).confirmed(true).active(true).build();

            when(movementRepository.findByUserIdAndDateBetweenAndConfirmedTrueAndActiveTrue(eq(USER_ID), any(), any()))
                    .thenReturn(List.of(sIncome, sExpense, pIncome, pExpense, transferShared, transferPersonal, nullAmount));
            when(allocationRepository.findByUserIdAndPeriodStartAndActiveTrue(eq(USER_ID), any()))
                    .thenReturn(List.of(allocation(1L, 41L, new BigDecimal("400000"))));

            when(categoryRepository.findByIdAndActiveTrue(40L)).thenReturn(Optional.of(sharedIncome(40L)));
            when(categoryRepository.findByIdAndActiveTrue(41L)).thenReturn(Optional.of(sharedExpense(41L)));
            when(categoryRepository.findByIdAndActiveTrue(10L)).thenReturn(Optional.of(incomeCategory(10L)));
            when(categoryRepository.findByIdAndActiveTrue(20L)).thenReturn(Optional.of(expenseCategory(20L)));

            ResultDTO result = service.consolidated(USER_ID, 1, REF);

            assertTrue(result.isCorrect());
            Map<String, Object> report = (Map<String, Object>) result.getObject();
            Map<String, Object> total = (Map<String, Object>) report.get("total");
            // total income = sharedIncome 1,000,000 + personalIncome 500,000
            assertEquals(new BigDecimal("1500000"), total.get("income"));
            // total expense = sharedExpense 300,000 + personalExpense 200,000 (nullAmount adds 0)
            assertEquals(new BigDecimal("500000"), total.get("expense"));
            assertEquals("SUPERAVIT", total.get("status"));

            Map<String, Object> budget = (Map<String, Object>) report.get("budget");
            assertEquals(new BigDecimal("400000"), budget.get("totalBudgeted"));
        }

        @Test
        @DisplayName("Should skip movements with missing category and report DEFICIT")
        @SuppressWarnings("unchecked")
        void shouldSkipMissingCategoryAndReportDeficit() {
            when(householdService.getHouseholdIds(USER_ID)).thenReturn(Collections.emptyList());

            Movement pExpense = Movement.builder().id(1L).categoryId(20L).userId(USER_ID)
                    .date(REF).amount(new BigDecimal("900000")).confirmed(true).active(true).build();
            Movement orphan = Movement.builder().id(2L).categoryId(77L).userId(USER_ID)
                    .date(REF).amount(new BigDecimal("123")).confirmed(true).active(true).build();

            when(movementRepository.findByUserIdAndDateBetweenAndConfirmedTrueAndActiveTrue(eq(USER_ID), any(), any()))
                    .thenReturn(List.of(pExpense, orphan));
            when(allocationRepository.findByUserIdAndPeriodStartAndActiveTrue(eq(USER_ID), any()))
                    .thenReturn(Collections.emptyList());
            when(categoryRepository.findByIdAndActiveTrue(20L)).thenReturn(Optional.of(expenseCategory(20L)));
            when(categoryRepository.findByIdAndActiveTrue(77L)).thenReturn(Optional.empty());

            ResultDTO result = service.consolidated(USER_ID, 1, REF);

            Map<String, Object> report = (Map<String, Object>) result.getObject();
            Map<String, Object> total = (Map<String, Object>) report.get("total");
            assertEquals("DEFICIT", total.get("status"));
            Map<String, Object> budget = (Map<String, Object>) report.get("budget");
            assertEquals(0.0, budget.get("executionPct"));
        }

        @Test
        @DisplayName("Should use household queries when user belongs to households")
        void shouldUseHouseholdQueries() {
            when(householdService.getHouseholdIds(USER_ID)).thenReturn(List.of(100L));
            when(movementRepository.findHouseholdConfirmed(eq(USER_ID), anyList(), any(), any()))
                    .thenReturn(Collections.emptyList());
            when(allocationRepository.findAllForUserAndHousehold(eq(USER_ID), anyList(), any()))
                    .thenReturn(Collections.emptyList());

            ResultDTO result = service.consolidated(USER_ID, 1, REF);

            assertTrue(result.isCorrect());
            verify(movementRepository).findHouseholdConfirmed(eq(USER_ID), anyList(), any(), any());
        }

        @Test
        @DisplayName("Should return 500 when query fails")
        void shouldHandleException() {
            when(householdService.getHouseholdIds(USER_ID)).thenThrow(new RuntimeException("DB error"));

            ResultDTO result = service.consolidated(USER_ID, 1, REF);

            assertFalse(result.isCorrect());
            assertEquals(500, result.getErrorCode());
        }
    }

    @Nested
    @DisplayName("autoCloseExpired")
    class AutoCloseTests {

        @Test
        @DisplayName("Should mark expired allocations as CLOSED")
        void shouldCloseExpired() {
            BudgetAllocation a1 = allocation(1L, 20L, new BigDecimal("100"));
            BudgetAllocation a2 = allocation(2L, 21L, new BigDecimal("200"));
            when(allocationRepository.findExpiredActive(any())).thenReturn(List.of(a1, a2));

            ResultDTO result = service.autoCloseExpired();

            assertTrue(result.isCorrect());
            assertEquals("CLOSED", a1.getStatus());
            assertEquals("CLOSED", a2.getStatus());
            verify(allocationRepository, times(2)).save(any(BudgetAllocation.class));
        }

        @Test
        @DisplayName("Should handle empty expired list")
        void shouldHandleEmpty() {
            when(allocationRepository.findExpiredActive(any())).thenReturn(Collections.emptyList());

            ResultDTO result = service.autoCloseExpired();

            assertTrue(result.isCorrect());
            verify(allocationRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should return 500 when query fails")
        void shouldHandleException() {
            when(allocationRepository.findExpiredActive(any())).thenThrow(new RuntimeException("DB error"));

            ResultDTO result = service.autoCloseExpired();

            assertFalse(result.isCorrect());
            assertEquals(500, result.getErrorCode());
        }
    }
}

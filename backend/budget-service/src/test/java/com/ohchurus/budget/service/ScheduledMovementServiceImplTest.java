package com.ohchurus.budget.service;

import com.ohchurus.budget.dto.input.ScheduledMovementFilterDTO;
import com.ohchurus.budget.dto.input.ScheduledMovementSaveDTO;
import com.ohchurus.budget.dto.output.ResultDTO;
import com.ohchurus.budget.dto.output.ResultMovementDTO;
import com.ohchurus.budget.dto.output.ResultScheduledMovementDTO;
import com.ohchurus.budget.entity.Category;
import com.ohchurus.budget.entity.Movement;
import com.ohchurus.budget.entity.ScheduledMovement;
import com.ohchurus.budget.enums.CategoryType;
import com.ohchurus.budget.enums.Frequency;
import com.ohchurus.budget.mapper.MovementMapper;
import com.ohchurus.budget.mapper.ScheduledMovementMapper;
import com.ohchurus.budget.repository.CategoryRepository;
import com.ohchurus.budget.repository.MovementRepository;
import com.ohchurus.budget.repository.ScheduledMovementRepository;
import com.ohchurus.budget.service.impl.HouseholdServiceImpl;
import com.ohchurus.budget.service.impl.ScheduledMovementServiceImpl;
import com.ohchurus.budget.util.PeriodUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

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
class ScheduledMovementServiceImplTest {

    @Mock
    private ScheduledMovementRepository scheduledMovementRepository;

    @Mock
    private MovementRepository movementRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ScheduledMovementMapper scheduledMovementMapper;

    @Mock
    private MovementMapper movementMapper;

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
    }


    @InjectMocks
    private ScheduledMovementServiceImpl scheduledMovementService;

    private ScheduledMovement testScheduled;
    private ResultScheduledMovementDTO testResultDTO;
    private ScheduledMovementSaveDTO testSaveDTO;
    private Category testCategory;

    @BeforeEach
    void setUp() {
        testCategory = Category.builder()
                .id(10L).userId(1L).name("Vivienda").type(CategoryType.EXPENSE)
                .active(true).build();

        LocalDate currentPeriodStart = PeriodUtils.getStartOfPeriod(1, LocalDate.now());
        testScheduled = ScheduledMovement.builder()
                .id(1L).userId(1L).categoryId(10L)
                .name("Monthly Rent")
                .amount(new BigDecimal("1500000.00"))
                .frequency(Frequency.MONTHLY)
                .startDate(currentPeriodStart)
                .dayOfMonth(1)
                .active(true)
                .build();

        testResultDTO = new ResultScheduledMovementDTO();
        testResultDTO.setId(1L);
        testResultDTO.setUserId(1L);
        testResultDTO.setCategoryId(10L);
        testResultDTO.setName("Monthly Rent");
        testResultDTO.setAmount(new BigDecimal("1500000.00"));
        testResultDTO.setFrequency(Frequency.MONTHLY);
        testResultDTO.setStartDate(LocalDate.of(2026, 1, 1));
        testResultDTO.setDayOfMonth(1);
        testResultDTO.setActive(true);

        testSaveDTO = new ScheduledMovementSaveDTO();
        testSaveDTO.setUserId(1L);
        testSaveDTO.setCategoryId(10L);
        testSaveDTO.setName("Monthly Rent");
        testSaveDTO.setAmount(new BigDecimal("1500000.00"));
        testSaveDTO.setFrequency(Frequency.MONTHLY);
        testSaveDTO.setStartDate(LocalDate.of(2026, 1, 1));
        testSaveDTO.setDayOfMonth(1);
    }

    @Nested
    @DisplayName("Create")
    class CreateTests {

        @Test
        @DisplayName("Should create scheduled movement successfully")
        void shouldCreateScheduled() {
            when(categoryRepository.findByIdAndActiveTrue(10L)).thenReturn(Optional.of(testCategory));
            when(scheduledMovementRepository.save(any(ScheduledMovement.class))).thenReturn(testScheduled);
            when(scheduledMovementMapper.toResultDTO(any())).thenReturn(testResultDTO);

            ResultDTO result = scheduledMovementService.saveAndUpdate(testSaveDTO);

            assertTrue(result.isCorrect());
            verify(scheduledMovementRepository).save(any(ScheduledMovement.class));
        }

        @Test
        @DisplayName("Should create scheduled with duration and calculate endDate")
        void shouldCreateWithDuration() {
            /* Esta prueba defendia el bug: exigia que un credito a 12 meses que
               empieza el 1 de enero de 2026 terminara el 1 de enero de 2027, o
               sea 13 cuotas. Doce meses desde el 1 de enero acaban el 31 de
               diciembre. */
            testSaveDTO.setDurationMonths(12);

            ScheduledMovement withEnd = ScheduledMovement.builder()
                    .id(2L).userId(1L).categoryId(10L).name("Loan")
                    .amount(new BigDecimal("500000.00")).frequency(Frequency.MONTHLY)
                    .startDate(LocalDate.of(2026, 1, 1))
                    .endDate(LocalDate.of(2026, 12, 31))
                    .durationMonths(12).active(true).build();

            ResultScheduledMovementDTO withEndDTO = new ResultScheduledMovementDTO();
            withEndDTO.setId(2L);
            withEndDTO.setEndDate(LocalDate.of(2026, 12, 31));

            when(categoryRepository.findByIdAndActiveTrue(10L)).thenReturn(Optional.of(testCategory));
            when(scheduledMovementRepository.save(any(ScheduledMovement.class))).thenReturn(withEnd);
            when(scheduledMovementMapper.toResultDTO(any())).thenReturn(withEndDTO);

            ResultDTO result = scheduledMovementService.saveAndUpdate(testSaveDTO);

            assertTrue(result.isCorrect());
            verify(scheduledMovementRepository).save(argThat(s ->
                    s.getEndDate() != null && s.getEndDate().equals(LocalDate.of(2026, 12, 31))));
        }

        @Test
        @DisplayName("Should create scheduled without duration (indefinite)")
        void shouldCreateWithoutDuration() {
            testSaveDTO.setDurationMonths(null);

            when(categoryRepository.findByIdAndActiveTrue(10L)).thenReturn(Optional.of(testCategory));
            when(scheduledMovementRepository.save(any(ScheduledMovement.class))).thenReturn(testScheduled);
            when(scheduledMovementMapper.toResultDTO(any())).thenReturn(testResultDTO);

            ResultDTO result = scheduledMovementService.saveAndUpdate(testSaveDTO);

            assertTrue(result.isCorrect());
            verify(scheduledMovementRepository).save(argThat(s -> s.getEndDate() == null));
        }

        @Test
        @DisplayName("Should fail when category not found")
        void shouldFailWhenCategoryNotFound() {
            when(categoryRepository.findByIdAndActiveTrue(10L)).thenReturn(Optional.empty());

            ResultDTO result = scheduledMovementService.saveAndUpdate(testSaveDTO);

            assertFalse(result.isCorrect());
            assertEquals(404, result.getErrorCode());
            verify(scheduledMovementRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Update")
    class UpdateTests {

        @BeforeEach
        void setUp() {
            testSaveDTO.setId(1L);
        }

        @Test
        @DisplayName("Should update scheduled movement successfully")
        void shouldUpdateScheduled() {
            when(scheduledMovementRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(testScheduled));
            when(categoryRepository.findByIdAndActiveTrue(10L)).thenReturn(Optional.of(testCategory));
            when(scheduledMovementRepository.save(any())).thenReturn(testScheduled);
            when(scheduledMovementMapper.toResultDTO(any())).thenReturn(testResultDTO);

            ResultDTO result = scheduledMovementService.saveAndUpdate(testSaveDTO);

            assertTrue(result.isCorrect());
        }

        @Test
        @DisplayName("Should fail when scheduled not found for update")
        void shouldFailWhenNotFound() {
            when(scheduledMovementRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.empty());

            ResultDTO result = scheduledMovementService.saveAndUpdate(testSaveDTO);

            assertFalse(result.isCorrect());
            assertEquals(404, result.getErrorCode());
        }

        @Test
        @DisplayName("Should fail when category not found for update")
        void shouldFailWhenCategoryNotFoundForUpdate() {
            when(scheduledMovementRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(testScheduled));
            when(categoryRepository.findByIdAndActiveTrue(10L)).thenReturn(Optional.empty());

            ResultDTO result = scheduledMovementService.saveAndUpdate(testSaveDTO);

            assertFalse(result.isCorrect());
            assertEquals(404, result.getErrorCode());
        }
    }

    @Nested
    @DisplayName("GetById")
    class GetByIdTests {

        @Test
        @DisplayName("Should return scheduled with enriched category data")
        void shouldReturnWhenFound() {
            when(scheduledMovementRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(testScheduled));
            when(scheduledMovementMapper.toResultDTO(testScheduled)).thenReturn(testResultDTO);
            when(categoryRepository.findByIdAndActiveTrue(testScheduled.getCategoryId()))
                    .thenReturn(Optional.of(testCategory));

            ResultDTO result = scheduledMovementService.getById(1L);

            assertTrue(result.isCorrect());
            assertEquals("Vivienda", testResultDTO.getCategoryName());
            assertEquals("EXPENSE", testResultDTO.getCategoryType());
        }

        @Test
        @DisplayName("Should return error when not found")
        void shouldReturnErrorWhenNotFound() {
            when(scheduledMovementRepository.findByIdAndActiveTrue(99L)).thenReturn(Optional.empty());

            ResultDTO result = scheduledMovementService.getById(99L);

            assertFalse(result.isCorrect());
            assertEquals(404, result.getErrorCode());
        }
    }

    @Nested
    @DisplayName("GetAll")
    class GetAllTests {

        @Test
        @DisplayName("Should return paginated scheduled movements with category enrichment")
        void shouldReturnPaginated() {
            ScheduledMovementFilterDTO filter = new ScheduledMovementFilterDTO();
            filter.setUserId(1L);

            when(householdService.getHouseholdIds(1L)).thenReturn(Collections.emptyList());
            Page<ScheduledMovement> page = new PageImpl<>(List.of(testScheduled));
            when(scheduledMovementRepository.findAllWithFilters(any(), any(), any(), any(Pageable.class))).thenReturn(page);
            when(scheduledMovementMapper.toResultDTO(any())).thenReturn(testResultDTO);
            when(categoryRepository.findByIdAndActiveTrue(testScheduled.getCategoryId()))
                    .thenReturn(Optional.of(testCategory));

            ResultDTO result = scheduledMovementService.getAll(filter);

            assertTrue(result.isCorrect());
            @SuppressWarnings("unchecked")
            Map<String, Object> response = (Map<String, Object>) result.getObject();
            assertEquals(1, ((List<?>) response.get("list")).size());
        }

        @Test
        @DisplayName("Should return empty list when no scheduled movements")
        void shouldReturnEmptyList() {
            ScheduledMovementFilterDTO filter = new ScheduledMovementFilterDTO();
            filter.setUserId(1L);

            when(householdService.getHouseholdIds(1L)).thenReturn(Collections.emptyList());
            Page<ScheduledMovement> page = new PageImpl<>(List.of());
            when(scheduledMovementRepository.findAllWithFilters(any(), any(), any(), any(Pageable.class))).thenReturn(page);

            ResultDTO result = scheduledMovementService.getAll(filter);

            assertTrue(result.isCorrect());
            @SuppressWarnings("unchecked")
            Map<String, Object> response = (Map<String, Object>) result.getObject();
            assertTrue(((List<?>) response.get("list")).isEmpty());
        }
    }

    @Nested
    @DisplayName("Delete")
    class DeleteTests {

        @Test
        @DisplayName("Should soft delete scheduled movement and cascade pending")
        void shouldSoftDeleteAndCascade() {
            Movement pending = Movement.builder()
                    .id(100L).userId(1L).categoryId(10L).date(LocalDate.now())
                    .amount(new BigDecimal("1500000")).scheduledMovementId(1L)
                    .confirmed(false).active(true).build();

            when(scheduledMovementRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(testScheduled));
            when(scheduledMovementRepository.save(any())).thenReturn(testScheduled);
            when(movementRepository.findByScheduledMovementIdAndConfirmedFalseAndActiveTrue(1L))
                    .thenReturn(List.of(pending));
            when(movementRepository.save(any())).thenReturn(pending);

            ResultDTO result = scheduledMovementService.delete(1L);

            assertTrue(result.isCorrect());
            verify(scheduledMovementRepository).save(argThat(s -> !s.getActive()));
            verify(movementRepository).save(argThat(m -> !m.getActive()));
        }

        @Test
        @DisplayName("Should fail when scheduled not found for delete")
        void shouldFailWhenNotFound() {
            when(scheduledMovementRepository.findByIdAndActiveTrue(99L)).thenReturn(Optional.empty());

            ResultDTO result = scheduledMovementService.delete(99L);

            assertFalse(result.isCorrect());
            assertEquals(404, result.getErrorCode());
        }
    }

    @Nested
    @DisplayName("GeneratePending")
    class GeneratePendingTests {

        @Test
        @DisplayName("Should generate pending movement for monthly scheduled")
        void shouldGeneratePendingMonthly() {
            when(householdService.getHouseholdIds(1L)).thenReturn(Collections.emptyList());
            when(scheduledMovementRepository.findByUserIdAndActiveTrue(1L))
                    .thenReturn(List.of(testScheduled));
            when(movementRepository.existeOcurrenciaDelPeriodo(
                    eq(1L), any(LocalDate.class), any(LocalDate.class), any(LocalDate.class))).thenReturn(false);

            Movement savedMovement = Movement.builder()
                    .id(100L).userId(1L).categoryId(10L)
                    .date(LocalDate.now()).amount(new BigDecimal("1500000.00"))
                    .description("Monthly Rent").scheduledMovementId(1L)
                    .confirmed(false).active(true).build();

            ResultMovementDTO savedDTO = new ResultMovementDTO();
            savedDTO.setId(100L);
            savedDTO.setConfirmed(false);

            when(movementRepository.save(any(Movement.class))).thenReturn(savedMovement);
            when(movementMapper.toResultDTO(any())).thenReturn(savedDTO);

            ResultDTO result = scheduledMovementService.generatePending(1L, 1);

            assertTrue(result.isCorrect());
            @SuppressWarnings("unchecked")
            List<ResultMovementDTO> generated = (List<ResultMovementDTO>) result.getObject();
            assertEquals(1, generated.size());
            verify(movementRepository).save(argThat(m -> !m.getConfirmed() && m.getScheduledMovementId().equals(1L)));
        }

        @Test
        @DisplayName("Should not generate when pending already exists")
        void shouldNotGenerateWhenAlreadyExists() {
            when(householdService.getHouseholdIds(1L)).thenReturn(Collections.emptyList());
            when(scheduledMovementRepository.findByUserIdAndActiveTrue(1L))
                    .thenReturn(List.of(testScheduled));
            when(movementRepository.existeOcurrenciaDelPeriodo(
                    eq(1L), any(LocalDate.class), any(LocalDate.class), any(LocalDate.class))).thenReturn(true);

            ResultDTO result = scheduledMovementService.generatePending(1L, 1);

            assertTrue(result.isCorrect());
            @SuppressWarnings("unchecked")
            List<?> generated = (List<?>) result.getObject();
            assertTrue(generated.isEmpty());
            verify(movementRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should not generate for expired scheduled movement")
        void shouldNotGenerateForExpired() {
            ScheduledMovement expired = ScheduledMovement.builder()
                    .id(2L).userId(1L).categoryId(10L).name("Old Loan")
                    .amount(new BigDecimal("100000.00")).frequency(Frequency.MONTHLY)
                    .startDate(LocalDate.of(2024, 1, 1))
                    .endDate(LocalDate.of(2024, 6, 1))
                    .active(true).build();

            when(householdService.getHouseholdIds(1L)).thenReturn(Collections.emptyList());
            when(scheduledMovementRepository.findByUserIdAndActiveTrue(1L))
                    .thenReturn(List.of(expired));

            ResultDTO result = scheduledMovementService.generatePending(1L, 1);

            assertTrue(result.isCorrect());
            @SuppressWarnings("unchecked")
            List<?> generated = (List<?>) result.getObject();
            assertTrue(generated.isEmpty());
            verify(movementRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should not generate for future scheduled movement")
        void shouldNotGenerateForFuture() {
            ScheduledMovement future = ScheduledMovement.builder()
                    .id(3L).userId(1L).categoryId(10L).name("Future Plan")
                    .amount(new BigDecimal("200000.00")).frequency(Frequency.MONTHLY)
                    .startDate(LocalDate.now().plusYears(1))
                    .active(true).build();

            when(householdService.getHouseholdIds(1L)).thenReturn(Collections.emptyList());
            when(scheduledMovementRepository.findByUserIdAndActiveTrue(1L))
                    .thenReturn(List.of(future));

            ResultDTO result = scheduledMovementService.generatePending(1L, 1);

            assertTrue(result.isCorrect());
            @SuppressWarnings("unchecked")
            List<?> generated = (List<?>) result.getObject();
            assertTrue(generated.isEmpty());
            verify(movementRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should return empty list when no scheduled movements")
        void shouldReturnEmptyWhenNoScheduled() {
            when(householdService.getHouseholdIds(1L)).thenReturn(Collections.emptyList());
            when(scheduledMovementRepository.findByUserIdAndActiveTrue(1L))
                    .thenReturn(List.of());

            ResultDTO result = scheduledMovementService.generatePending(1L, 1);

            assertTrue(result.isCorrect());
            @SuppressWarnings("unchecked")
            List<?> generated = (List<?>) result.getObject();
            assertTrue(generated.isEmpty());
        }

        @Test
        @DisplayName("Should generate for quarterly scheduled in correct period")
        void shouldGenerateForQuarterly() {
            LocalDate currentPeriodStart = PeriodUtils.getStartOfPeriod(1, LocalDate.now());
            ScheduledMovement quarterly = ScheduledMovement.builder()
                    .id(4L).userId(1L).categoryId(10L).name("Quarterly Tax")
                    .amount(new BigDecimal("300000.00")).frequency(Frequency.QUARTERLY)
                    .startDate(currentPeriodStart)
                    .dayOfMonth(15)
                    .active(true).build();

            when(householdService.getHouseholdIds(1L)).thenReturn(Collections.emptyList());
            when(scheduledMovementRepository.findByUserIdAndActiveTrue(1L))
                    .thenReturn(List.of(quarterly));
            when(movementRepository.existeOcurrenciaDelPeriodo(
                    eq(4L), any(LocalDate.class), any(LocalDate.class), any(LocalDate.class))).thenReturn(false);

            Movement savedMovement = Movement.builder()
                    .id(101L).userId(1L).categoryId(10L)
                    .date(LocalDate.now()).amount(new BigDecimal("300000.00"))
                    .scheduledMovementId(4L).confirmed(false).active(true).build();

            ResultMovementDTO savedDTO = new ResultMovementDTO();
            savedDTO.setId(101L);

            when(movementRepository.save(any(Movement.class))).thenReturn(savedMovement);
            when(movementMapper.toResultDTO(any())).thenReturn(savedDTO);

            ResultDTO result = scheduledMovementService.generatePending(1L, 1);

            assertTrue(result.isCorrect());
        }

        @Test
        @DisplayName("Should generate for weekly scheduled")
        void shouldGenerateForWeekly() {
            LocalDate currentPeriodStart = PeriodUtils.getStartOfPeriod(1, LocalDate.now());
            ScheduledMovement weekly = ScheduledMovement.builder()
                    .id(5L).userId(1L).categoryId(10L).name("Weekly Groceries")
                    .amount(new BigDecimal("150000.00")).frequency(Frequency.WEEKLY)
                    .startDate(currentPeriodStart).dayOfMonth(1).active(true).build();

            when(householdService.getHouseholdIds(1L)).thenReturn(Collections.emptyList());
            when(scheduledMovementRepository.findByUserIdAndActiveTrue(1L)).thenReturn(List.of(weekly));
            when(movementRepository.existeOcurrenciaDelPeriodo(
                    eq(5L), any(LocalDate.class), any(LocalDate.class), any(LocalDate.class))).thenReturn(false);

            Movement saved = Movement.builder().id(102L).userId(1L).categoryId(10L)
                    .date(LocalDate.now()).amount(new BigDecimal("150000.00"))
                    .scheduledMovementId(5L).confirmed(false).active(true).build();
            ResultMovementDTO dto = new ResultMovementDTO();
            dto.setId(102L);

            when(movementRepository.save(any(Movement.class))).thenReturn(saved);
            when(movementMapper.toResultDTO(any())).thenReturn(dto);

            ResultDTO result = scheduledMovementService.generatePending(1L, 1);
            assertTrue(result.isCorrect());
        }

        @Test
        @DisplayName("Should generate for DAILY scheduled")
        void shouldGenerateForDaily() {
            LocalDate currentPeriodStart = PeriodUtils.getStartOfPeriod(1, LocalDate.now());
            ScheduledMovement daily = ScheduledMovement.builder()
                    .id(10L).userId(1L).categoryId(10L).name("Daily Coffee")
                    .amount(new BigDecimal("5000.00")).frequency(Frequency.DAILY)
                    .startDate(currentPeriodStart).dayOfMonth(1).active(true).build();

            when(householdService.getHouseholdIds(1L)).thenReturn(Collections.emptyList());
            when(scheduledMovementRepository.findByUserIdAndActiveTrue(1L)).thenReturn(List.of(daily));
            when(movementRepository.existeOcurrenciaDelPeriodo(
                    eq(10L), any(LocalDate.class), any(LocalDate.class), any(LocalDate.class))).thenReturn(false);

            Movement saved = Movement.builder().id(110L).userId(1L).categoryId(10L)
                    .date(LocalDate.now()).amount(new BigDecimal("5000.00"))
                    .scheduledMovementId(10L).confirmed(false).active(true).build();
            ResultMovementDTO dto = new ResultMovementDTO();
            dto.setId(110L);

            when(movementRepository.save(any(Movement.class))).thenReturn(saved);
            when(movementMapper.toResultDTO(any())).thenReturn(dto);

            ResultDTO result = scheduledMovementService.generatePending(1L, 1);
            assertTrue(result.isCorrect());
        }

        @Test
        @DisplayName("Should use budgetStartDay when dayOfMonth is null")
        void shouldUseBudgetStartDayWhenNoDayOfMonth() {
            // Use the same budgetStartDay for startDate so the period aligns
            LocalDate currentPeriodStart = PeriodUtils.getStartOfPeriod(15, LocalDate.now());
            ScheduledMovement noDayScheduled = ScheduledMovement.builder()
                    .id(6L).userId(1L).categoryId(10L).name("No Day")
                    .amount(new BigDecimal("50000.00")).frequency(Frequency.MONTHLY)
                    .startDate(currentPeriodStart).dayOfMonth(null).active(true).build();

            when(householdService.getHouseholdIds(1L)).thenReturn(Collections.emptyList());
            when(scheduledMovementRepository.findByUserIdAndActiveTrue(1L)).thenReturn(List.of(noDayScheduled));
            when(movementRepository.existeOcurrenciaDelPeriodo(
                    eq(6L), any(LocalDate.class), any(LocalDate.class), any(LocalDate.class))).thenReturn(false);

            Movement saved = Movement.builder().id(103L).userId(1L).categoryId(10L)
                    .date(LocalDate.now()).amount(new BigDecimal("50000.00"))
                    .scheduledMovementId(6L).confirmed(false).active(true).build();
            ResultMovementDTO dto = new ResultMovementDTO();
            dto.setId(103L);

            when(movementRepository.save(any(Movement.class))).thenReturn(saved);
            when(movementMapper.toResultDTO(any())).thenReturn(dto);

            ResultDTO result = scheduledMovementService.generatePending(1L, 15);
            assertTrue(result.isCorrect());
        }

        @Test
        @DisplayName("Should generate with null amount as zero")
        void shouldHandleNullAmount() {
            LocalDate currentPeriodStart = PeriodUtils.getStartOfPeriod(1, LocalDate.now());
            ScheduledMovement nullAmount = ScheduledMovement.builder()
                    .id(7L).userId(1L).categoryId(10L).name("Variable")
                    .amount(null).frequency(Frequency.MONTHLY)
                    .startDate(currentPeriodStart).dayOfMonth(1).active(true).build();

            when(householdService.getHouseholdIds(1L)).thenReturn(Collections.emptyList());
            when(scheduledMovementRepository.findByUserIdAndActiveTrue(1L)).thenReturn(List.of(nullAmount));
            when(movementRepository.existeOcurrenciaDelPeriodo(
                    eq(7L), any(LocalDate.class), any(LocalDate.class), any(LocalDate.class))).thenReturn(false);

            Movement saved = Movement.builder().id(104L).userId(1L).categoryId(10L)
                    .date(LocalDate.now()).amount(BigDecimal.ZERO)
                    .scheduledMovementId(7L).confirmed(false).active(true).build();
            ResultMovementDTO dto = new ResultMovementDTO();
            dto.setId(104L);

            when(movementRepository.save(any(Movement.class))).thenReturn(saved);
            when(movementMapper.toResultDTO(any())).thenReturn(dto);

            ResultDTO result = scheduledMovementService.generatePending(1L, 1);
            assertTrue(result.isCorrect());
            verify(movementRepository).save(argThat(m -> m.getAmount().equals(BigDecimal.ZERO)));
        }

        @Test
        @DisplayName("Should handle exception in generate pending")
        void shouldHandleException() {
            when(householdService.getHouseholdIds(1L)).thenReturn(Collections.emptyList());
            when(scheduledMovementRepository.findByUserIdAndActiveTrue(1L))
                    .thenThrow(new RuntimeException("DB error"));

            ResultDTO result = scheduledMovementService.generatePending(1L, 1);
            assertFalse(result.isCorrect());
            assertEquals(500, result.getErrorCode());
        }
    }

    @Nested
    @DisplayName("SaveAndUpdate - Error handling")
    class SaveErrorTests {

        @Test
        @DisplayName("Should handle exception in save")
        void shouldHandleExceptionInSave() {
            when(categoryRepository.findByIdAndActiveTrue(10L))
                    .thenThrow(new RuntimeException("DB error"));

            ResultDTO result = scheduledMovementService.saveAndUpdate(testSaveDTO);
            assertFalse(result.isCorrect());
            assertEquals(500, result.getErrorCode());
        }
    }

    @Nested
    @DisplayName("FrequencyList")
    class FrequencyListTests {

        @Test
        @DisplayName("Should return all 8 frequencies including DAILY")
        void shouldReturnFrequencies() {
            ResultDTO result = scheduledMovementService.frequencyList();

            assertTrue(result.isCorrect());
            @SuppressWarnings("unchecked")
            List<Map<String, String>> frequencies = (List<Map<String, String>>) result.getObject();
            assertEquals(8, frequencies.size());
        }
    }
}

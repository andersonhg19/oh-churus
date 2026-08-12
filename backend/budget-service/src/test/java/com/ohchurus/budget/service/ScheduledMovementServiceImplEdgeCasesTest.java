package com.ohchurus.budget.service;

import com.ohchurus.budget.dto.input.MaterializeOccurrencesDTO;
import com.ohchurus.budget.dto.input.OccurrenceRefDTO;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
@DisplayName("ScheduledMovementServiceImpl - edge cases / branches")
class ScheduledMovementServiceImplEdgeCasesTest {

    @Mock private ScheduledMovementRepository scheduledMovementRepository;
    @Mock private MovementRepository movementRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private ScheduledMovementMapper scheduledMovementMapper;
    @Mock private MovementMapper movementMapper;
    @Mock private HouseholdServiceImpl householdService;
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



    /* Las cuentas entraron con la ola 3: todo movimiento tiene que caer en
       alguna, y si el DTO no dice cual, el servicio pone la de por defecto.
       Aqui se le da una cualquiera para poder seguir probando la LOGICA; que
       no se pueda colar un movimiento en la cuenta de otra persona lo
       demuestra la seccion "Cuentas" de AislamientoEntreUsuariosTest. */
    @Mock
    private com.ohchurus.budget.service.impl.AccountServiceImpl cuentas;

    @Mock
    private com.ohchurus.budget.repository.AccountRepository cuentaRepo;

    @BeforeEach
    void darleUnaCuentaALosMovimientos() {
        com.ohchurus.budget.entity.Account cuenta = com.ohchurus.budget.entity.Account.builder()
                .id(500L).userId(1L).name("Sin asignar")
                .kind(com.ohchurus.budget.enums.AccountKind.OWN)
                .isDefault(true).active(true).build();
        org.mockito.Mockito.lenient().when(cuentas.porDefecto(org.mockito.ArgumentMatchers.any()))
                .thenReturn(cuenta);
        org.mockito.Mockito.lenient().when(cuentaRepo.findByIdAndActiveTrue(
                org.mockito.ArgumentMatchers.any())).thenReturn(java.util.Optional.of(cuenta));
    }

    @InjectMocks
    private ScheduledMovementServiceImpl service;

    private static final Long USER_ID = 1L;

    private Category expense(Long id) {
        return Category.builder().id(id).userId(USER_ID).name("Cat" + id)
                .type(CategoryType.EXPENSE).active(true).build();
    }

    private ScheduledMovement scheduled(Long id, Frequency freq, LocalDate start, LocalDate end,
                                        BigDecimal amount, Integer dayOfMonth) {
        return ScheduledMovement.builder()
                .id(id).userId(USER_ID).categoryId(10L).name("Arriendo")
                .frequency(freq).startDate(start).endDate(end)
                .amount(amount).dayOfMonth(dayOfMonth).active(true).build();
    }

    /* lenient: segun la frecuencia y cuanto se haya atrasado, unas ocurrencias
       se crean y otras se proponen, y las que se proponen no llegan a save(). */
    private void stubGeneration() {
        lenient().when(movementRepository.findByScheduledMovementId(anyLong())).thenReturn(List.of());
        lenient().when(movementRepository.save(any(Movement.class))).thenAnswer(i -> {
            Movement m = i.getArgument(0); m.setId(999L); return m;
        });
        lenient().when(movementMapper.toResultDTO(any(Movement.class))).thenReturn(new ResultMovementDTO());
    }

    /* generate-pending devuelve lo CREADO y lo PROPUESTO por separado desde que
       hay tope de materializacion. Lo que estas pruebas miden es que el
       generador CONOZCA la ocurrencia; si la crea o la propone depende de
       cuantas se hayan atrasado, y eso lo prueba aparte la de coherencia. */
    @SuppressWarnings("unchecked")
    private List<?> conocidas(ResultDTO resultado) {
        Map<String, Object> respuesta = (Map<String, Object>) resultado.getObject();
        List<Object> todas = new java.util.ArrayList<>((List<Object>) respuesta.get("created"));
        todas.addAll((List<Object>) respuesta.get("proposals"));
        return todas;
    }

    // ===================== generatePending =====================

    @ParameterizedTest
    @EnumSource(Frequency.class)
    @DisplayName("generatePending should honor every frequency over a multi-period span")
    void shouldGenerateForEachFrequency(Frequency freq) {
        LocalDate start = LocalDate.now().minusMonths(13).withDayOfMonth(1);
        when(householdService.getHouseholdIds(USER_ID)).thenReturn(Collections.emptyList());
        when(scheduledMovementRepository.findByUserIdAndActiveTrue(USER_ID))
                .thenReturn(List.of(scheduled(1L, freq, start, null, new BigDecimal("1000"), null)));
        stubGeneration();

        ResultDTO r = service.generatePending(USER_ID, 1);

        assertTrue(r.isCorrect());
        assertFalse(conocidas(r).isEmpty(),
                "la frecuencia " + freq + " no produjo ni una ocurrencia en trece meses");
    }

    @Test
    @DisplayName("Should skip a scheduled that already fully expired before the current period")
    void shouldSkipExpired() {
        when(householdService.getHouseholdIds(USER_ID)).thenReturn(Collections.emptyList());
        when(scheduledMovementRepository.findByUserIdAndActiveTrue(USER_ID))
                .thenReturn(List.of(scheduled(1L, Frequency.MONTHLY,
                        LocalDate.now().minusMonths(6), LocalDate.now().minusMonths(4),
                        new BigDecimal("1000"), null)));

        ResultDTO r = service.generatePending(USER_ID, 1);
        assertTrue(r.isCorrect());
        verify(movementRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should skip a scheduled that has not started yet")
    void shouldSkipNotStarted() {
        when(householdService.getHouseholdIds(USER_ID)).thenReturn(Collections.emptyList());
        when(scheduledMovementRepository.findByUserIdAndActiveTrue(USER_ID))
                .thenReturn(List.of(scheduled(1L, Frequency.MONTHLY,
                        LocalDate.now().plusMonths(3), null, new BigDecimal("1000"), null)));

        ResultDTO r = service.generatePending(USER_ID, 1);
        assertTrue(r.isCorrect());
        verify(movementRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should generate across a multi-period span with a future end date still active")
    void shouldGenerateMultiPeriodActive() {
        when(householdService.getHouseholdIds(USER_ID)).thenReturn(Collections.emptyList());
        // starts 5 months ago, ends 2 months in the future -> active across several periods
        when(scheduledMovementRepository.findByUserIdAndActiveTrue(USER_ID))
                .thenReturn(List.of(scheduled(1L, Frequency.MONTHLY,
                        LocalDate.now().minusMonths(5), LocalDate.now().plusMonths(2),
                        new BigDecimal("1000"), null)));
        stubGeneration();

        ResultDTO r = service.generatePending(USER_ID, 1);
        assertTrue(r.isCorrect());
        verify(movementRepository, atLeastOnce()).save(any());
    }

    @Test
    @DisplayName("Should use explicit dayOfMonth and clamp date inside the period; null amount becomes zero")
    void shouldUseDayOfMonthAndZeroAmount() {
        when(householdService.getHouseholdIds(USER_ID)).thenReturn(Collections.emptyList());
        // budgetStartDay 15, dayOfMonth 5 -> movement date (5th) falls before period start (15th) -> clamped
        when(scheduledMovementRepository.findByUserIdAndActiveTrue(USER_ID))
                .thenReturn(List.of(scheduled(1L, Frequency.MONTHLY,
                        LocalDate.now().withDayOfMonth(1).minusMonths(1), null, null, 5)));
        stubGeneration();

        ResultDTO r = service.generatePending(USER_ID, 15);
        assertTrue(r.isCorrect());
        verify(movementRepository, atLeastOnce()).save(argThat(m -> m.getAmount().compareTo(BigDecimal.ZERO) == 0));
    }

    @Test
    @DisplayName("Should skip generation when a movement already exists for the period")
    void shouldSkipWhenAlreadyExists() {
        when(householdService.getHouseholdIds(USER_ID)).thenReturn(Collections.emptyList());
        when(scheduledMovementRepository.findByUserIdAndActiveTrue(USER_ID))
                .thenReturn(List.of(scheduled(1L, Frequency.MONTHLY,
                        LocalDate.now().withDayOfMonth(1), null, new BigDecimal("1000"), null)));
        when(movementRepository.findByScheduledMovementId(1L)).thenReturn(List.of(
                Movement.builder().id(88L).userId(USER_ID).categoryId(10L)
                        .date(LocalDate.now()).amount(new BigDecimal("1000"))
                        .scheduledMovementId(1L)
                        .periodStart(LocalDate.now().withDayOfMonth(1))
                        .confirmed(false).active(true).build()));

        ResultDTO r = service.generatePending(USER_ID, 1);
        assertTrue(r.isCorrect());
        verify(movementRepository, never()).save(any());
    }

    @Test
    @DisplayName("una ocurrencia BORRADA no resucita: la fila apagada es su lapida")
    void laOcurrenciaBorradaNoResucita() {
        when(householdService.getHouseholdIds(USER_ID)).thenReturn(Collections.emptyList());
        when(scheduledMovementRepository.findByUserIdAndActiveTrue(USER_ID))
                .thenReturn(List.of(scheduled(1L, Frequency.MONTHLY,
                        LocalDate.now().withDayOfMonth(1), null, new BigDecimal("1000"), null)));
        when(movementRepository.findByScheduledMovementId(1L)).thenReturn(List.of(
                Movement.builder().id(88L).userId(USER_ID).categoryId(10L)
                        .date(LocalDate.now()).amount(new BigDecimal("1000"))
                        .scheduledMovementId(1L)
                        .periodStart(LocalDate.now().withDayOfMonth(1))
                        .confirmed(false).active(false).build()));

        ResultDTO r = service.generatePending(USER_ID, 1);
        assertTrue(r.isCorrect());
        verify(movementRepository, never()).save(any());
    }

    @Test
    @DisplayName("una ocurrencia vieja SIN periodStart se reconoce por su fecha")
    void laOcurrenciaViejaSinClaveSeReconocePorLaFecha() {
        /* Las ocurrencias creadas antes de que existiera periodStart lo tienen
           nulo. Sin esta compatibilidad, el primer refresco tras el despliegue
           duplicaria todos los pendientes vivos. */
        when(householdService.getHouseholdIds(USER_ID)).thenReturn(Collections.emptyList());
        when(scheduledMovementRepository.findByUserIdAndActiveTrue(USER_ID))
                .thenReturn(List.of(scheduled(1L, Frequency.MONTHLY,
                        LocalDate.now().withDayOfMonth(1), null, new BigDecimal("1000"), null)));
        when(movementRepository.findByScheduledMovementId(1L)).thenReturn(List.of(
                Movement.builder().id(89L).userId(USER_ID).categoryId(10L)
                        .date(LocalDate.now()).amount(new BigDecimal("1000"))
                        .scheduledMovementId(1L).periodStart(null)
                        .confirmed(false).active(true).build()));

        ResultDTO r = service.generatePending(USER_ID, 1);
        assertTrue(r.isCorrect());
        verify(movementRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should use household-aware query when user belongs to households")
    void shouldUseHouseholdScheduled() {
        when(householdService.getHouseholdIds(USER_ID)).thenReturn(List.of(500L));
        when(scheduledMovementRepository.findHouseholdActive(eq(USER_ID), anyList()))
                .thenReturn(Collections.emptyList());

        ResultDTO r = service.generatePending(USER_ID, 1);
        assertTrue(r.isCorrect());
        verify(scheduledMovementRepository).findHouseholdActive(eq(USER_ID), anyList());
    }

    @Test
    @DisplayName("Should return 500 when generation fails")
    void shouldHandleException() {
        when(householdService.getHouseholdIds(USER_ID)).thenThrow(new RuntimeException("DB"));
        ResultDTO r = service.generatePending(USER_ID, 1);
        assertFalse(r.isCorrect());
        assertEquals(500, r.getErrorCode());
    }

    // ===================== materialize =====================

    @Nested
    @DisplayName("materialize")
    class MaterializeTests {

        private MaterializeOccurrencesDTO peticion(Long programado, LocalDate... claves) {
            List<OccurrenceRefDTO> refs = new java.util.ArrayList<>();
            for (LocalDate clave : claves) {
                refs.add(new OccurrenceRefDTO(programado, clave));
            }
            return new MaterializeOccurrencesDTO(refs);
        }

        @Test
        @DisplayName("una referencia mala no deja creadas las buenas: se valida todo antes de escribir")
        void seValidaTodoAntesDeEscribirNada() {
            /* Si se validara y creara sobre la marcha, la persona veria "no se
               pudo" con la mitad del trabajo hecho, que es la peor de las dos
               respuestas posibles. */
            LocalDate esteMes = LocalDate.now().withDayOfMonth(1);
            ScheduledMovement mensual = scheduled(1L, Frequency.MONTHLY, esteMes, null,
                    new BigDecimal("1000"), 1);
            when(scheduledMovementRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(mensual));

            /* La segunda clave cae un dia 17: un mensual solo tiene claves en
               el dia 1 de cada mes, asi que no pertenece a este programado. */
            ResultDTO r = service.materialize(peticion(1L, esteMes, esteMes.withDayOfMonth(17)));

            assertFalse(r.isCorrect());
            assertEquals(400, r.getErrorCode());
            verify(movementRepository, never()).save(any());
        }

        @Test
        @DisplayName("no materializa un programado que no existe o no es mio")
        void noMaterializaLoAjeno() {
            when(scheduledMovementRepository.findByIdAndActiveTrue(99L)).thenReturn(Optional.empty());

            ResultDTO r = service.materialize(peticion(99L, LocalDate.now().withDayOfMonth(1)));

            assertFalse(r.isCorrect());
            assertEquals(404, r.getErrorCode());
            verify(movementRepository, never()).save(any());
        }

        @Test
        @DisplayName("la misma clave repetida en la peticion crea un solo movimiento")
        void laClaveRepetidaNoDuplica() {
            LocalDate esteMes = LocalDate.now().withDayOfMonth(1);
            ScheduledMovement mensual = scheduled(1L, Frequency.MONTHLY, esteMes, null,
                    new BigDecimal("1000"), 1);
            when(scheduledMovementRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(mensual));
            when(movementRepository.findByScheduledMovementId(1L)).thenReturn(List.of());
            when(movementRepository.save(any(Movement.class))).thenAnswer(i -> {
                Movement m = i.getArgument(0); m.setId(999L); return m;
            });
            when(movementMapper.toResultDTO(any(Movement.class))).thenReturn(new ResultMovementDTO());

            ResultDTO r = service.materialize(peticion(1L, esteMes, esteMes));

            assertTrue(r.isCorrect());
            verify(movementRepository, times(1)).save(any());
        }

        @Test
        @DisplayName("Should return 500 when materialization fails")
        void devuelve500SiFalla() {
            when(scheduledMovementRepository.findByIdAndActiveTrue(anyLong()))
                    .thenThrow(new RuntimeException("DB"));

            ResultDTO r = service.materialize(peticion(1L, LocalDate.now().withDayOfMonth(1)));

            assertFalse(r.isCorrect());
            assertEquals(500, r.getErrorCode());
        }
    }

    // ===================== calculateEndDate (via create) =====================

    private ScheduledMovementSaveDTO saveDto(Integer durationMonths, LocalDate startDate) {
        ScheduledMovementSaveDTO dto = new ScheduledMovementSaveDTO();
        dto.setUserId(USER_ID);
        dto.setCategoryId(10L);
        dto.setName("Arriendo");
        dto.setFrequency(Frequency.MONTHLY);
        dto.setStartDate(startDate);
        dto.setDurationMonths(durationMonths);
        dto.setAmount(new BigDecimal("1000"));
        return dto;
    }

    @Nested
    @DisplayName("calculateEndDate via create")
    class EndDateTests {

        @Test
        @DisplayName("Should compute endDate when duration and startDate present")
        void shouldComputeEndDate() {
            when(categoryRepository.findByIdAndActiveTrue(10L)).thenReturn(Optional.of(expense(10L)));
            when(scheduledMovementRepository.save(any(ScheduledMovement.class))).thenAnswer(i -> i.getArgument(0));
            when(scheduledMovementMapper.toResultDTO(any(ScheduledMovement.class)))
                    .thenReturn(new ResultScheduledMovementDTO());

            /* Esta prueba defendia el bug: daba por bueno que 6 meses desde el
               15 de enero terminaran el 15 de julio, cuando ese dia ya es la
               septima cuota. El ultimo dia cubierto es el 14. */
            service.saveAndUpdate(saveDto(6, LocalDate.of(2026, 1, 15)));
            verify(scheduledMovementRepository).save(argThat(s ->
                    LocalDate.of(2026, 7, 14).equals(s.getEndDate())));
        }

        @Test
        @DisplayName("Should leave endDate null when durationMonths is null")
        void shouldNullEndDateWhenDurationNull() {
            when(categoryRepository.findByIdAndActiveTrue(10L)).thenReturn(Optional.of(expense(10L)));
            when(scheduledMovementRepository.save(any(ScheduledMovement.class))).thenAnswer(i -> i.getArgument(0));
            when(scheduledMovementMapper.toResultDTO(any(ScheduledMovement.class)))
                    .thenReturn(new ResultScheduledMovementDTO());

            service.saveAndUpdate(saveDto(null, LocalDate.of(2026, 1, 15)));
            verify(scheduledMovementRepository).save(argThat(s -> s.getEndDate() == null));
        }

        @Test
        @DisplayName("Should leave endDate null when startDate is null")
        void shouldNullEndDateWhenStartNull() {
            when(categoryRepository.findByIdAndActiveTrue(10L)).thenReturn(Optional.of(expense(10L)));
            when(scheduledMovementRepository.save(any(ScheduledMovement.class))).thenAnswer(i -> i.getArgument(0));
            when(scheduledMovementMapper.toResultDTO(any(ScheduledMovement.class)))
                    .thenReturn(new ResultScheduledMovementDTO());

            service.saveAndUpdate(saveDto(6, null));
            verify(scheduledMovementRepository).save(argThat(s -> s.getEndDate() == null));
        }
    }

    @Nested
    @DisplayName("updateScheduled household branch")
    class UpdateTests {

        @Test
        @DisplayName("Should NOT reassign userId when category is shared")
        void shouldKeepOwnerOnShared() {
            ScheduledMovementSaveDTO dto = saveDto(null, LocalDate.of(2026, 1, 15));
            dto.setId(7L);
            dto.setUserId(2L);
            ScheduledMovement existing = scheduled(7L, Frequency.MONTHLY, LocalDate.now(), null,
                    new BigDecimal("1000"), null);
            existing.setUserId(99L);
            Category shared = Category.builder().id(10L).userId(99L).name("Shared")
                    .type(CategoryType.EXPENSE).householdId(500L).active(true).build();

            when(scheduledMovementRepository.findByIdAndActiveTrue(7L)).thenReturn(Optional.of(existing));
            when(categoryRepository.findByIdAndActiveTrue(10L)).thenReturn(Optional.of(shared));
            when(scheduledMovementRepository.save(any(ScheduledMovement.class))).thenAnswer(i -> i.getArgument(0));
            when(scheduledMovementMapper.toResultDTO(any(ScheduledMovement.class)))
                    .thenReturn(new ResultScheduledMovementDTO());

            service.saveAndUpdate(dto);
            assertEquals(99L, existing.getUserId());
        }
    }

    @Nested
    @DisplayName("getById / getAll category enrichment branches")
    class EnrichmentTests {

        @Test
        @DisplayName("getById should leave categoryType null when category has no type")
        void getByIdNullType() {
            ScheduledMovement s = scheduled(1L, Frequency.MONTHLY, LocalDate.now(), null,
                    new BigDecimal("1000"), null);
            Category noType = Category.builder().id(10L).userId(USER_ID).name("NoType").type(null).active(true).build();
            when(scheduledMovementRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(s));
            when(scheduledMovementMapper.toResultDTO(s)).thenReturn(new ResultScheduledMovementDTO());
            when(categoryRepository.findByIdAndActiveTrue(10L)).thenReturn(Optional.of(noType));

            ResultDTO r = service.getById(1L);
            assertTrue(r.isCorrect());
            assertNull(((ResultScheduledMovementDTO) r.getObject()).getCategoryType());
        }

        @Test
        @DisplayName("getAll should use empty households when userId is null and skip null mapped DTOs")
        @SuppressWarnings("unchecked")
        void getAllNullUserAndNullDto() {
            ScheduledMovementFilterDTO filter = new ScheduledMovementFilterDTO();
            filter.setUserId(null);
            ScheduledMovement s = scheduled(1L, Frequency.MONTHLY, LocalDate.now(), null,
                    new BigDecimal("1000"), null);
            Page<ScheduledMovement> page = new PageImpl<>(List.of(s), Pageable.ofSize(10), 1);
            when(scheduledMovementRepository.findAllWithFilters(isNull(), any(), any(), any())).thenReturn(page);
            when(scheduledMovementMapper.toResultDTO(s)).thenReturn(null); // covers dto == null branch

            ResultDTO r = service.getAll(filter);
            assertTrue(r.isCorrect());
            verify(householdService, never()).getHouseholdIds(any());
        }

        @Test
        @DisplayName("getAll should use household query and enrich category (null type handled)")
        void getAllHouseholdNullType() {
            ScheduledMovementFilterDTO filter = new ScheduledMovementFilterDTO();
            filter.setUserId(USER_ID);
            ScheduledMovement s = scheduled(1L, Frequency.MONTHLY, LocalDate.now(), null,
                    new BigDecimal("1000"), null);
            Category noType = Category.builder().id(10L).userId(USER_ID).name("NoType").type(null).active(true).build();
            Page<ScheduledMovement> page = new PageImpl<>(List.of(s), Pageable.ofSize(10), 1);

            when(householdService.getHouseholdIds(USER_ID)).thenReturn(List.of(500L));
            when(scheduledMovementRepository.findAllWithFiltersAndHousehold(eq(USER_ID), anyList(), any(), any(), any()))
                    .thenReturn(page);
            when(scheduledMovementMapper.toResultDTO(s)).thenReturn(new ResultScheduledMovementDTO());
            when(categoryRepository.findByIdAndActiveTrue(10L)).thenReturn(Optional.of(noType));

            ResultDTO r = service.getAll(filter);
            assertTrue(r.isCorrect());
            verify(scheduledMovementRepository).findAllWithFiltersAndHousehold(eq(USER_ID), anyList(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("delete with generated pending")
    class DeleteTests {

        @Test
        @DisplayName("Should deactivate the scheduled and its unconfirmed pending movements")
        void shouldCancelPending() {
            ScheduledMovement s = scheduled(1L, Frequency.MONTHLY, LocalDate.now(), null,
                    new BigDecimal("1000"), null);
            Movement pending = Movement.builder().id(50L).userId(USER_ID).categoryId(10L)
                    .date(LocalDate.now()).amount(BigDecimal.TEN).confirmed(false).active(true)
                    .scheduledMovementId(1L).build();

            when(scheduledMovementRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(s));
            when(movementRepository.findByScheduledMovementIdAndConfirmedFalseAndActiveTrue(1L))
                    .thenReturn(List.of(pending));

            ResultDTO r = service.delete(1L);
            assertTrue(r.isCorrect());
            assertFalse(s.getActive());
            assertFalse(pending.getActive());
            verify(movementRepository).save(pending);
        }
    }
}

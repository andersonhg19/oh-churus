package com.ohchurus.budget.service;

import com.ohchurus.budget.dto.input.MovementFilterDTO;
import com.ohchurus.budget.dto.input.MovementSaveDTO;
import com.ohchurus.budget.dto.output.ResultDTO;
import com.ohchurus.budget.dto.output.ResultMovementDTO;
import com.ohchurus.budget.entity.Category;
import com.ohchurus.budget.entity.Movement;
import com.ohchurus.budget.enums.CategoryType;
import com.ohchurus.budget.mapper.MovementMapper;
import com.ohchurus.budget.repository.CategoryRepository;
import com.ohchurus.budget.repository.MovementRepository;
import com.ohchurus.budget.service.impl.HouseholdServiceImpl;
import com.ohchurus.budget.service.impl.MovementServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.BeforeEach;
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
@DisplayName("MovementServiceImpl - edge cases / branches")
class MovementServiceImplEdgeCasesTest {

    @Mock
    private MovementRepository movementRepository;
    @Mock
    private CategoryRepository categoryRepository;
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
    private MovementServiceImpl service;

    private static final Long USER_ID = 1L;

    private Category expense(Long id) {
        return Category.builder().id(id).userId(USER_ID).name("Cat" + id)
                .type(CategoryType.EXPENSE).active(true).build();
    }

    private ResultMovementDTO dtoFor(Long id, Long categoryId) {
        ResultMovementDTO dto = new ResultMovementDTO();
        dto.setId(id);
        dto.setCategoryId(categoryId);
        return dto;
    }

    private MovementSaveDTO saveDto() {
        MovementSaveDTO dto = new MovementSaveDTO();
        dto.setUserId(USER_ID);
        dto.setCategoryId(10L);
        dto.setDate(LocalDate.of(2026, 3, 15));
        dto.setAmount(new BigDecimal("1000"));
        return dto;
    }

    @Nested
    @DisplayName("createMovement nesting depth")
    class NestingTests {

        @Test
        @DisplayName("Should allow a child at level 2 (parent has a parent, grandparent has none)")
        void shouldAllowLevel2() {
            MovementSaveDTO dto = saveDto();
            dto.setParentMovementId(200L);

            Movement parent = Movement.builder().id(200L).userId(USER_ID).categoryId(50L)
                    .parentMovementId(100L).date(LocalDate.now()).amount(BigDecimal.TEN).active(true).build();
            Movement grandparent = Movement.builder().id(100L).userId(USER_ID).categoryId(50L)
                    .parentMovementId(null).date(LocalDate.now()).amount(BigDecimal.TEN).active(true).build();

            when(categoryRepository.findByIdAndActiveTrue(10L)).thenReturn(Optional.of(expense(10L)));
            when(movementRepository.findByIdAndActiveTrue(200L)).thenReturn(Optional.of(parent));
            when(movementRepository.findByIdAndActiveTrue(100L)).thenReturn(Optional.of(grandparent));
            when(movementRepository.save(any(Movement.class))).thenAnswer(i -> {
                Movement m = i.getArgument(0); m.setId(5L); return m;
            });
            when(movementMapper.toResultDTO(any(Movement.class))).thenReturn(dtoFor(5L, 50L));
            when(categoryRepository.findByIdAndActiveTrue(50L)).thenReturn(Optional.of(expense(50L)));

            ResultDTO r = service.saveAndUpdate(dto);
            assertTrue(r.isCorrect());
            // child inherited parent's category
            verify(movementRepository).save(argThat(m -> m.getCategoryId().equals(50L)));
        }

        @Test
        @DisplayName("Should reject when nesting exceeds 3 levels")
        void shouldRejectTooDeep() {
            MovementSaveDTO dto = saveDto();
            dto.setParentMovementId(200L);

            Movement parent = Movement.builder().id(200L).userId(USER_ID).categoryId(50L)
                    .parentMovementId(100L).date(LocalDate.now()).amount(BigDecimal.TEN).active(true).build();
            Movement grandparent = Movement.builder().id(100L).userId(USER_ID).categoryId(50L)
                    .parentMovementId(99L).date(LocalDate.now()).amount(BigDecimal.TEN).active(true).build();

            when(categoryRepository.findByIdAndActiveTrue(10L)).thenReturn(Optional.of(expense(10L)));
            when(movementRepository.findByIdAndActiveTrue(200L)).thenReturn(Optional.of(parent));
            when(movementRepository.findByIdAndActiveTrue(100L)).thenReturn(Optional.of(grandparent));

            ResultDTO r = service.saveAndUpdate(dto);
            assertFalse(r.isCorrect());
            assertEquals(400, r.getErrorCode());
        }

        @Test
        @DisplayName("Should return 404 when parent movement not found")
        void shouldRejectMissingParent() {
            MovementSaveDTO dto = saveDto();
            dto.setParentMovementId(200L);
            when(categoryRepository.findByIdAndActiveTrue(10L)).thenReturn(Optional.of(expense(10L)));
            when(movementRepository.findByIdAndActiveTrue(200L)).thenReturn(Optional.empty());

            ResultDTO r = service.saveAndUpdate(dto);
            assertFalse(r.isCorrect());
            assertEquals(404, r.getErrorCode());
        }
    }

    @Nested
    @DisplayName("createMovement confirmed flag")
    class ConfirmedFlagTests {

        @Test
        @DisplayName("Should honor explicit confirmed=false")
        void shouldHonorConfirmedFalse() {
            MovementSaveDTO dto = saveDto();
            dto.setConfirmed(false);
            when(categoryRepository.findByIdAndActiveTrue(10L)).thenReturn(Optional.of(expense(10L)));
            when(movementRepository.save(any(Movement.class))).thenAnswer(i -> i.getArgument(0));
            when(movementMapper.toResultDTO(any(Movement.class))).thenReturn(dtoFor(1L, 10L));
            when(categoryRepository.findByIdAndActiveTrue(10L)).thenReturn(Optional.of(expense(10L)));

            service.saveAndUpdate(dto);
            verify(movementRepository).save(argThat(m -> Boolean.FALSE.equals(m.getConfirmed())));
        }

        @Test
        @DisplayName("Should default confirmed to true when null")
        void shouldDefaultConfirmedTrue() {
            MovementSaveDTO dto = saveDto();
            dto.setConfirmed(null);
            when(categoryRepository.findByIdAndActiveTrue(10L)).thenReturn(Optional.of(expense(10L)));
            when(movementRepository.save(any(Movement.class))).thenAnswer(i -> i.getArgument(0));
            when(movementMapper.toResultDTO(any(Movement.class))).thenReturn(dtoFor(1L, 10L));

            service.saveAndUpdate(dto);
            verify(movementRepository).save(argThat(m -> Boolean.TRUE.equals(m.getConfirmed())));
        }
    }

    @Nested
    @DisplayName("updateMovement")
    class UpdateTests {

        @Test
        @DisplayName("Should NOT reassign userId when category is shared (household)")
        void shouldKeepOwnerOnShared() {
            MovementSaveDTO dto = saveDto();
            dto.setId(7L);
            dto.setConfirmed(null); // covers confirmed-null branch on update
            Movement existing = Movement.builder().id(7L).userId(99L).categoryId(10L)
                    .date(LocalDate.now()).amount(BigDecimal.TEN).confirmed(true).active(true).build();
            Category shared = Category.builder().id(10L).userId(99L).name("Shared")
                    .type(CategoryType.EXPENSE).householdId(500L).active(true).build();

            when(movementRepository.findByIdAndActiveTrue(7L)).thenReturn(Optional.of(existing));
            when(categoryRepository.findByIdAndActiveTrue(10L)).thenReturn(Optional.of(shared));
            when(movementRepository.save(any(Movement.class))).thenAnswer(i -> i.getArgument(0));
            when(movementMapper.toResultDTO(any(Movement.class))).thenReturn(dtoFor(7L, 10L));

            ResultDTO r = service.saveAndUpdate(dto);
            assertTrue(r.isCorrect());
            assertEquals(99L, existing.getUserId()); // owner preserved
        }

        @Test
        @DisplayName("El dueno NUNCA cambia al actualizar, ni en categoria personal")
        void noCambiaDeDuenoAlActualizar() {
            MovementSaveDTO dto = saveDto();
            dto.setId(7L);
            dto.setUserId(2L);
            dto.setConfirmed(false);
            Movement existing = Movement.builder().id(7L).userId(99L).categoryId(10L)
                    .date(LocalDate.now()).amount(BigDecimal.TEN).confirmed(true).active(true).build();

            when(movementRepository.findByIdAndActiveTrue(7L)).thenReturn(Optional.of(existing));
            when(categoryRepository.findByIdAndActiveTrue(10L)).thenReturn(Optional.of(expense(10L)));
            when(movementRepository.save(any(Movement.class))).thenAnswer(i -> i.getArgument(0));
            when(movementMapper.toResultDTO(any(Movement.class))).thenReturn(dtoFor(7L, 10L));

            ResultDTO r = service.saveAndUpdate(dto);
            assertTrue(r.isCorrect());
            /* Esta prueba pedia lo contrario —que el movimiento pasara del
               usuario 99 al 2— y por eso el fallo aguanto tanto: la suite
               CERTIFICABA el robo de datos como comportamiento correcto.
               Mandar el id de un movimiento ajeno y tu propio userId bastaba
               para quedarte con el. */
            assertEquals(99L, existing.getUserId());
            assertFalse(existing.getConfirmed());
        }
    }

    @Nested
    @DisplayName("getAll household branch")
    class GetAllTests {

        @Test
        @DisplayName("Should use household query when user belongs to households")
        @SuppressWarnings("unchecked")
        void shouldUseHouseholdQuery() {
            MovementFilterDTO filter = new MovementFilterDTO();
            filter.setUserId(USER_ID);
            Movement m = Movement.builder().id(1L).userId(USER_ID).categoryId(10L)
                    .date(LocalDate.now()).amount(BigDecimal.TEN).active(true).build();

            when(householdService.getHouseholdIds(USER_ID)).thenReturn(List.of(500L));
            Page<Movement> page = new PageImpl<>(List.of(m), Pageable.ofSize(10), 1);
            when(movementRepository.findAllWithFiltersAndHousehold(eq(USER_ID), anyList(), any(), any(), any(), any(), any()))
                    .thenReturn(page);
            when(movementMapper.toResultDTO(m)).thenReturn(dtoFor(1L, 10L));
            when(categoryRepository.findByIdAndActiveTrue(10L)).thenReturn(Optional.of(expense(10L)));

            ResultDTO r = service.getAll(filter);
            assertTrue(r.isCorrect());
            Map<String, Object> resp = (Map<String, Object>) r.getObject();
            assertEquals(1, ((List<?>) resp.get("list")).size());
            verify(movementRepository).findAllWithFiltersAndHousehold(eq(USER_ID), anyList(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("Should use empty household list when userId is null")
        void shouldHandleNullUserId() {
            MovementFilterDTO filter = new MovementFilterDTO();
            filter.setUserId(null);
            when(movementRepository.findAllWithFilters(isNull(), any(), any(), any(), any(), any()))
                    .thenReturn(new PageImpl<>(Collections.emptyList(), Pageable.ofSize(10), 0));

            ResultDTO r = service.getAll(filter);
            assertTrue(r.isCorrect());
            verify(householdService, never()).getHouseholdIds(any());
        }
    }

    @Nested
    @DisplayName("confirmWithAmount")
    class ConfirmTests {

        @Test
        @DisplayName("Should ignore non-positive new amount but still confirm")
        void shouldIgnoreNonPositiveAmount() {
            Movement m = Movement.builder().id(1L).userId(USER_ID).categoryId(10L)
                    .date(LocalDate.now()).amount(new BigDecimal("500")).confirmed(false).active(true).build();
            when(movementRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(m));
            when(movementRepository.save(any(Movement.class))).thenAnswer(i -> i.getArgument(0));
            when(movementMapper.toResultDTO(any(Movement.class))).thenReturn(dtoFor(1L, 10L));

            ResultDTO r = service.confirmWithAmount(1L, BigDecimal.ZERO);
            assertTrue(r.isCorrect());
            assertEquals(new BigDecimal("500"), m.getAmount()); // unchanged
            assertTrue(m.getConfirmed());
        }

        @Test
        @DisplayName("Should apply a positive new amount")
        void shouldApplyPositiveAmount() {
            Movement m = Movement.builder().id(1L).userId(USER_ID).categoryId(10L)
                    .date(LocalDate.now()).amount(new BigDecimal("500")).confirmed(false).active(true).build();
            when(movementRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(m));
            when(movementRepository.save(any(Movement.class))).thenAnswer(i -> i.getArgument(0));
            when(movementMapper.toResultDTO(any(Movement.class))).thenReturn(dtoFor(1L, 10L));

            service.confirmWithAmount(1L, new BigDecimal("900"));
            assertEquals(new BigDecimal("900"), m.getAmount());
        }
    }

    @Nested
    @DisplayName("getByPeriod household branch")
    class GetByPeriodTests {

        @Test
        @DisplayName("Should use household query when user belongs to households")
        void shouldUseHouseholdQuery() {
            when(householdService.getHouseholdIds(USER_ID)).thenReturn(List.of(500L));
            when(movementRepository.findHouseholdByPeriod(eq(USER_ID), anyList(), any(), any()))
                    .thenReturn(Collections.emptyList());

            ResultDTO r = service.getByPeriod(USER_ID, LocalDate.now(), LocalDate.now());
            assertTrue(r.isCorrect());
            verify(movementRepository).findHouseholdByPeriod(eq(USER_ID), anyList(), any(), any());
        }
    }

    @Nested
    @DisplayName("getChildren edge cases")
    class GetChildrenTests {

        @Test
        @DisplayName("Should handle null amounts on parent and children (executionPct 0)")
        @SuppressWarnings("unchecked")
        void shouldHandleNullAmounts() {
            Movement parent = Movement.builder().id(50L).userId(USER_ID).categoryId(10L)
                    .date(LocalDate.now()).amount(null).active(true).build();
            Movement child = Movement.builder().id(51L).userId(USER_ID).categoryId(10L)
                    .parentMovementId(50L).date(LocalDate.now()).amount(null).active(true).build();

            when(movementRepository.findByIdAndActiveTrue(50L)).thenReturn(Optional.of(parent));
            when(movementRepository.findByParentMovementIdAndActiveTrue(50L)).thenReturn(List.of(child));
            when(movementMapper.toResultDTO(child)).thenReturn(dtoFor(51L, 10L));
            when(categoryRepository.findByIdAndActiveTrue(10L)).thenReturn(Optional.of(expense(10L)));

            ResultDTO r = service.getChildren(50L);
            assertTrue(r.isCorrect());
            Map<String, Object> result = (Map<String, Object>) r.getObject();
            assertEquals(BigDecimal.ZERO, result.get("childrenTotal"));
            assertEquals(0.0, result.get("executionPct"));
        }
    }

    @Nested
    @DisplayName("delete transfer pair")
    class DeleteTests {

        @Test
        @DisplayName("Should also deactivate the transfer pair movement")
        void shouldDeleteTransferPair() {
            Movement entity = Movement.builder().id(1L).userId(USER_ID).categoryId(10L)
                    .date(LocalDate.now()).amount(BigDecimal.TEN)
                    .isTransfer(true).transferPairId(2L).active(true).build();
            Movement pair = Movement.builder().id(2L).userId(USER_ID).categoryId(11L)
                    .date(LocalDate.now()).amount(BigDecimal.TEN)
                    .isTransfer(true).transferPairId(1L).active(true).build();

            when(movementRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(entity));
            when(movementRepository.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(pair));

            ResultDTO r = service.delete(1L);
            assertTrue(r.isCorrect());
            assertFalse(entity.getActive());
            assertFalse(pair.getActive());
            verify(movementRepository).save(pair);
        }
    }

    @Nested
    @DisplayName("enrichWithCategory branches")
    class EnrichTests {

        @Test
        @DisplayName("Should skip enrichment when categoryId is null and handle null category type")
        void shouldHandleNullCategoryIdAndType() {
            // movement whose mapped DTO has null categoryId -> no enrichment path
            Movement m = Movement.builder().id(1L).userId(USER_ID).categoryId(10L)
                    .date(LocalDate.now()).amount(BigDecimal.TEN).active(true).build();
            ResultMovementDTO noCat = new ResultMovementDTO();
            noCat.setId(1L);
            noCat.setCategoryId(null);
            when(movementRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(m));
            when(movementMapper.toResultDTO(m)).thenReturn(noCat);

            ResultDTO r = service.getById(1L);
            assertTrue(r.isCorrect());
            assertNull(((ResultMovementDTO) r.getObject()).getCategoryName());
        }

        @Test
        @DisplayName("Should leave categoryType null when the category has no type")
        void shouldHandleNullType() {
            Movement m = Movement.builder().id(2L).userId(USER_ID).categoryId(20L)
                    .date(LocalDate.now()).amount(BigDecimal.TEN).active(true).build();
            Category noType = Category.builder().id(20L).userId(USER_ID).name("NoType")
                    .type(null).active(true).build();
            when(movementRepository.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(m));
            when(movementMapper.toResultDTO(m)).thenReturn(dtoFor(2L, 20L));
            when(categoryRepository.findByIdAndActiveTrue(20L)).thenReturn(Optional.of(noType));

            ResultDTO r = service.getById(2L);
            ResultMovementDTO dto = (ResultMovementDTO) r.getObject();
            assertEquals("NoType", dto.getCategoryName());
            assertNull(dto.getCategoryType());
        }

        @Test
        @DisplayName("Should refresh category data between calls (ThreadLocal cache cleared per request)")
        void shouldNotServeStaleCategory() {
            Movement m = Movement.builder().id(3L).userId(USER_ID).categoryId(30L)
                    .date(LocalDate.now()).amount(BigDecimal.TEN).active(true).build();
            when(movementRepository.findByIdAndActiveTrue(3L)).thenReturn(Optional.of(m));
            when(movementMapper.toResultDTO(m)).thenReturn(dtoFor(3L, 30L), dtoFor(3L, 30L));

            when(categoryRepository.findByIdAndActiveTrue(30L))
                    .thenReturn(Optional.of(Category.builder().id(30L).userId(USER_ID).name("Antes")
                            .type(CategoryType.EXPENSE).active(true).build()));
            assertEquals("Antes", ((ResultMovementDTO) service.getById(3L).getObject()).getCategoryName());

            when(categoryRepository.findByIdAndActiveTrue(30L))
                    .thenReturn(Optional.of(Category.builder().id(30L).userId(USER_ID).name("Despues")
                            .type(CategoryType.EXPENSE).active(true).build()));
            assertEquals("Despues", ((ResultMovementDTO) service.getById(3L).getObject()).getCategoryName());
        }
    }
}

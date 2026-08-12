package com.ohchurus.budget.service;

import com.ohchurus.budget.dto.output.ResultDTO;
import com.ohchurus.budget.entity.Household;
import com.ohchurus.budget.entity.HouseholdMember;
import com.ohchurus.budget.repository.HouseholdMemberRepository;
import com.ohchurus.budget.repository.HouseholdRepository;
import com.ohchurus.budget.service.impl.HouseholdServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("HouseholdServiceImpl")
class HouseholdServiceImplTest {

    @Mock
    private HouseholdRepository householdRepository;

    @Mock
    private HouseholdMemberRepository memberRepository;

    @Mock
    private com.ohchurus.budget.repository.CategoryRepository categoryRepository;

    @Mock
    private com.ohchurus.budget.repository.BudgetAllocationRepository allocationRepository;

    @Mock
    private com.ohchurus.budget.client.DirectorioDeUsuarios directorio;
    /* addMember/removeMember exigen ahora que quien llama sea el OWNER de ese
       hogar. Antes no comprobaban nada: cualquiera enviaba {householdId, su
       userId} y entraba en la casa de otra pareja. Estas pruebas se ejecutan
       como el OWNER para poder seguir probando la LOGICA; que un extrano no
       pueda lo demuestra AislamientoEntreUsuariosTest. */
    @BeforeEach
    void ejecutarComoDuenoDelHogar() {
        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                "dueno@ohchurus.com", null, java.util.Collections.emptyList());
        auth.setDetails(USER_ID);
        org.springframework.security.core.context.SecurityContextHolder.getContext()
                .setAuthentication(auth);
        org.mockito.Mockito.lenient().when(memberRepository
                .findByHouseholdIdAndUserIdAndActiveTrue(HOUSEHOLD_ID, USER_ID))
                .thenReturn(Optional.of(HouseholdMember.builder()
                        .id(1L).householdId(HOUSEHOLD_ID).userId(USER_ID)
                        .role("OWNER").active(true).build()));
        /* Ahora se exige que el hogar exista y siga activo antes de tocarlo:
           un hogar borrado dejaba sus filas de miembros activas y su OWNER
           podia seguir metiendo gente en una casa que ya no existe. */
        org.mockito.Mockito.lenient().when(householdRepository.findByIdAndActiveTrue(HOUSEHOLD_ID))
                .thenReturn(Optional.of(household()));
    }

    @org.junit.jupiter.api.AfterEach
    void limpiarSesion() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }


    @InjectMocks
    private HouseholdServiceImpl householdService;

    private static final Long USER_ID = 1L;
    private static final Long HOUSEHOLD_ID = 100L;

    private Household household() {
        return Household.builder().id(HOUSEHOLD_ID).name("Familia").active(true).build();
    }

    private HouseholdMember owner() {
        return HouseholdMember.builder()
                .id(1L).householdId(HOUSEHOLD_ID).userId(USER_ID).role("OWNER").active(true).build();
    }

    private HouseholdMember member(Long userId) {
        return HouseholdMember.builder()
                .id(2L).householdId(HOUSEHOLD_ID).userId(userId).role("MEMBER").active(true).build();
    }

    @Nested
    @DisplayName("create")
    class CreateTests {

        @Test
        @DisplayName("Should create household and register owner")
        void shouldCreateHousehold() {
            when(householdRepository.save(any(Household.class))).thenReturn(household());
            when(memberRepository.save(any(HouseholdMember.class))).thenReturn(owner());

            ResultDTO result = householdService.create("Familia", USER_ID);

            assertTrue(result.isCorrect());
            assertSame(Household.class, result.getObject().getClass());
            verify(householdRepository).save(any(Household.class));
            // owner membership registered
            verify(memberRepository).save(argThat(m -> "OWNER".equals(m.getRole())
                    && USER_ID.equals(m.getUserId())
                    && HOUSEHOLD_ID.equals(m.getHouseholdId())));
        }

        @Test
        @DisplayName("Should return 500 when persistence fails")
        void shouldHandleException() {
            when(householdRepository.save(any(Household.class))).thenThrow(new RuntimeException("DB error"));

            ResultDTO result = householdService.create("Familia", USER_ID);

            assertFalse(result.isCorrect());
            assertEquals(500, result.getErrorCode());
        }
    }

    @Nested
    @DisplayName("addMember")
    class AddMemberTests {

        @Test
        @DisplayName("Should add a new member as MEMBER role")
        void shouldAddMember() {
            when(memberRepository.existsByHouseholdIdAndUserIdAndActiveTrue(HOUSEHOLD_ID, 2L)).thenReturn(false);
            when(memberRepository.save(any(HouseholdMember.class))).thenReturn(member(2L));

            ResultDTO result = householdService.addMember(HOUSEHOLD_ID, 2L);

            assertTrue(result.isCorrect());
            verify(memberRepository).save(argThat(m -> "MEMBER".equals(m.getRole())));
        }

        @Test
        @DisplayName("Should reject when user is already a member")
        void shouldRejectDuplicateMember() {
            when(memberRepository.existsByHouseholdIdAndUserIdAndActiveTrue(HOUSEHOLD_ID, 2L)).thenReturn(true);

            ResultDTO result = householdService.addMember(HOUSEHOLD_ID, 2L);

            assertFalse(result.isCorrect());
            assertEquals(400, result.getErrorCode());
            verify(memberRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should return 500 when persistence fails")
        void shouldHandleException() {
            when(memberRepository.existsByHouseholdIdAndUserIdAndActiveTrue(HOUSEHOLD_ID, 2L)).thenReturn(false);
            when(memberRepository.save(any(HouseholdMember.class))).thenThrow(new RuntimeException("DB error"));

            ResultDTO result = householdService.addMember(HOUSEHOLD_ID, 2L);

            assertFalse(result.isCorrect());
            assertEquals(500, result.getErrorCode());
        }
    }

    @Nested
    @DisplayName("removeMember")
    class RemoveMemberTests {

        @Test
        @DisplayName("Should soft-delete a regular member")
        void shouldRemoveMember() {
            HouseholdMember m = member(2L);
            when(memberRepository.findByHouseholdIdAndUserIdAndActiveTrue(HOUSEHOLD_ID, 2L))
                    .thenReturn(Optional.of(m));

            ResultDTO result = householdService.removeMember(HOUSEHOLD_ID, 2L);

            assertTrue(result.isCorrect());
            assertFalse(m.getActive());
            verify(memberRepository).save(m);
        }

        @Test
        @DisplayName("Should return 404 when member not found")
        void shouldReturnNotFound() {
            when(memberRepository.findByHouseholdIdAndUserIdAndActiveTrue(HOUSEHOLD_ID, 2L))
                    .thenReturn(Optional.empty());

            ResultDTO result = householdService.removeMember(HOUSEHOLD_ID, 2L);

            assertFalse(result.isCorrect());
            assertEquals(404, result.getErrorCode());
        }

        @Test
        @DisplayName("Should not allow removing the OWNER")
        void shouldNotRemoveOwner() {
            when(memberRepository.findByHouseholdIdAndUserIdAndActiveTrue(HOUSEHOLD_ID, USER_ID))
                    .thenReturn(Optional.of(owner()));

            ResultDTO result = householdService.removeMember(HOUSEHOLD_ID, USER_ID);

            assertFalse(result.isCorrect());
            assertEquals(400, result.getErrorCode());
            verify(memberRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should return 500 when persistence fails")
        void shouldHandleException() {
            when(memberRepository.findByHouseholdIdAndUserIdAndActiveTrue(HOUSEHOLD_ID, 2L))
                    .thenThrow(new RuntimeException("DB error"));

            ResultDTO result = householdService.removeMember(HOUSEHOLD_ID, 2L);

            assertFalse(result.isCorrect());
            assertEquals(500, result.getErrorCode());
        }
    }

    @Nested
    @DisplayName("invitarPorCorreo")
    class InvitarPorCorreoTests {

        @Test
        @DisplayName("Resuelve el correo contra el directorio y mete a ese usuario")
        void invitaAlDuenoDelCorreo() {
            when(directorio.idPorCorreo("bruno@ohchurus.com")).thenReturn(2L);
            when(memberRepository.existsByHouseholdIdAndUserIdAndActiveTrue(HOUSEHOLD_ID, 2L)).thenReturn(false);
            when(memberRepository.save(any(HouseholdMember.class))).thenReturn(member(2L));

            ResultDTO result = householdService.invitarPorCorreo(HOUSEHOLD_ID, "bruno@ohchurus.com");

            assertTrue(result.isCorrect());
            verify(memberRepository).save(argThat(m -> Long.valueOf(2L).equals(m.getUserId())
                    && "MEMBER".equals(m.getRole())));
        }

        @Test
        @DisplayName("Un correo que no existe no mete a nadie")
        void correoDesconocido() {
            when(directorio.idPorCorreo("nadie@ohchurus.com")).thenReturn(null);

            ResultDTO result = householdService.invitarPorCorreo(HOUSEHOLD_ID, "nadie@ohchurus.com");

            assertFalse(result.isCorrect());
            assertEquals(404, result.getErrorCode());
            verify(memberRepository, never()).save(any());
        }

        @Test
        @DisplayName("No se invita a un hogar que no existe")
        void hogarInexistente() {
            when(householdRepository.findByIdAndActiveTrue(999L)).thenReturn(Optional.empty());

            ResultDTO result = householdService.invitarPorCorreo(999L, "bruno@ohchurus.com");

            assertFalse(result.isCorrect());
            assertEquals(404, result.getErrorCode());
            verify(directorio, never()).idPorCorreo(any());
        }
    }

    @Nested
    @DisplayName("Al expulsar se limpia el rastro del expulsado")
    class LimpiezaAlExpulsar {

        /* Sacar a alguien solo apagaba su fila de miembro. Sus subcategorias
           personales seguian colgando de una categoria del hogar que ya no ve
           —desaparecian del arbol— y sus asignaciones sobre categorias del
           hogar quedaban activas apuntando a algo invisible. */

        @Test
        @DisplayName("La subcategoria personal del expulsado sube a raiz")
        void subcategoriaSubeARaiz() {
            com.ohchurus.budget.entity.Category delHogar = com.ohchurus.budget.entity.Category.builder()
                    .id(10L).userId(USER_ID).name("Arriendo").householdId(HOUSEHOLD_ID).active(true).build();
            com.ohchurus.budget.entity.Category hijaDelExpulsado = com.ohchurus.budget.entity.Category.builder()
                    .id(11L).userId(2L).name("Mi parte").parentId(10L).active(true).build();

            when(memberRepository.findByHouseholdIdAndUserIdAndActiveTrue(HOUSEHOLD_ID, 2L))
                    .thenReturn(Optional.of(member(2L)));
            when(categoryRepository.findByHouseholdIdAndActiveTrue(HOUSEHOLD_ID)).thenReturn(List.of(delHogar));
            when(categoryRepository.findByParentIdAndActiveTrue(10L)).thenReturn(List.of(hijaDelExpulsado));

            ResultDTO result = householdService.removeMember(HOUSEHOLD_ID, 2L);

            assertTrue(result.isCorrect());
            assertNull(hijaDelExpulsado.getParentId());
            verify(categoryRepository).save(hijaDelExpulsado);
        }

        @Test
        @DisplayName("La subcategoria de OTRO miembro no se toca")
        void noTocaLoDeLosDemas() {
            com.ohchurus.budget.entity.Category delHogar = com.ohchurus.budget.entity.Category.builder()
                    .id(10L).userId(USER_ID).name("Arriendo").householdId(HOUSEHOLD_ID).active(true).build();
            com.ohchurus.budget.entity.Category hijaDelQueSeQueda = com.ohchurus.budget.entity.Category.builder()
                    .id(12L).userId(USER_ID).name("Su parte").parentId(10L).active(true).build();

            when(memberRepository.findByHouseholdIdAndUserIdAndActiveTrue(HOUSEHOLD_ID, 2L))
                    .thenReturn(Optional.of(member(2L)));
            when(categoryRepository.findByHouseholdIdAndActiveTrue(HOUSEHOLD_ID)).thenReturn(List.of(delHogar));
            when(categoryRepository.findByParentIdAndActiveTrue(10L)).thenReturn(List.of(hijaDelQueSeQueda));

            householdService.removeMember(HOUSEHOLD_ID, 2L);

            assertEquals(Long.valueOf(10L), hijaDelQueSeQueda.getParentId());
            verify(categoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("Sus asignaciones sobre categorias del hogar se desactivan")
        void asignacionesHuerfanasSeDesactivan() {
            com.ohchurus.budget.entity.Category delHogar = com.ohchurus.budget.entity.Category.builder()
                    .id(10L).userId(USER_ID).name("Arriendo").householdId(HOUSEHOLD_ID).active(true).build();
            com.ohchurus.budget.entity.BudgetAllocation asignacion =
                    com.ohchurus.budget.entity.BudgetAllocation.builder()
                            .id(70L).userId(2L).categoryId(10L)
                            .periodStart(java.time.LocalDate.now()).periodEnd(java.time.LocalDate.now())
                            .allocatedAmount(java.math.BigDecimal.TEN).active(true).build();

            when(memberRepository.findByHouseholdIdAndUserIdAndActiveTrue(HOUSEHOLD_ID, 2L))
                    .thenReturn(Optional.of(member(2L)));
            when(categoryRepository.findByHouseholdIdAndActiveTrue(HOUSEHOLD_ID)).thenReturn(List.of(delHogar));
            when(allocationRepository.findByUserIdAndActiveTrueAndCategoryIdIn(2L, List.of(10L)))
                    .thenReturn(List.of(asignacion));

            householdService.removeMember(HOUSEHOLD_ID, 2L);

            assertFalse(asignacion.getActive());
            verify(allocationRepository).save(asignacion);
        }
    }

    @Nested
    @DisplayName("getByUser")
    class GetByUserTests {

        @Test
        @DisplayName("Should return households with members and resolved name")
        @SuppressWarnings("unchecked")
        void shouldReturnHouseholds() {
            when(memberRepository.findByUserIdAndActiveTrue(USER_ID))
                    .thenReturn(List.of(owner()));
            when(householdRepository.findByIdAndActiveTrue(HOUSEHOLD_ID))
                    .thenReturn(Optional.of(household()));
            when(memberRepository.findByHouseholdIdAndActiveTrue(HOUSEHOLD_ID))
                    .thenReturn(List.of(owner(), member(2L)));

            ResultDTO result = householdService.getByUser(USER_ID);

            assertTrue(result.isCorrect());
            List<Map<String, Object>> list = (List<Map<String, Object>>) result.getObject();
            assertEquals(1, list.size());
            Map<String, Object> first = list.get(0);
            assertEquals("Familia", first.get("name"));
            assertEquals(2, first.get("memberCount"));
            assertEquals("OWNER", first.get("role"));
        }

        @Test
        @DisplayName("Should use empty name when household record is missing")
        @SuppressWarnings("unchecked")
        void shouldHandleMissingHousehold() {
            when(memberRepository.findByUserIdAndActiveTrue(USER_ID))
                    .thenReturn(List.of(owner()));
            when(householdRepository.findByIdAndActiveTrue(HOUSEHOLD_ID))
                    .thenReturn(Optional.empty());
            when(memberRepository.findByHouseholdIdAndActiveTrue(HOUSEHOLD_ID))
                    .thenReturn(List.of(owner()));

            ResultDTO result = householdService.getByUser(USER_ID);

            assertTrue(result.isCorrect());
            List<Map<String, Object>> list = (List<Map<String, Object>>) result.getObject();
            assertEquals("", list.get(0).get("name"));
        }

        @Test
        @DisplayName("Should return empty list when user has no memberships")
        @SuppressWarnings("unchecked")
        void shouldReturnEmpty() {
            when(memberRepository.findByUserIdAndActiveTrue(USER_ID)).thenReturn(Collections.emptyList());

            ResultDTO result = householdService.getByUser(USER_ID);

            assertTrue(result.isCorrect());
            assertTrue(((List<Map<String, Object>>) result.getObject()).isEmpty());
        }

        @Test
        @DisplayName("Should return 500 when query fails")
        void shouldHandleException() {
            when(memberRepository.findByUserIdAndActiveTrue(USER_ID))
                    .thenThrow(new RuntimeException("DB error"));

            ResultDTO result = householdService.getByUser(USER_ID);

            assertFalse(result.isCorrect());
            assertEquals(500, result.getErrorCode());
        }
    }

    @Nested
    @DisplayName("getHouseholdIds")
    class GetHouseholdIdsTests {

        @Test
        @DisplayName("Should map memberships to household ids")
        void shouldReturnIds() {
            when(memberRepository.findByUserIdAndActiveTrue(USER_ID))
                    .thenReturn(List.of(owner(), member(2L)));

            List<Long> ids = householdService.getHouseholdIds(USER_ID);

            assertEquals(List.of(HOUSEHOLD_ID, HOUSEHOLD_ID), ids);
        }

        @Test
        @DisplayName("Should return empty list when no memberships")
        void shouldReturnEmpty() {
            when(memberRepository.findByUserIdAndActiveTrue(USER_ID)).thenReturn(Collections.emptyList());

            assertTrue(householdService.getHouseholdIds(USER_ID).isEmpty());
        }
    }
}

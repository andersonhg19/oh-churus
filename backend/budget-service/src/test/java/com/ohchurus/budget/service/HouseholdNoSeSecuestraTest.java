package com.ohchurus.budget.service;

import com.ohchurus.budget.client.DirectorioDeUsuarios;
import com.ohchurus.budget.dto.output.ResultDTO;
import com.ohchurus.budget.entity.Household;
import com.ohchurus.budget.entity.HouseholdMember;
import com.ohchurus.budget.repository.BudgetAllocationRepository;
import com.ohchurus.budget.repository.CategoryRepository;
import com.ohchurus.budget.repository.HouseholdMemberRepository;
import com.ohchurus.budget.repository.HouseholdRepository;
import com.ohchurus.budget.service.impl.HouseholdServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * QA A1: el OWNER de un hogar metia a cualquier usuario existente en su casa
 * sin consentimiento, y la victima no podia salirse. Ahora solo se agrega a
 * quien no tiene hogar activo, el mensaje de rechazo no revela si el correo
 * existe, y un miembro puede abandonar el hogar por su cuenta.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Nadie queda metido en un hogar ajeno sin poder salir")
class HouseholdNoSeSecuestraTest {

    private static final Long INTRUSO = 5L;
    private static final Long VICTIMA = 3L;
    private static final Long HOGAR_INTRUSO = 30L;
    private static final Long HOGAR_VICTIMA = 10L;

    @Mock private HouseholdRepository householdRepository;
    @Mock private HouseholdMemberRepository memberRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private BudgetAllocationRepository allocationRepository;
    @Mock private DirectorioDeUsuarios directorio;

    @InjectMocks private HouseholdServiceImpl servicio;

    private static HouseholdMember miembro(Long hogar, Long usuario, String rol) {
        return HouseholdMember.builder().id(hogar * 100 + usuario).householdId(hogar)
                .userId(usuario).role(rol).active(true).build();
    }

    private static void comoUsuario(Long id) {
        var auth = new UsernamePasswordAuthenticationToken("u" + id + "@ohchurus.com", null,
                Collections.emptyList());
        auth.setDetails(id);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @BeforeEach
    void hogaresActivos() {
        lenient().when(householdRepository.findByIdAndActiveTrue(HOGAR_INTRUSO))
                .thenReturn(Optional.of(Household.builder().id(HOGAR_INTRUSO).name("Intruso").active(true).build()));
        lenient().when(householdRepository.findByIdAndActiveTrue(HOGAR_VICTIMA))
                .thenReturn(Optional.of(Household.builder().id(HOGAR_VICTIMA).name("Casa").active(true).build()));
        lenient().when(memberRepository.findByHouseholdIdAndUserIdAndActiveTrue(HOGAR_INTRUSO, INTRUSO))
                .thenReturn(Optional.of(miembro(HOGAR_INTRUSO, INTRUSO, "OWNER")));
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("Agregar")
    class Agregar {

        @Test
        @DisplayName("no se agrega por id a quien ya vive en otro hogar activo")
        void yaTieneHogar() {
            comoUsuario(INTRUSO);
            when(memberRepository.findByUserIdAndActiveTrue(VICTIMA))
                    .thenReturn(List.of(miembro(HOGAR_VICTIMA, VICTIMA, "OWNER")));

            ResultDTO r = servicio.addMember(HOGAR_INTRUSO, VICTIMA);

            assertFalse(r.isCorrect());
            assertEquals(400, r.getErrorCode());
            verify(memberRepository, never()).save(any());
        }

        @Test
        @DisplayName("una membresia en un hogar ya borrado no cuenta: se puede agregar")
        void hogarBorradoNoCuenta() {
            comoUsuario(INTRUSO);
            when(householdRepository.findByIdAndActiveTrue(77L)).thenReturn(Optional.empty());
            when(memberRepository.findByUserIdAndActiveTrue(VICTIMA))
                    .thenReturn(List.of(miembro(77L, VICTIMA, "OWNER")));
            when(memberRepository.save(any(HouseholdMember.class))).thenAnswer(i -> i.getArgument(0));

            ResultDTO r = servicio.addMember(HOGAR_INTRUSO, VICTIMA);

            assertTrue(r.isCorrect());
        }

        @Test
        @DisplayName("invitar un correo inexistente y uno con hogar responde exactamente igual")
        void sinEnumeracion() {
            comoUsuario(INTRUSO);
            when(directorio.idPorCorreo("nadie@ohchurus.com")).thenReturn(null);
            when(directorio.idPorCorreo("andy@ohchurus.com")).thenReturn(VICTIMA);
            when(memberRepository.findByUserIdAndActiveTrue(VICTIMA))
                    .thenReturn(List.of(miembro(HOGAR_VICTIMA, VICTIMA, "MEMBER")));

            ResultDTO inexistente = servicio.invitarPorCorreo(HOGAR_INTRUSO, "nadie@ohchurus.com");
            ResultDTO conHogar = servicio.invitarPorCorreo(HOGAR_INTRUSO, "andy@ohchurus.com");

            assertFalse(inexistente.isCorrect());
            assertFalse(conHogar.isCorrect());
            assertEquals(inexistente.getMessage(), conHogar.getMessage());
            assertEquals(inexistente.getErrorCode(), conHogar.getErrorCode());
            verify(memberRepository, never()).save(any());
        }

        @Test
        @DisplayName("quien no es OWNER sigue sin poder agregar")
        void soloElDueno() {
            comoUsuario(VICTIMA);

            ResultDTO r = servicio.addMember(HOGAR_INTRUSO, 9L);

            assertFalse(r.isCorrect());
            verify(memberRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Salir")
    class Salir {

        @Test
        @DisplayName("un miembro puede salirse por su cuenta del hogar donde lo metieron")
        void miembroSeSale() {
            comoUsuario(VICTIMA);
            HouseholdMember yo = miembro(HOGAR_INTRUSO, VICTIMA, "MEMBER");
            when(memberRepository.findByHouseholdIdAndUserIdAndActiveTrue(HOGAR_INTRUSO, VICTIMA))
                    .thenReturn(Optional.of(yo));

            ResultDTO r = servicio.removeMember(HOGAR_INTRUSO, VICTIMA);

            assertTrue(r.isCorrect());
            assertFalse(yo.getActive());
            verify(memberRepository).save(yo);
        }

        @Test
        @DisplayName("un miembro no puede sacar a otro")
        void miembroNoSacaAOtro() {
            comoUsuario(VICTIMA);
            when(memberRepository.findByHouseholdIdAndUserIdAndActiveTrue(HOGAR_INTRUSO, VICTIMA))
                    .thenReturn(Optional.of(miembro(HOGAR_INTRUSO, VICTIMA, "MEMBER")));

            ResultDTO r = servicio.removeMember(HOGAR_INTRUSO, 8L);

            assertFalse(r.isCorrect());
            assertEquals(404, r.getErrorCode());
            verify(memberRepository, never()).save(any());
        }

        @Test
        @DisplayName("el OWNER no se puede salir (el hogar quedaria sin dueno)")
        void duenoNoSeSale() {
            comoUsuario(INTRUSO);

            ResultDTO r = servicio.removeMember(HOGAR_INTRUSO, INTRUSO);

            assertFalse(r.isCorrect());
            assertEquals(400, r.getErrorCode());
            verify(memberRepository, never()).save(any());
        }
    }
}

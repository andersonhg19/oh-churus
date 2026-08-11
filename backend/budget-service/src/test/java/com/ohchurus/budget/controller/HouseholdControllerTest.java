package com.ohchurus.budget.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ohchurus.budget.dto.output.ResultDTO;
import com.ohchurus.budget.security.JWTAuthorizationFilter;
import com.ohchurus.budget.security.SecParams;
import com.ohchurus.budget.service.impl.HouseholdServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(HouseholdController.class)
@AutoConfigureMockMvc(addFilters = false)
class HouseholdControllerTest {
    /* Los controllers ya no aceptan el userId del cuerpo: lo sacan del token.
       Estas pruebas usan @WebMvcTest con los filtros apagados, asi que no hay
       token; se planta la identidad a mano para poder seguir probando el
       CONTROLLER. Que el userId del cuerpo se ignore de verdad lo demuestra
       AislamientoEntreUsuariosTest con la aplicacion entera levantada. */
    @org.junit.jupiter.api.BeforeEach
    void autenticarComoUsuario1() {
        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                "usuario1@ohchurus.com", null, java.util.Collections.emptyList());
        auth.setDetails(1L);
        org.springframework.security.core.context.SecurityContextHolder.getContext()
                .setAuthentication(auth);
    }

    @org.junit.jupiter.api.AfterEach
    void limpiarSesion() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }


    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private HouseholdServiceImpl householdService;

    @MockBean
    private JWTAuthorizationFilter jwtAuthorizationFilter;

    @MockBean
    private SecParams secParams;

    private ResultDTO successResult;

    @BeforeEach
    void setUp() {
        successResult = new ResultDTO("data");
    }

    @Test
    @DisplayName("POST /create should delegate to service")
    void shouldCreate() throws Exception {
        when(householdService.create(eq("Familia"), eq(1L))).thenReturn(successResult);

        mockMvc.perform(post("/v1/household/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Familia", "userId", 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correct").value(true));

        verify(householdService).create("Familia", 1L);
    }

    @Test
    @DisplayName("POST /add-member should delegate to service")
    void shouldAddMember() throws Exception {
        when(householdService.addMember(eq(100L), eq(2L))).thenReturn(successResult);

        mockMvc.perform(post("/v1/household/add-member")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("householdId", 100, "userId", 2))))
                .andExpect(status().isOk());

        verify(householdService).addMember(100L, 2L);
    }

    @Test
    @DisplayName("POST /remove-member should delegate to service")
    void shouldRemoveMember() throws Exception {
        when(householdService.removeMember(eq(100L), eq(2L))).thenReturn(successResult);

        mockMvc.perform(post("/v1/household/remove-member")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("householdId", 100, "userId", 2))))
                .andExpect(status().isOk());

        verify(householdService).removeMember(100L, 2L);
    }

    @Test
    @DisplayName("POST /by-user should delegate to service")
    void shouldGetByUser() throws Exception {
        when(householdService.getByUser(eq(1L))).thenReturn(successResult);

        mockMvc.perform(post("/v1/household/by-user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("userId", 1))))
                .andExpect(status().isOk());

        verify(householdService).getByUser(1L);
    }
}

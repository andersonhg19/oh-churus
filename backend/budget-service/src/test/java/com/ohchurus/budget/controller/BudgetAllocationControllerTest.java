package com.ohchurus.budget.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ohchurus.budget.dto.output.ResultDTO;
import com.ohchurus.budget.security.JWTAuthorizationFilter;
import com.ohchurus.budget.security.SecParams;
import com.ohchurus.budget.service.impl.BudgetAllocationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BudgetAllocationController.class)
@AutoConfigureMockMvc(addFilters = false)
class BudgetAllocationControllerTest {
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
    private BudgetAllocationServiceImpl service;

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
    @DisplayName("POST /save should parse body (with optional fields) and delegate")
    void shouldSaveWithOptionalFields() throws Exception {
        when(service.save(eq(1L), eq(20L), eq(new BigDecimal("500000")), eq("nota"), eq(15), any()))
                .thenReturn(successResult);

        Map<String, Object> body = new HashMap<>();
        body.put("userId", 1);
        body.put("categoryId", 20);
        body.put("amount", "500000");
        body.put("notes", "nota");
        body.put("budgetStartDay", 15);
        body.put("referenceDate", "2026-03-15");

        mockMvc.perform(post("/v1/budget-allocation/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correct").value(true));

        verify(service).save(1L, 20L, new BigDecimal("500000"), "nota", 15, java.time.LocalDate.of(2026, 3, 15));
    }

    @Test
    @DisplayName("POST /save should default budgetStartDay and null notes/referenceDate")
    void shouldSaveWithDefaults() throws Exception {
        when(service.save(eq(1L), eq(20L), eq(new BigDecimal("100")), eq(null), eq(1), eq(null)))
                .thenReturn(successResult);

        Map<String, Object> body = new HashMap<>();
        body.put("userId", 1);
        body.put("categoryId", 20);
        body.put("amount", "100");

        mockMvc.perform(post("/v1/budget-allocation/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        verify(service).save(1L, 20L, new BigDecimal("100"), null, 1, null);
    }

    @Test
    @DisplayName("POST /list should delegate with defaults")
    void shouldList() throws Exception {
        when(service.list(eq(1L), eq(1), eq(null))).thenReturn(successResult);

        mockMvc.perform(post("/v1/budget-allocation/list")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("userId", 1))))
                .andExpect(status().isOk());

        verify(service).list(1L, 1, null);
    }

    @Test
    @DisplayName("POST /list should parse optional budgetStartDay and referenceDate")
    void shouldListWithOptionalFields() throws Exception {
        when(service.list(eq(1L), eq(15), eq(java.time.LocalDate.of(2026, 3, 15)))).thenReturn(successResult);

        Map<String, Object> body = new HashMap<>();
        body.put("userId", 1);
        body.put("budgetStartDay", 15);
        body.put("referenceDate", "2026-03-15");

        mockMvc.perform(post("/v1/budget-allocation/list")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        verify(service).list(1L, 15, java.time.LocalDate.of(2026, 3, 15));
    }

    @Test
    @DisplayName("POST /summary should delegate with defaults")
    void shouldSummary() throws Exception {
        when(service.summary(eq(1L), eq(1), eq(null))).thenReturn(successResult);

        mockMvc.perform(post("/v1/budget-allocation/summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("userId", 1))))
                .andExpect(status().isOk());

        verify(service).summary(1L, 1, null);
    }

    @Test
    @DisplayName("POST /summary should parse optional budgetStartDay and referenceDate")
    void shouldSummaryWithOptionalFields() throws Exception {
        when(service.summary(eq(1L), eq(15), eq(java.time.LocalDate.of(2026, 3, 15)))).thenReturn(successResult);

        Map<String, Object> body = new HashMap<>();
        body.put("userId", 1);
        body.put("budgetStartDay", 15);
        body.put("referenceDate", "2026-03-15");

        mockMvc.perform(post("/v1/budget-allocation/summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        verify(service).summary(1L, 15, java.time.LocalDate.of(2026, 3, 15));
    }

    @Test
    @DisplayName("POST /save, /list, /summary should treat explicit null referenceDate as null")
    void shouldHandleExplicitNullReferenceDate() throws Exception {
        when(service.save(eq(1L), eq(20L), any(), isNull(), eq(1), isNull())).thenReturn(successResult);
        when(service.list(eq(1L), eq(1), isNull())).thenReturn(successResult);
        when(service.summary(eq(1L), eq(1), isNull())).thenReturn(successResult);

        mockMvc.perform(post("/v1/budget-allocation/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":1,\"categoryId\":20,\"amount\":\"100\",\"referenceDate\":null}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/v1/budget-allocation/list")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":1,\"referenceDate\":null}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/v1/budget-allocation/summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":1,\"referenceDate\":null}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /delete/{id} should delegate")
    void shouldDelete() throws Exception {
        when(service.delete(7L)).thenReturn(successResult);

        mockMvc.perform(post("/v1/budget-allocation/delete/7")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(service).delete(7L);
    }
}

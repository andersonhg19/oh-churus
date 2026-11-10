package com.ohchurus.budget.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ohchurus.budget.dto.input.DashboardRequestDTO;
import com.ohchurus.budget.dto.output.ResultDTO;
import com.ohchurus.budget.security.JWTAuthorizationFilter;
import com.ohchurus.budget.security.SecParams;
import com.ohchurus.budget.service.DashboardService;
import com.ohchurus.budget.service.impl.BudgetAllocationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DashboardController.class)
@AutoConfigureMockMvc(addFilters = false)
class DashboardControllerTest {
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
    private DashboardService dashboardService;

    @MockBean
    private JWTAuthorizationFilter jwtAuthorizationFilter;

    @MockBean
    private SecParams secParams;

    private ResultDTO successResult;
    private ResultDTO errorResult;
    private DashboardRequestDTO validRequest;

    @BeforeEach
    void setUp() {
        successResult = new ResultDTO("data");
        errorResult = new ResultDTO(false, "Error", 100);

        validRequest = new DashboardRequestDTO();
        validRequest.setUserId(1L);
        validRequest.setBudgetStartDay(15);
    }

    @Nested
    @DisplayName("POST /v1/dashboard/pending")
    class PendingTests {

        @Test
        @DisplayName("Should return pending movements")
        void shouldReturnPending() throws Exception {
            when(dashboardService.getPending(eq(1L), eq(15), isNull())).thenReturn(successResult);

            mockMvc.perform(post("/v1/dashboard/pending")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.correct").value(true));

            verify(dashboardService).getPending(eq(1L), eq(15), isNull());
        }

        @Test
        @DisplayName("Should return error when service fails")
        void shouldReturnErrorOnServiceFailure() throws Exception {
            when(dashboardService.getPending(eq(1L), eq(15), isNull())).thenReturn(errorResult);

            mockMvc.perform(post("/v1/dashboard/pending")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.correct").value(false));
        }

        @Test
        @DisplayName("funciona SIN userId en el cuerpo: la identidad la pone el token")
        void funcionaSinUserIdEnElCuerpo() throws Exception {
            /* Estas pruebas exigian un 400 si faltaba el userId. Ese contrato
               desaparecio con el arreglo de seguridad: el userId ya no se pide
               al cliente —lo ponia el cliente, y por eso podia poner el de
               otro—, sale del token. Que una peticion sin userId funcione es
               ahora la prueba de que la identidad viene del sitio correcto. */
            DashboardRequestDTO dto = new DashboardRequestDTO();

            mockMvc.perform(post("/v1/dashboard/pending")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk());
        }
    }

}

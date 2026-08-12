package com.ohchurus.budget.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ohchurus.budget.security.JWTAuthorizationFilter;
import com.ohchurus.budget.security.SecParams;
import com.ohchurus.budget.service.impl.ExcelExportService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ExportController.class)
@AutoConfigureMockMvc(addFilters = false)
class ExportControllerTest {
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
    private ExcelExportService excelExportService;

    @MockBean
    private JWTAuthorizationFilter jwtAuthorizationFilter;

    @MockBean
    private SecParams secParams;

    @Test
    @DisplayName("POST /excel should stream the workbook bytes with attachment headers")
    void shouldExportExcel() throws Exception {
        byte[] fake = "xlsx-bytes".getBytes();
        when(excelExportService.exportPeriod(eq(1L), anyInt(), any())).thenReturn(fake);

        mockMvc.perform(post("/v1/export/excel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("userId", 1, "budgetStartDay", 1))))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("Presupuesto.xlsx")))
                .andExpect(content().bytes(fake));
    }

    /* Esta prueba defendia un 500 con el cuerpo VACIO: quien pulsaba
       "Descargar Excel" y fallaba la generacion no recibia ni un byte ni una
       explicacion, y el frontend —que solo sabe leer ResultDTO— se quedaba
       mudo. Ahora se exige que el fallo se pueda leer. */
    @Test
    @DisplayName("POST /excel: si falla la generacion devuelve un ResultDTO legible, no un 500 vacio")
    void shouldReturnErrorOnFailure() throws Exception {
        when(excelExportService.exportPeriod(eq(1L), anyInt(), any()))
                .thenThrow(new IOException("boom"));

        mockMvc.perform(post("/v1/export/excel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("userId", 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correct").value(false))
                .andExpect(jsonPath("$.errorCode").value(500))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("Excel")));
    }

    @Test
    @DisplayName("exporta SIN userId en el cuerpo: la identidad la pone el token")
    void funcionaSinUserIdEnElCuerpo() throws Exception {
        /* Antes se exigia un 400 si faltaba el userId. Pedirselo al cliente era
           justamente el fallo: podia mandar el de otra persona y descargarse su
           Excel. Ahora sale del token y la peticion sin userId es valida. */
        mockMvc.perform(post("/v1/export/excel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("budgetStartDay", 1))))
                .andExpect(status().isOk());
    }
}

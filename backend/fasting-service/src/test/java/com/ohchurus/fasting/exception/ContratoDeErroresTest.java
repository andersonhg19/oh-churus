package com.ohchurus.fasting.exception;

import com.ohchurus.fasting.controller.FastingController;
import com.ohchurus.fasting.security.JWTAuthorizationFilter;
import com.ohchurus.fasting.security.SecParams;
import com.ohchurus.fasting.service.impl.FastingServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El contrato de errores en fasting-service: pase lo que pase, HTTP 200 con un
 * ResultDTO legible.
 *
 * Este era el servicio mas expuesto: TODOS sus cuerpos eran Map y se leian a
 * mano con Long.valueOf(body.get("userId").toString()). Un cuerpo vacio
 * reventaba con un NullPointerException y el ayuno "no arrancaba" sin decir
 * por que.
 */
@WebMvcTest(FastingController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("Contrato de errores: siempre 200 con ResultDTO")
class ContratoDeErroresTest {

    private static final String TEXTO_QUE_NO_CABE = "x".repeat(300);

    @Autowired private MockMvc mvc;
    /* La identidad ya no llega en el cuerpo: sale del token. Estas pruebas son
       unitarias / con los filtros apagados, asi que no hay token; se planta a
       mano para poder seguir probando la LOGICA. Que un extrano NO pueda es lo
       que comprueba AislamientoEnAyunoTest, con la aplicacion levantada. */
    @org.junit.jupiter.api.BeforeEach
    void plantarIdentidad() {
        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                "usuario@ohchurus.com", null, java.util.Collections.emptyList());
        auth.setDetails(3L);
        org.springframework.security.core.context.SecurityContextHolder.getContext()
                .setAuthentication(auth);
    }

    @org.junit.jupiter.api.AfterEach
    void limpiarIdentidad() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }


    @MockBean private FastingServiceImpl fastingService;
    @MockBean private JWTAuthorizationFilter jwtAuthorizationFilter;
    @MockBean private SecParams secParams;

    private void exigeContrato(String ruta, String cuerpo, String pistaEsperada) throws Exception {
        mvc.perform(post(ruta).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correct").value(false))
                .andExpect(jsonPath("$.errorCode").value(400))
                .andExpect(jsonPath("$.message").value(containsString(pistaEsperada)));
    }

    @Test
    @DisplayName("cuerpo vacio")
    void cuerpoVacio() throws Exception {
        exigeContrato("/v1/fasting/session/start", "", "vacio");
    }

    @Test
    @DisplayName("cuerpo sin el userId obligatorio")
    void sinUserId() throws Exception {
        exigeContrato("/v1/fasting/session/start", "{}", "'userId'");
    }

    @Test
    @DisplayName("campo con tipo incorrecto")
    void tipoIncorrecto() throws Exception {
        exigeContrato("/v1/fasting/water/add", "{\"userId\":3,\"glasses\":\"muchos\"}", "'glasses'");
    }

    @Test
    @DisplayName("una hora que no es una hora")
    void fechaQueNoEsFecha() throws Exception {
        exigeContrato("/v1/fasting/session/stop", "{\"userId\":3,\"endTime\":\"ayer\"}", "'endTime'");
    }

    @Test
    @DisplayName("un plan que no existe dice cual es el campo malo")
    void planDesconocido() throws Exception {
        exigeContrato("/v1/fasting/plan/save", "{\"userId\":3,\"planType\":\"PLAN_99_1\"}", "'planType'");
    }

    @Test
    @DisplayName("texto demasiado largo")
    void textoDemasiadoLargo() throws Exception {
        exigeContrato("/v1/fasting/plan/save",
                "{\"userId\":3,\"planType\":\"PLAN_16_8\",\"suggestedStartTime\":\""
                        + TEXTO_QUE_NO_CABE + "\"}", "'suggestedStartTime'");
    }

    @Test
    @DisplayName("una excepcion no capturada tampoco rompe el contrato")
    void excepcionNoCapturada() throws Exception {
        when(fastingService.getPlanConfig(anyLong())).thenThrow(new IllegalStateException("boom"));

        mvc.perform(post("/v1/fasting/plan/get")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correct").value(false))
                .andExpect(jsonPath("$.errorCode").value(500))
                .andExpect(jsonPath("$.message").value(containsString("error inesperado")));
    }
}

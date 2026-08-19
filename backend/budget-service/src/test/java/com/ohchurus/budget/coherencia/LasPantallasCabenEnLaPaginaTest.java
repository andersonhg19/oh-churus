package com.ohchurus.budget.coherencia;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * ============================================================================
 * LO QUE PIDE LA PANTALLA CABE EN LA PAGINA
 * ============================================================================
 *
 * ESTA PRUEBA NACIO DE UN FALLO VIVO QUE LLEVABA MESES.
 *
 * `SummaryScreen` pedia 200 movimientos y `MovementFilterDTO` tenia
 * `@Max(100)`. El resultado no era un error visible: el validador rechazaba la
 * peticion, el @RestControllerAdvice la convertia en un `correct: false`, la
 * pantalla comprobaba `if (res.correct)` y simplemente no entraba. Las barras
 * de "presupuesto vs real" del resumen **no funcionaron nunca**, y no habia
 * forma de notarlo: la dona y los totales de al lado si funcionan, asi que la
 * pantalla parece sana.
 *
 * Y volvio a pasar el mismo dia que se encontro: la pantalla de importacion,
 * recien escrita, pedia 200 categorias contra un `@Max(100)` de
 * `CategoryFilterDTO`. Los chips para elegir categoria no habrian cargado
 * nunca, dejando el importador inutil en la practica.
 *
 * Que se repita dos veces el mismo dia dice que el limite no estaba escrito
 * donde alguien lo fuera a leer. Por eso ahora hay una prueba que ATA los dos
 * lados: si alguien vuelve a bajar el tope, esto se pone rojo con el nombre de
 * la pantalla que se romperia.
 */
@SpringBootTest
@DisplayName("El tope de pagina aguanta lo que las pantallas piden de verdad")
class LasPantallasCabenEnLaPaginaTest {

    private static final Long ANA = 1L;

    /**
     * El tamano mas grande que pide cualquier pantalla hoy.
     *
     * Si manana una pide mas, que sea esta constante la que cambie y no el
     * @Max de tres DTOs distintos.
     */
    private static final int LO_QUE_MAS_PIDE_UNA_PANTALLA = 200;

    @Autowired private WebApplicationContext contexto;
    @Value("${secret}") private String secreto;

    private final ObjectMapper json = new ObjectMapper();
    private MockMvc mvc;

    @BeforeEach
    void prepararEscenario() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto).apply(springSecurity()).build();
    }

    private String token() {
        return "Bearer " + JWT.create().withSubject("ana@ohchurus.com")
                .withClaim("userId", ANA)
                .withExpiresAt(new Date(System.currentTimeMillis() + 3_600_000))
                .sign(Algorithm.HMAC256(secreto));
    }

    private JsonNode pedirPagina(String ruta) throws Exception {
        String r = mvc.perform(post(ruta).header("Authorization", token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"page\":0,\"size\":" + LO_QUE_MAS_PIDE_UNA_PANTALLA + "}"))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(r);
    }

    // ========================================================================

    @Test
    @DisplayName("los movimientos: es lo que pide el resumen para las barras")
    void losMovimientos() throws Exception {
        assertThat(pedirPagina("/v1/movements/all").path("correct").asBoolean())
                .as("SummaryScreen pide 200 para las barras de presupuesto vs real. Con el "
                        + "tope por debajo, la peticion se rechaza, la pantalla no entra en el "
                        + "if y las barras salen vacias SIEMPRE, sin ningun error a la vista")
                .isTrue();
    }

    @Test
    @DisplayName("las categorias: es lo que pide el importador para sus chips")
    void lasCategorias() throws Exception {
        assertThat(pedirPagina("/v1/categories/all").path("correct").asBoolean())
                .as("ImportScreen pide 200 categorias para poder elegir una por fila. Sin "
                        + "ellas no se puede clasificar nada y el importador queda inutil")
                .isTrue();
    }

    @Test
    @DisplayName("los programados: mismo tope, para que no vuelva a descuadrarse uno solo")
    void losProgramados() throws Exception {
        /* Hoy ninguna pantalla pide 200 programados, pero el tope se sube a la
           vez en los tres para que no queden tres numeros distintos que hay que
           recordar por separado. Eso es justamente lo que hizo que el fallo
           apareciera dos veces. */
        assertThat(pedirPagina("/v1/scheduled/all").path("correct").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("pero sigue habiendo un tope: una pagina sin limite es un problema esperando")
    void elTopeSigueExistiendo() throws Exception {
        String r = mvc.perform(post("/v1/movements/all").header("Authorization", token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"page\":0,\"size\":100000}"))
                .andReturn().getResponse().getContentAsString();

        assertThat(json.readTree(r).path("correct").asBoolean())
                .as("subir el tope no puede significar quitarlo: una peticion sin limite se "
                        + "trae la tabla entera a memoria")
                .isFalse();
    }
}

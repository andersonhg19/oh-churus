package com.ohchurus.fasting.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.ohchurus.fasting.entity.FastingSession;
import com.ohchurus.fasting.entity.WaterLog;
import com.ohchurus.fasting.enums.SessionStatus;
import com.ohchurus.fasting.repository.AchievementRepository;
import com.ohchurus.fasting.repository.PlanConfigRepository;
import com.ohchurus.fasting.repository.SessionRepository;
import com.ohchurus.fasting.repository.WaterLogRepository;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * ============================================================================
 * AISLAMIENTO EN AYUNO
 * ============================================================================
 *
 * El gemelo de AislamientoEntreUsuariosTest, para el otro servicio.
 *
 * Existe porque durante un tiempo la frase "la identidad sale del JWT" fue
 * verdad a medias: budget-service ya estaba arreglado y fasting-service seguia
 * leyendo el userId del cuerpo en sus trece endpoints. Media verdad en
 * seguridad es una mentira, y ninguna prueba lo cubria porque la matriz de
 * aislamiento solo miraba budget.
 *
 * Ana ayuna. Bruno, con un token perfectamente valido, intenta ver y tocar el
 * ayuno de Ana. No debe conseguir ni lo uno ni lo otro.
 */
@SpringBootTest
@DisplayName("Aislamiento en ayuno: Bruno no ve ni toca el ayuno de Ana")
class AislamientoEnAyunoTest {

    private static final Long ANA = 1L;
    private static final Long BRUNO = 2L;

    @Autowired private WebApplicationContext contexto;
    @Autowired private SessionRepository sesiones;
    @Autowired private WaterLogRepository aguas;
    @Autowired private PlanConfigRepository planes;
    @Autowired private AchievementRepository logros;
    @Value("${secret}") private String secreto;

    private MockMvc mvc;
    private Long sesionDeAna;

    @BeforeEach
    void prepararEscenario() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto).apply(springSecurity()).build();
        sesiones.deleteAll();
        aguas.deleteAll();
        planes.deleteAll();
        logros.deleteAll();

        LocalDateTime inicio = LocalDateTime.now().minusHours(3);
        sesionDeAna = sesiones.save(FastingSession.builder()
                .userId(ANA).startTime(inicio).targetEndTime(inicio.plusHours(16))
                .fastingHours(16).status(SessionStatus.IN_PROGRESS).active(true).build()).getId();

        aguas.save(WaterLog.builder()
                .userId(ANA).logDate(LocalDate.now()).glasses(7).goalGlasses(8).build());
    }

    private String tokenDeBruno() {
        return "Bearer " + JWT.create().withSubject("bruno@ohchurus.com")
                .withClaim("userId", BRUNO)
                .withExpiresAt(new Date(System.currentTimeMillis() + 3_600_000))
                .sign(Algorithm.HMAC256(secreto));
    }

    private String atacar(String ruta, String cuerpo) throws Exception {
        return mvc.perform(post(ruta).header("Authorization", tokenDeBruno())
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andReturn().getResponse().getContentAsString();
    }

    // ========================================================================

    @Test
    @DisplayName("no puede ver el ayuno en curso de Ana pidiendolo con su userId")
    void sesionActiva() throws Exception {
        String r = atacar("/v1/fasting/session/active", "{\"userId\":" + ANA + "}");
        assertThat(r)
                .as("Bruno recibio la sesion de Ana: " + r)
                .doesNotContain("\"id\":" + sesionDeAna);
    }

    @Test
    @DisplayName("no puede cancelar el ayuno de Ana")
    void cancelar() throws Exception {
        atacar("/v1/fasting/session/cancel", "{\"userId\":" + ANA + "}");
        assertThat(sesiones.findById(sesionDeAna))
                .as("le cancelaron el ayuno a Ana")
                .get().extracting(FastingSession::getStatus).isEqualTo(SessionStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("no puede cerrar el ayuno de Ana antes de tiempo")
    void detener() throws Exception {
        atacar("/v1/fasting/session/stop", "{\"userId\":" + ANA + "}");
        assertThat(sesiones.findById(sesionDeAna))
                .get().extracting(FastingSession::getActualEndTime).isNull();
    }

    @Test
    @DisplayName("no puede editar el ayuno de Ana ni sabiendo su id")
    void editar() throws Exception {
        LocalDateTime original = sesiones.findById(sesionDeAna).orElseThrow().getStartTime();
        atacar("/v1/fasting/session/edit",
                "{\"sessionId\":" + sesionDeAna + ",\"startTime\":\""
                        + LocalDateTime.now().minusHours(20) + "\"}");
        assertThat(sesiones.findById(sesionDeAna).orElseThrow().getStartTime())
                .as("le movieron la hora de inicio del ayuno a Ana")
                .isEqualTo(original);
    }

    @Test
    @DisplayName("no puede ver cuanta agua bebio Ana")
    void agua() throws Exception {
        String r = atacar("/v1/fasting/water/today", "{\"userId\":" + ANA + "}");
        assertThat(r).doesNotContain("\"glasses\":7");
    }

    @Test
    @DisplayName("no puede apuntarle vasos de agua a Ana")
    void anadirAgua() throws Exception {
        atacar("/v1/fasting/water/add", "{\"userId\":" + ANA + ",\"glasses\":5}");
        assertThat(aguas.findByUserIdAndLogDate(ANA, LocalDate.now()))
                .get().extracting(WaterLog::getGlasses)
                .as("le sumaron agua al registro de Ana")
                .isEqualTo(7);
    }

    @Test
    @DisplayName("no puede leer el historial ni el resumen de Ana")
    void historial() throws Exception {
        String h = atacar("/v1/fasting/history/by-period",
                "{\"userId\":" + ANA + ",\"budgetStartDay\":1}");
        assertThat(h).doesNotContain("\"id\":" + sesionDeAna);
    }
}

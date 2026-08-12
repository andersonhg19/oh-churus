package com.ohchurus.fasting.consultas;

import com.ohchurus.fasting.entity.Achievement;
import com.ohchurus.fasting.entity.FastingPlanConfig;
import com.ohchurus.fasting.entity.FastingSession;
import com.ohchurus.fasting.entity.WaterLog;
import com.ohchurus.fasting.enums.PlanType;
import com.ohchurus.fasting.enums.SessionStatus;
import com.ohchurus.fasting.repository.AchievementRepository;
import com.ohchurus.fasting.repository.PlanConfigRepository;
import com.ohchurus.fasting.repository.SessionRepository;
import com.ohchurus.fasting.repository.WaterLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ============================================================================
 * EL ESQUEMA DE AYUNO, CONTRA EL POSTGRESQL QUE SE DESPLIEGA
 * ============================================================================
 *
 * fasting-service no tiene ni una sola @Query: todas sus consultas son
 * derivadas del nombre del metodo. Aun asi es el servicio con MAS distancia
 * entre lo que se prueba y lo que se despliega, y por dos motivos a la vez:
 *
 *   · Su perfil de pruebas apaga Flyway y monta el esquema con create-drop, o
 *     sea, con el DDL que Hibernate se inventa. Las migraciones de verdad solo
 *     las ejecuta ElEsquemaCuadraConElCodigoTest... contra H2.
 *   · Sus dos migraciones no tienen gemelo por motor, asi que la unica prueba
 *     que las corria comprobaba una traduccion aproximada de su sintaxis.
 *
 * Resultado: las migraciones de ayuno nunca se habian ejecutado contra el
 * motor en el que corren en produccion. Aqui se ejecutan.
 *
 * Que esta clase levante el contexto ya es media prueba: el esquema lo pone
 * Flyway sobre PostgreSQL y ddl-auto=validate lo compara columna a columna
 * contra el mapeo. La otra media son los CHECK de la V2 y las consultas
 * derivadas, que tambien se traducen a SQL y tambien pueden salir distintas.
 */
@DisplayName("El esquema y las consultas de ayuno funcionan en PostgreSQL")
class ElEsquemaDeAyunoCuadraEnPostgresTest extends PostgresDeVerdad {

    private static final Long ANA = 1L;
    private static final Long BETO = 2L;

    private static final LocalDateTime ANOCHE = LocalDateTime.of(2026, 3, 10, 20, 0);

    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlanConfigRepository planes;
    @Autowired private SessionRepository sesiones;
    @Autowired private WaterLogRepository agua;
    @Autowired private AchievementRepository logros;

    @BeforeEach
    void vaciar() {
        sesiones.deleteAll();
        planes.deleteAll();
        agua.deleteAll();
        logros.deleteAll();
    }

    private FastingPlanConfig plan(Long duena, int ayuno, int comida, boolean vivo) {
        return FastingPlanConfig.builder()
                .userId(duena).planType(PlanType.CUSTOM)
                .fastingHours(ayuno).eatingHours(comida)
                .remindersEnabled(false).active(vivo).build();
    }

    private FastingSession sesion(Long duena, LocalDateTime inicio, SessionStatus estado, boolean viva) {
        return FastingSession.builder()
                .userId(duena).startTime(inicio).targetEndTime(inicio.plusHours(16))
                .fastingHours(16).status(estado).active(viva).build();
    }

    // ========================================================================

    @Test
    @DisplayName("las migraciones de ayuno se aplicaron sobre PostgreSQL, no sobre H2")
    void lasMigracionesCorrenEnPostgres() {
        Integer ultima = jdbc.queryForObject(
                "SELECT MAX(CAST(version AS INT)) FROM flyway_schema_history WHERE success = TRUE",
                Integer.class);

        assertThat(ultima)
                .as("sin la V2 la base de ayuno vuelve a no tener ni un CHECK, y esta es la "
                        + "unica prueba del proyecto que la ejecuta contra el motor real")
                .isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("un plan con horas imposibles lo rechaza PostgreSQL, no solo H2")
    void elPlanImposibleSeRechaza() {
        /* -5 y 29 suman 24, asi que la validacion de Java los deja pasar con
           planType CUSTOM. De ahi salen sesiones que terminan antes de empezar. */
        assertThatThrownBy(() -> planes.saveAndFlush(plan(ANA, -5, 29, true)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("una sesion que termina antes de empezar la rechaza PostgreSQL")
    void laSesionAlRevesSeRechaza() {
        FastingSession alReves = sesion(ANA, ANOCHE, SessionStatus.IN_PROGRESS, true);
        alReves.setTargetEndTime(ANOCHE.minusHours(1));

        assertThatThrownBy(() -> sesiones.saveAndFlush(alReves))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("una meta de cero vasos la rechaza PostgreSQL: dividir por ella revienta el resumen")
    void laMetaDeCeroSeRechaza() {
        assertThatThrownBy(() -> agua.saveAndFlush(WaterLog.builder()
                .userId(ANA).logDate(LocalDate.of(2026, 3, 10)).glasses(3).goalGlasses(0).build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("la sesion en curso se encuentra por persona y estado, y solo la suya")
    void laSesionEnCursoEsLaDeCadaUno() {
        sesiones.saveAndFlush(sesion(ANA, ANOCHE, SessionStatus.IN_PROGRESS, true));
        sesiones.saveAndFlush(sesion(BETO, ANOCHE, SessionStatus.IN_PROGRESS, true));

        assertThat(sesiones.findByUserIdAndStatusAndActiveTrue(ANA, SessionStatus.IN_PROGRESS))
                .as("el enum viaja como texto con un CHECK detras: si el dialecto lo tradujera "
                        + "distinto, nadie encontraria su propio ayuno en curso")
                .isPresent()
                .get().extracting(FastingSession::getUserId).isEqualTo(ANA);
    }

    @Test
    @DisplayName("el historial por fechas trae solo las sesiones del tramo, de la mas vieja a la mas nueva")
    void elHistorialVieneOrdenado() {
        sesiones.saveAndFlush(sesion(ANA, ANOCHE.minusDays(5), SessionStatus.COMPLETED, true));
        sesiones.saveAndFlush(sesion(ANA, ANOCHE.minusDays(1), SessionStatus.COMPLETED, true));
        sesiones.saveAndFlush(sesion(ANA, ANOCHE.minusDays(40), SessionStatus.COMPLETED, true));
        sesiones.saveAndFlush(sesion(ANA, ANOCHE.minusDays(2), SessionStatus.CANCELLED, false));

        List<FastingSession> tramo = sesiones
                .findByUserIdAndStartTimeBetweenAndActiveTrueOrderByStartTimeAsc(
                        ANA, ANOCHE.minusDays(7), ANOCHE);

        assertThat(tramo).extracting(FastingSession::getStartTime)
                .as("la de hace 40 dias esta fuera del tramo y la cancelada esta borrada")
                .containsExactly(ANOCHE.minusDays(5), ANOCHE.minusDays(1));
    }

    @Test
    @DisplayName("el diario de agua se busca por persona y dia")
    void elDiarioDeAguaSeBuscaPorDia() {
        agua.saveAndFlush(WaterLog.builder().userId(ANA)
                .logDate(LocalDate.of(2026, 3, 10)).glasses(5).goalGlasses(8).build());
        agua.saveAndFlush(WaterLog.builder().userId(BETO)
                .logDate(LocalDate.of(2026, 3, 10)).glasses(2).goalGlasses(8).build());

        assertThat(agua.findByUserIdAndLogDate(ANA, LocalDate.of(2026, 3, 10)))
                .isPresent()
                .get().extracting(WaterLog::getGlasses).isEqualTo(5);
    }

    @Test
    @DisplayName("los logros salen del mas reciente al mas antiguo y no se mezclan entre personas")
    void losLogrosVienenDelMasRecienteYSonDeCadaUno() {
        logros.saveAndFlush(Achievement.builder()
                .userId(ANA).code("STREAK_7").name("Siete dias").build());
        logros.saveAndFlush(Achievement.builder()
                .userId(BETO).code("STREAK_30").name("Treinta dias").build());

        assertThat(logros.findByUserIdOrderByUnlockedAtDesc(ANA))
                .extracting(Achievement::getCode).containsExactly("STREAK_7");
        assertThat(logros.existsByUserIdAndCode(ANA, "STREAK_30"))
                .as("el logro de Beto no puede contar como de Ana")
                .isFalse();
    }
}

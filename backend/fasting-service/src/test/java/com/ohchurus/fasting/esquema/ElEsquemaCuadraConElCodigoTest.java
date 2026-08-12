package com.ohchurus.fasting.esquema;

import com.ohchurus.fasting.entity.FastingPlanConfig;
import com.ohchurus.fasting.entity.FastingSession;
import com.ohchurus.fasting.entity.WaterLog;
import com.ohchurus.fasting.enums.PlanType;
import com.ohchurus.fasting.enums.SessionStatus;
import com.ohchurus.fasting.repository.PlanConfigRepository;
import com.ohchurus.fasting.repository.SessionRepository;
import com.ohchurus.fasting.repository.WaterLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ============================================================================
 * EL ESQUEMA CUADRA CON EL CODIGO (Y NO SE FIA DE EL)
 * ============================================================================
 *
 * Que esta prueba levante el contexto ya es media prueba: el esquema lo
 * construye Flyway y ddl-auto=validate lo compara columna a columna contra el
 * mapeo. Con el "update" de antes, anadir un @Column(nullable=false) a una
 * tabla con datos fallaba como WARN y la aplicacion arrancaba igual con la
 * base desincronizada.
 *
 * La otra media son las reglas que el servicio olvidaba. La mas fea: con el
 * plan CUSTOM solo se comprobaba que ayuno + comida sumaran 24, asi que un
 * -5 y un 29 pasaban la validacion y dejaban un plan del que salen sesiones
 * que terminan antes de empezar.
 *
 * Es la unica prueba de fasting-service que toca base de datos, por eso trae
 * su propia configuracion de H2: el resto son unitarias o @WebMvcTest.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:fastingesquema;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "secret=test-secret-key",
        "expired-time=3600000"
})
@DisplayName("El esquema de ayuno valida contra el mapeo y sostiene sus invariantes")
class ElEsquemaCuadraConElCodigoTest {

    private static final Long ANA = 1L;

    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlanConfigRepository planes;
    @Autowired private SessionRepository sesiones;
    @Autowired private WaterLogRepository agua;

    @BeforeEach
    void vaciar() {
        sesiones.deleteAll();
        planes.deleteAll();
        agua.deleteAll();
    }

    private FastingPlanConfig plan(int horasDeAyuno, int horasDeComida) {
        return FastingPlanConfig.builder()
                .userId(ANA).planType(PlanType.CUSTOM)
                .fastingHours(horasDeAyuno).eatingHours(horasDeComida)
                .remindersEnabled(false).active(true)
                .build();
    }

    @Test
    @DisplayName("las migraciones se aplicaron: el esquema lo puso Flyway, no Hibernate")
    void lasMigracionesSeAplicaron() {
        Integer ultima = jdbc.queryForObject(
                // Flyway crea su tabla con el nombre entrecomillado en minuscula; sin
                // comillas H2 la busca en mayuscula y no la encuentra.
                "SELECT MAX(CAST(\"version\" AS INT)) FROM \"flyway_schema_history\" WHERE \"success\" = TRUE",
                Integer.class);
        /* "De la V2 en adelante", no "exactamente la V2": lo que se defiende es
           que las invariantes esten puestas. Clavar el numero exacto convertia
           cada migracion nueva en un fallo de esta prueba, y con validate
           anadir un campo YA obliga a escribir una migracion. */
        assertThat(ultima).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("un plan con horas negativas que suman 24 ya no pasa")
    void lasHorasDelPlanSonHorasDeVerdad() {
        assertThatThrownBy(() -> planes.saveAndFlush(plan(-5, 29)))
                .as("el servicio solo mira que sumen 24: sin esto queda un plan imposible")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("una sesion no puede terminar antes de empezar")
    void unAyunoNoTerminaAntesDeEmpezar() {
        LocalDateTime ahora = LocalDateTime.now();
        FastingSession alReves = FastingSession.builder()
                .userId(ANA).startTime(ahora).targetEndTime(ahora.plusHours(16))
                .actualEndTime(ahora.minusHours(3))
                .fastingHours(16).status(SessionStatus.INCOMPLETE).active(true)
                .build();

        assertThatThrownBy(() -> sesiones.saveAndFlush(alReves))
                .as("editSession acepta cualquier hora de fin, incluso anterior al inicio")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("una sesion no puede colgar de un plan que no existe")
    void noHaySesionesHuerfanas() {
        LocalDateTime ahora = LocalDateTime.now();
        FastingSession huerfana = FastingSession.builder()
                .userId(ANA).planConfigId(999_999L)
                .startTime(ahora).targetEndTime(ahora.plusHours(16))
                .fastingHours(16).status(SessionStatus.IN_PROGRESS).active(true)
                .build();

        assertThatThrownBy(() -> sesiones.saveAndFlush(huerfana))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("no se pueden beber vasos negativos")
    void losVasosNoSonNegativos() {
        WaterLog imposible = WaterLog.builder()
                .userId(ANA).logDate(LocalDate.now()).glasses(-3).goalGlasses(8)
                .build();

        assertThatThrownBy(() -> agua.saveAndFlush(imposible))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}

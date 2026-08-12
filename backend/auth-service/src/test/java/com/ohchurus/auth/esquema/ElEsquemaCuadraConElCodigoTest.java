package com.ohchurus.auth.esquema;

import com.ohchurus.auth.entity.User;
import com.ohchurus.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

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
 * base desincronizada; ahora, si alguien toca una entidad y se olvida de la
 * migracion, esto no arranca y se entera aqui, no en produccion.
 *
 * La otra media es que las reglas que solo vivian en Java ahora tambien estan
 * escritas en la base.
 */
@SpringBootTest
@DisplayName("El esquema de auth valida contra el mapeo y sostiene sus invariantes")
class ElEsquemaCuadraConElCodigoTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserRepository usuarios;

    @BeforeEach
    void vaciar() {
        usuarios.deleteAll();
    }

    private User usuario(String correo, int diaDeCorte) {
        return User.builder()
                .name("Prueba").email(correo).password("$2a$10$hashfalsoperoconpinta")
                .budgetStartDay(diaDeCorte).active(true)
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
    @DisplayName("dos cuentas con el mismo correo: la base dice que no")
    void elCorreoEsUnico() {
        usuarios.saveAndFlush(usuario("ana@ohchurus.com", 1));

        assertThatThrownBy(() -> usuarios.saveAndFlush(usuario("ana@ohchurus.com", 1)))
                .as("dos altas simultaneas con el mismo correo dejaban dos cuentas y el login "
                        + "no sabria a cual entrar")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("un dia de corte que no es un dia del mes: la base dice que no")
    void elDiaDeCorteEsUnDiaDelMes() {
        /* PeriodUtils calcula el periodo a partir de este numero. Un 45
           produce un periodo imposible y todas las cifras de esa persona
           salen mal sin que nada avise. */
        assertThatThrownBy(() -> usuarios.saveAndFlush(usuario("raro@ohchurus.com", 45)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}

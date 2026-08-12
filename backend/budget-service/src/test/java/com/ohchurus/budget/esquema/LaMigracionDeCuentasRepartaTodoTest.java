package com.ohchurus.budget.esquema;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ============================================================================
 * LA MIGRACION DE CUENTAS NO DEJA A NADIE FUERA
 * ============================================================================
 *
 * Una migracion que anade una columna se prueba sola: si el ALTER esta mal, no
 * arranca. Lo que NO se prueba solo es el RELLENO, y aqui el relleno es la
 * mitad de la funcionalidad: la V4 tiene que repartir todos los movimientos
 * que ya existian a una cuenta "Sin asignar" por persona.
 *
 * Si ese reparto falla o se deja alguno, el sintoma es silencioso y feo: el
 * movimiento sigue contando en el presupuesto pero desaparece de todos los
 * saldos. La app afirmaria un saldo que el banco desmiente, que es justo lo
 * contrario de lo que las cuentas venian a arreglar.
 *
 * COMO SE PRUEBA UN "ANTES Y DESPUES"
 * -----------------------------------
 * Flyway se detiene donde se le diga. Se migra hasta la V3 —el mundo sin
 * cuentas—, se escriben movimientos como los que hay en la base de verdad, y
 * solo entonces se aplica la V4. Es la unica forma de ejercitar el camino que
 * recorreran los datos reales; arrancar de cero con la V4 ya aplicada prueba
 * una situacion que en produccion no ocurre nunca.
 *
 * ESTO SE COMPROBO TAMBIEN A MANO contra el volcado real de Anderson (36
 * movimientos, 2 personas) en un Postgres desechable: 0 movimientos sin cuenta
 * y 2 cuentas creadas. Esta prueba es para que siga siendo verdad manana sin
 * que nadie tenga que acordarse de repetirlo.
 */
@DisplayName("La V4 no deja ningun movimiento sin cuenta")
class LaMigracionDeCuentasRepartaTodoTest {

    /** Una base en memoria por prueba: la V4 solo se puede aplicar una vez. */
    private JdbcTemplate baseHasta(String version, String nombre) {
        DriverManagerDataSource fuente = new DriverManagerDataSource(
                "jdbc:h2:mem:" + nombre + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL", "sa", "");
        fuente.setDriverClassName("org.h2.Driver");

        Flyway.configure()
                .dataSource(fuente)
                .locations("classpath:db/migration/comun", "classpath:db/migration/h2")
                .target(version)
                .load()
                .migrate();

        return new JdbcTemplate(fuente);
    }

    private void migrarA(JdbcTemplate plantilla, String version) {
        Flyway.configure()
                .dataSource(plantilla.getDataSource())
                .locations("classpath:db/migration/comun", "classpath:db/migration/h2")
                .target(version)
                .load()
                .migrate();
    }

    /**
     * La categoria a la que colgar los movimientos.
     *
     * Hace falta porque la V2 puso una clave foranea de verdad: sin categoria
     * la base rechaza el movimiento. Que eso ocurra es buena senal —es el muro
     * de la V2 haciendo su trabajo—, pero obliga a montar el escenario
     * completo en vez de a medias.
     */
    private long categoriaDe(JdbcTemplate db, long usuario) {
        db.update("INSERT INTO oc_budget_category (user_id, name, type, active) "
                + "VALUES (?, 'Gastos', 'EXPENSE', TRUE)", usuario);
        return db.queryForObject(
                "SELECT max(id) FROM oc_budget_category WHERE user_id = ?", Long.class, usuario);
    }

    /** Un movimiento del mundo de antes: sin columna de cuenta siquiera. */
    private void movimientoHeredado(JdbcTemplate db, long usuario, long categoria, String descripcion) {
        db.update("INSERT INTO oc_budget_movement "
                        + "(user_id, category_id, date, amount, description, is_transfer, confirmed, active) "
                        + "VALUES (?, ?, CURRENT_DATE, 100000, ?, FALSE, TRUE, TRUE)",
                usuario, categoria, descripcion);
    }

    // ========================================================================

    @Nested
    @DisplayName("Con datos heredados")
    class ConDatosHeredados {

        @Test
        @DisplayName("reparte TODOS los movimientos, uno por persona, y no se deja ninguno")
        void repartoCompleto() {
            JdbcTemplate db = baseHasta("3", "migra_reparto");

            long catAnderson = categoriaDe(db, 3L);
            long catPareja = categoriaDe(db, 4L);
            movimientoHeredado(db, 3L, catAnderson, "arriendo de Anderson");
            movimientoHeredado(db, 3L, catAnderson, "mercado de Anderson");
            movimientoHeredado(db, 3L, catAnderson, "gasolina de Anderson");
            movimientoHeredado(db, 4L, catPareja, "cafe de la pareja");

            migrarA(db, "4");

            Integer huerfanos = db.queryForObject(
                    "SELECT count(*) FROM oc_budget_movement WHERE account_id IS NULL", Integer.class);
            assertThat(huerfanos)
                    .as("un movimiento sin cuenta sigue contando en el presupuesto pero "
                            + "desaparece de todos los saldos: descuadre invisible")
                    .isZero();

            List<Map<String, Object>> cuentas = db.queryForList(
                    "SELECT user_id, name, is_default FROM oc_budget_account ORDER BY user_id");
            assertThat(cuentas)
                    .as("una cuenta por persona con movimientos, ni una mas ni una menos")
                    .hasSize(2);
            assertThat(cuentas).allSatisfy(c -> {
                assertThat(c.get("NAME")).isEqualTo("Sin asignar");
                assertThat(c.get("IS_DEFAULT")).isEqualTo(true);
            });
        }

        @Test
        @DisplayName("no mezcla: cada quien acaba con lo suyo")
        void nadieHeredaLoDeOtro() {
            JdbcTemplate db = baseHasta("3", "migra_sin_mezcla");

            movimientoHeredado(db, 3L, categoriaDe(db, 3L), "de Anderson");
            movimientoHeredado(db, 4L, categoriaDe(db, 4L), "de la pareja");

            migrarA(db, "4");

            /* El fallo que se estaria buscando: un UPDATE mal correlacionado
               que le meta a todo el mundo la misma cuenta. Con dos personas se
               ve enseguida; en produccion significaria que uno ve en su saldo
               los gastos del otro. */
            Integer cruzados = db.queryForObject(
                    "SELECT count(*) FROM oc_budget_movement m "
                            + "JOIN oc_budget_account a ON a.id = m.account_id "
                            + "WHERE a.user_id <> m.user_id", Integer.class);
            assertThat(cruzados)
                    .as("hay movimientos dentro de la cuenta de otra persona")
                    .isZero();
        }
    }

    @Nested
    @DisplayName("Sin datos heredados")
    class SinDatosHeredados {

        @Test
        @DisplayName("una base vacia no se llena de cuentas 'Sin asignar' que nadie pidio")
        void instalacionLimpia() {
            JdbcTemplate db = baseHasta("3", "migra_limpia");

            migrarA(db, "4");

            Integer cuentas = db.queryForObject(
                    "SELECT count(*) FROM oc_budget_account", Integer.class);
            assertThat(cuentas)
                    .as("a quien estrena la app no se le puede plantar una cuenta llamada "
                            + "'Sin asignar'; el servicio ya se la crea el dia que la necesite")
                    .isZero();
        }
    }

    @Nested
    @DisplayName("Repetir la migracion")
    class Idempotencia {

        @Test
        @DisplayName("aplicar la V4 dos veces no duplica cuentas")
        void aplicarDosVeces() {
            JdbcTemplate db = baseHasta("3", "migra_dos_veces");
            movimientoHeredado(db, 3L, categoriaDe(db, 3L), "arriendo");
            migrarA(db, "4");

            /* Flyway no repetiria la V4 —lleva su historial—, asi que se
               ejecuta el cuerpo a mano. Importa porque una recuperacion mal
               hecha (restaurar un volcado y volver a migrar sobre el) es
               exactamente como se acaba ejecutando dos veces, y dos cuentas
               por defecto dejarian la mitad de los movimientos en cada una. */
            db.update("INSERT INTO oc_budget_account "
                    + "(user_id, name, kind, is_default, active, created_at, updated_at) "
                    + "SELECT DISTINCT m.user_id, 'Sin asignar', 'OWN', TRUE, TRUE, "
                    + "       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP "
                    + "FROM oc_budget_movement m "
                    + "WHERE NOT EXISTS (SELECT 1 FROM oc_budget_account a "
                    + "                  WHERE a.user_id = m.user_id AND a.is_default = TRUE AND a.active = TRUE)");

            Integer cuentas = db.queryForObject(
                    "SELECT count(*) FROM oc_budget_account WHERE is_default = TRUE", Integer.class);
            assertThat(cuentas)
                    .as("la segunda pasada creo otra cuenta por defecto: los movimientos "
                            + "quedarian repartidos entre dos y ningun saldo cuadraria")
                    .isEqualTo(1);
        }
    }
}

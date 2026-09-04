package com.ohchurus.budget.seguridad;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ============================================================================
 * LA BASE DE DATOS FILTRA POR USUARIO, AUNQUE EL SQL NO LO HAGA
 * ============================================================================
 *
 * Esta es la prueba que justifica la ola B entera.
 *
 * La matriz de aislamiento comprueba que los ENDPOINTS no dejan pasar. Eso
 * vale mientras cada consulta nueva se acuerde de filtrar; la primera que se
 * olvide devuelve datos de todo el mundo y la matriz no lo vera, porque prueba
 * los endpoints que ya existen.
 *
 * Aqui NO hay endpoints ni servicios ni repositorios: se abre una conexion con
 * un rol normal y se escribe el SQL mas descarado posible —SELECT * FROM
 * movimientos, sin un solo WHERE— y se comprueba que PostgreSQL no entrega las
 * filas de otro. Si esto pasa en verde, una consulta olvidada deja de ser una
 * fuga.
 *
 * POR QUE UN ROL PROPIO Y NO EL DEL CONTENEDOR
 * --------------------------------------------
 * El usuario de Testcontainers es superusuario, y un superusuario se salta RLS
 * SIEMPRE, incluso con FORCE. Probar con el daria verde sin probar nada — que
 * es exactamente lo que pasa hoy en la aplicacion real, y por eso
 * ComprobacionDeRls lo avisa al arrancar.
 *
 * SI NO HAY DOCKER, SE SALTA. Igual que el resto de las pruebas de
 * Testcontainers de este proyecto.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("La base de datos filtra por usuario, aunque el SQL no lo haga")
class LaBaseDeDatosFiltraPorUsuarioTest {

    private static final String ROL = "oc_app_pruebas";
    private static final String CLAVE = "clave_de_pruebas";

    private static final long ANA = 1L;
    private static final long BETO = 2L;
    /** Ana y Beto comparten hogar; Carla no. */
    private static final long CARLA = 3L;
    private static final long HOGAR = 10L;

    static PostgreSQLContainer<?> postgres;

    @BeforeAll
    static void prepararBase() throws Exception {
        if (!DockerClientFactory.instance().isDockerAvailable()) return;

        postgres = new PostgreSQLContainer<>("postgres:14");
        postgres.start();

        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration/comun", "classpath:db/migration/postgresql")
                .baselineOnMigrate(true)
                .load()
                .migrate();

        try (Connection dueno = comoDueno(); Statement s = dueno.createStatement()) {
            /* Un rol NORMAL: sin superusuario y sin BYPASSRLS. Es la unica
               forma de que las politicas se apliquen de verdad. */
            s.execute("CREATE ROLE " + ROL + " LOGIN PASSWORD '" + CLAVE + "'");
            s.execute("GRANT USAGE ON SCHEMA public TO " + ROL);
            s.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO " + ROL);
            s.execute("GRANT USAGE ON ALL SEQUENCES IN SCHEMA public TO " + ROL);

            s.execute("INSERT INTO oc_budget_household (id, name, active, created_at) "
                    + "VALUES (" + HOGAR + ", 'Casa', true, now())");
            s.execute("INSERT INTO oc_budget_household_member (id, household_id, user_id, role, active, created_at) "
                    + "VALUES (1, " + HOGAR + ", " + ANA + ", 'OWNER', true, now()), "
                    + "       (2, " + HOGAR + ", " + BETO + ", 'MEMBER', true, now())");

            /* Tres categorias: una personal de Ana, una del hogar, y una
               personal de Carla, que no comparte nada con nadie. */
            s.execute("INSERT INTO oc_budget_category (id, user_id, name, household_id, type, active, created_at) "
                    + "VALUES (100, " + ANA + ", 'Personal de Ana', NULL, 'EXPENSE', true, now()), "
                    + "       (101, " + ANA + ", 'Compartida', " + HOGAR + ", 'EXPENSE', true, now()), "
                    + "       (102, " + CARLA + ", 'Personal de Carla', NULL, 'EXPENSE', true, now())");

            s.execute("INSERT INTO oc_budget_movement "
                    + "(id, user_id, category_id, date, amount, description, is_transfer, confirmed, active, created_at) "
                    + "VALUES (200, " + ANA + ", 100, '2026-03-01', 1000, 'Cafe de Ana', false, true, true, now()), "
                    + "       (201, " + BETO + ", 101, '2026-03-02', 2000, 'Mercado del hogar', false, true, true, now()), "
                    + "       (202, " + CARLA + ", 102, '2026-03-03', 3000, 'Secreto de Carla', false, true, true, now())");
        }
    }

    @AfterAll
    static void apagar() {
        if (postgres != null) postgres.stop();
    }

    private static Connection comoDueno() throws Exception {
        return DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    /** Una conexion con el rol normal, diciendo quien pregunta. */
    private static Connection comoLaApp(Long userId) throws Exception {
        Connection c = DriverManager.getConnection(postgres.getJdbcUrl(), ROL, CLAVE);
        if (userId != null) {
            try (Statement s = c.createStatement()) {
                s.execute("SELECT set_config('app.user_id', '" + userId + "', false)");
            }
        }
        return c;
    }

    /** El SQL mas descarado posible: sin un solo WHERE. */
    private static List<String> descripcionesQueVe(Connection c) throws Exception {
        List<String> vistas = new ArrayList<>();
        try (Statement s = c.createStatement();
             ResultSet r = s.executeQuery("SELECT description FROM oc_budget_movement")) {
            while (r.next()) vistas.add(r.getString(1));
        }
        return vistas;
    }

    // ========================================================================

    @Test
    @DisplayName("Ana ve lo suyo y lo del hogar, y NO lo de Carla")
    void anaVeLoSuyoYLoDelHogar() throws Exception {
        try (Connection c = comoLaApp(ANA)) {
            assertThat(descripcionesQueVe(c))
                    .containsExactlyInAnyOrder("Cafe de Ana", "Mercado del hogar");
        }
    }

    @Test
    @DisplayName("Beto ve lo del hogar aunque el movimiento sea de Ana")
    void betoVeLoDelHogar() throws Exception {
        /* Es la mitad que se olvida: una politica de "solo lo tuyo" seria mas
           simple y dejaria el consolidado del hogar a medias sin que nada
           fallara. */
        try (Connection c = comoLaApp(BETO)) {
            assertThat(descripcionesQueVe(c)).contains("Mercado del hogar");
            assertThat(descripcionesQueVe(c)).doesNotContain("Cafe de Ana", "Secreto de Carla");
        }
    }

    @Test
    @DisplayName("CARLA NO VE NADA DE LOS DEMAS, NI ESCRIBIENDO EL SQL A MANO")
    void carlaNoVeNadaAjeno() throws Exception {
        try (Connection c = comoLaApp(CARLA)) {
            assertThat(descripcionesQueVe(c)).containsExactly("Secreto de Carla");
        }
    }

    @Test
    @DisplayName("sin decir quien pregunta, no se ve NINGUNA fila")
    void sinIdentidadNoSeVeNada() throws Exception {
        /* El fallo seguro: si el aspecto que anuncia la identidad se rompiera,
           la aplicacion se quedaria sin datos —muy visible— en vez de
           ensenarselos todos a cualquiera. */
        try (Connection c = comoLaApp(null)) {
            assertThat(descripcionesQueVe(c)).isEmpty();
        }
    }

    @Test
    @DisplayName("un id inventado tampoco abre nada")
    void idInventado() throws Exception {
        try (Connection c = comoLaApp(9999L)) {
            assertThat(descripcionesQueVe(c)).isEmpty();
        }
    }

    @Test
    @DisplayName("tampoco se pueden BORRAR filas ajenas")
    void noBorraLoAjeno() throws Exception {
        try (Connection c = comoLaApp(CARLA); Statement s = c.createStatement()) {
            int borradas = s.executeUpdate("DELETE FROM oc_budget_movement WHERE id = 200");
            assertThat(borradas)
                    .as("Carla borro un movimiento de Ana")
                    .isZero();
        }
        try (Connection c = comoLaApp(ANA)) {
            assertThat(descripcionesQueVe(c)).contains("Cafe de Ana");
        }
    }

    @Test
    @DisplayName("ni EDITARLAS: un UPDATE sin WHERE solo toca lo propio")
    void noEditaLoAjeno() throws Exception {
        try (Connection c = comoLaApp(CARLA); Statement s = c.createStatement()) {
            int tocadas = s.executeUpdate("UPDATE oc_budget_movement SET amount = 0");
            assertThat(tocadas)
                    .as("un UPDATE sin WHERE alcanzo filas de otros")
                    .isEqualTo(1);
        }
        try (Connection dueno = comoDueno(); Statement s = dueno.createStatement();
             ResultSet r = s.executeQuery("SELECT amount FROM oc_budget_movement WHERE id = 200")) {
            r.next();
            assertThat(r.getBigDecimal(1).intValue())
                    .as("el movimiento de Ana cambio de importe")
                    .isEqualTo(1000);
        }
    }

    @Test
    @DisplayName("ni INSERTAR una fila a nombre de otro")
    void noInsertaANombreDeOtro() throws Exception {
        try (Connection c = comoLaApp(CARLA); Statement s = c.createStatement()) {
            /* El WITH CHECK de la politica es lo que impide esto. Sin el, se
               podrian sembrar movimientos en la cuenta de cualquiera. */
            org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () ->
                    s.execute("INSERT INTO oc_budget_movement "
                            + "(id, user_id, category_id, date, amount, description, is_transfer, confirmed, active, created_at) "
                            + "VALUES (300, " + ANA + ", 100, '2026-03-05', 500, 'Colado', false, true, true, now())"));
        }
    }

    @Test
    @DisplayName("las categorias tambien filtran: Carla no ve las del hogar de Ana")
    void categoriasTambienFiltran() throws Exception {
        try (Connection c = comoLaApp(CARLA); Statement s = c.createStatement();
             ResultSet r = s.executeQuery("SELECT name FROM oc_budget_category")) {
            List<String> vistas = new ArrayList<>();
            while (r.next()) vistas.add(r.getString(1));
            assertThat(vistas).containsExactly("Personal de Carla");
        }
    }

    @Test
    @DisplayName("y el hogar solo lo ven sus miembros")
    void elHogarSoloSusMiembros() throws Exception {
        try (Connection c = comoLaApp(CARLA); Statement s = c.createStatement();
             ResultSet r = s.executeQuery("SELECT count(*) FROM oc_budget_household")) {
            r.next();
            assertThat(r.getInt(1)).isZero();
        }
        try (Connection c = comoLaApp(ANA); Statement s = c.createStatement();
             ResultSet r = s.executeQuery("SELECT count(*) FROM oc_budget_household")) {
            r.next();
            assertThat(r.getInt(1)).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("EL SUPERUSUARIO SE LO SALTA TODO, y por eso hay un aviso al arrancar")
    void elSuperusuarioSeLoSalta() throws Exception {
        /* Esto no es un fallo: es como funciona PostgreSQL, y es la razon de
           que la aplicacion —que hoy conecta como superusuario— siga viendo
           todo. Se prueba para que quede escrito y nadie confunda "las
           politicas existen" con "la base protege". */
        try (Connection c = comoDueno()) {
            assertThat(descripcionesQueVe(c)).hasSize(3);
        }
    }
}

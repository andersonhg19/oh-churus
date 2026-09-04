package com.ohchurus.budget.seguridad;

import com.ohchurus.budget.security.ComprobacionDeRls;
import com.ohchurus.budget.security.ContextoDeUsuarioEnLaBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * El aspecto que le dice a PostgreSQL quien pregunta, y sus dos silencios
 * deliberados: en H2 no hace nada, y sin usuario no dice nada — que en las
 * politicas significa "no ves ni una fila".
 */
@DisplayName("La identidad se anuncia a la base de datos")
class ContextoDeUsuarioEnLaBaseTest {

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    private static DataSource fuenteQueDice(String producto) throws Exception {
        DataSource fuente = mock(DataSource.class);
        Connection conexion = mock(Connection.class);
        DatabaseMetaData metadatos = mock(DatabaseMetaData.class);
        when(fuente.getConnection()).thenReturn(conexion);
        when(conexion.getMetaData()).thenReturn(metadatos);
        when(metadatos.getDatabaseProductName()).thenReturn(producto);
        return fuente;
    }

    private static void seAutentica(Long userId) {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken("quien@sea", null, List.of());
        auth.setDetails(userId);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private static ProceedingJoinPoint puntoQueDevuelve(Object valor) throws Throwable {
        ProceedingJoinPoint punto = mock(ProceedingJoinPoint.class);
        when(punto.proceed()).thenReturn(valor);
        return punto;
    }

    @Nested
    @DisplayName("Cuando el motor es PostgreSQL")
    class EnPostgres {

        @Test
        @DisplayName("anuncia el usuario con set_config LOCAL a la transaccion")
        void anunciaElUsuario() throws Throwable {
            /* El tercer parametro en true es lo que hace que PostgreSQL lo
               deshaga al terminar la transaccion. Con un SET normal el valor
               se quedaria pegado a la conexion, la conexion volveria al pool,
               y la siguiente peticion —de otra persona— la reutilizaria con la
               identidad anterior puesta. */
            EntityManager em = mock(EntityManager.class);
            Query consulta = mock(Query.class);
            when(em.createNativeQuery(anyString())).thenReturn(consulta);
            when(consulta.setParameter(anyString(), any())).thenReturn(consulta);

            ContextoDeUsuarioEnLaBase aspecto =
                    new ContextoDeUsuarioEnLaBase(fuenteQueDice("PostgreSQL"), true);
            ReflectionTestUtils.setField(aspecto, "entityManager", em);
            seAutentica(42L);

            Object resultado = aspecto.anunciarQuienPregunta(puntoQueDevuelve("ok"));

            assertThat(resultado).isEqualTo("ok");
            verify(em).createNativeQuery(contains("set_config('app.user_id'"));
            verify(consulta).setParameter("valor", "42");
        }

        @Test
        @DisplayName("SIN USUARIO NO ANUNCIA NADA: es el fallo seguro")
        void sinUsuarioNoAnuncia() throws Throwable {
            /* Y no anunciar nada significa, en las politicas, no ver ninguna
               fila. Mas vale una lista vacia que los datos de otro. */
            EntityManager em = mock(EntityManager.class);
            ContextoDeUsuarioEnLaBase aspecto =
                    new ContextoDeUsuarioEnLaBase(fuenteQueDice("PostgreSQL"), true);
            ReflectionTestUtils.setField(aspecto, "entityManager", em);
            SecurityContextHolder.clearContext();

            aspecto.anunciarQuienPregunta(puntoQueDevuelve("ok"));

            verify(em, never()).createNativeQuery(anyString());
        }

        @Test
        @DisplayName("se puede apagar por configuracion")
        void sePuedeApagar() throws Exception {
            assertThat(new ContextoDeUsuarioEnLaBase(fuenteQueDice("PostgreSQL"), false).estaActivo())
                    .isFalse();
        }
    }

    @Nested
    @DisplayName("Cuando el motor no es PostgreSQL")
    class EnOtroMotor {

        @Test
        @DisplayName("en H2 no hace nada: set_config no existe ahi")
        void enH2NoHaceNada() throws Throwable {
            EntityManager em = mock(EntityManager.class);
            ContextoDeUsuarioEnLaBase aspecto =
                    new ContextoDeUsuarioEnLaBase(fuenteQueDice("H2"), true);
            ReflectionTestUtils.setField(aspecto, "entityManager", em);
            seAutentica(42L);

            assertThat(aspecto.estaActivo()).isFalse();
            aspecto.anunciarQuienPregunta(puntoQueDevuelve("ok"));

            verify(em, never()).createNativeQuery(anyString());
        }

        @Test
        @DisplayName("un motor que no dice su nombre tampoco cuenta como PostgreSQL")
        void motorSinNombre() throws Exception {
            /* getDatabaseProductName puede devolver null con algun driver; sin
               esta comprobacion seria un NullPointerException en el arranque. */
            assertThat(new ContextoDeUsuarioEnLaBase(fuenteQueDice(null), true).estaActivo()).isFalse();
        }

        @Test
        @DisplayName("si no se puede saber el motor, se queda quieto")
        void motorDesconocido() throws Exception {
            /* Mandar set_config a un motor que no lo entiende romperia TODAS
               las operaciones del servicio, que es mucho peor que dejar RLS
               sin anunciar. */
            DataSource rota = mock(DataSource.class);
            when(rota.getConnection()).thenThrow(new RuntimeException("sin conexion"));

            assertThat(new ContextoDeUsuarioEnLaBase(rota, true).estaActivo()).isFalse();
        }
    }

    @Nested
    @DisplayName("El aviso del arranque")
    class Aviso {

        private ComprobacionDeRls comprobacionCon(boolean superusuario, boolean bypass) throws Exception {
            DataSource fuente = mock(DataSource.class);
            Connection conexion = mock(Connection.class);
            DatabaseMetaData metadatos = mock(DatabaseMetaData.class);
            Statement sentencia = mock(Statement.class);
            ResultSet resultado = mock(ResultSet.class);

            when(fuente.getConnection()).thenReturn(conexion);
            when(conexion.getMetaData()).thenReturn(metadatos);
            when(metadatos.getDatabaseProductName()).thenReturn("PostgreSQL");
            when(conexion.createStatement()).thenReturn(sentencia);
            when(sentencia.executeQuery(anyString())).thenReturn(resultado);
            when(resultado.next()).thenReturn(true);
            when(resultado.getString(1)).thenReturn("postgres");
            when(resultado.getBoolean(2)).thenReturn(superusuario);
            when(resultado.getBoolean(3)).thenReturn(bypass);

            return new ComprobacionDeRls(fuente, true);
        }

        @Test
        @DisplayName("con un superusuario avisa de que las politicas estan dormidas")
        void avisaConSuperusuario() throws Exception {
            /* Sin este aviso queda la peor situacion posible de una medida de
               seguridad: creerla activa. */
            assertThatCode(() -> comprobacionCon(true, false).comprobar()).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("con un rol normal dice que esta activo")
        void conRolNormal() throws Exception {
            assertThatCode(() -> comprobacionCon(false, false).comprobar()).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("con BYPASSRLS tambien avisa")
        void conBypass() throws Exception {
            assertThatCode(() -> comprobacionCon(false, true).comprobar()).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("en H2 no dice nada")
        void enH2() throws Exception {
            ComprobacionDeRls comprobacion = new ComprobacionDeRls(fuenteQueDice("H2"), true);
            assertThatCode(comprobacion::comprobar).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("un motor que no dice su nombre tampoco se comprueba")
        void motorSinNombre() throws Exception {
            ComprobacionDeRls comprobacion = new ComprobacionDeRls(fuenteQueDice(null), true);
            assertThatCode(comprobacion::comprobar).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("si la consulta del rol no devuelve nada, no se inventa un diagnostico")
        void sinFilaDeRol() throws Exception {
            DataSource fuente = mock(DataSource.class);
            Connection conexion = mock(Connection.class);
            DatabaseMetaData metadatos = mock(DatabaseMetaData.class);
            Statement sentencia = mock(Statement.class);
            ResultSet resultado = mock(ResultSet.class);
            when(fuente.getConnection()).thenReturn(conexion);
            when(conexion.getMetaData()).thenReturn(metadatos);
            when(metadatos.getDatabaseProductName()).thenReturn("PostgreSQL");
            when(conexion.createStatement()).thenReturn(sentencia);
            when(sentencia.executeQuery(anyString())).thenReturn(resultado);
            when(resultado.next()).thenReturn(false);

            assertThatCode(() -> new ComprobacionDeRls(fuente, true).comprobar())
                    .doesNotThrowAnyException();
            verify(resultado, never()).getBoolean(anyInt());
        }

        @Test
        @DisplayName("apagado, ni lo intenta")
        void apagado() throws Exception {
            DataSource fuente = mock(DataSource.class);
            new ComprobacionDeRls(fuente, false).comprobar();
            verify(fuente, never()).getConnection();
        }

        @Test
        @DisplayName("y si la comprobacion falla, no impide arrancar")
        void siFallaNoImpideArrancar() throws Exception {
            DataSource rota = mock(DataSource.class);
            when(rota.getConnection()).thenThrow(new RuntimeException("sin conexion"));

            assertThatCode(() -> new ComprobacionDeRls(rota, true).comprobar())
                    .doesNotThrowAnyException();
        }
    }
}

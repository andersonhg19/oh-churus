package com.ohchurus.budget.security;

import com.ohchurus.budget.util.SecurityUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * ============================================================================
 * QUIEN PREGUNTA, DICHO EN LA PROPIA SESION DE POSTGRESQL
 * ============================================================================
 *
 * Las politicas de Row Level Security (migracion V9) deciden que filas existen
 * leyendo el ajuste app.user_id. Alguien tiene que ponerlo, y ese alguien es
 * esto.
 *
 * TRES DECISIONES
 *
 *  1. SE USA set_config(..., true), NO "SET". El tercer parametro en true
 *     significa LOCAL A LA TRANSACCION: al terminar —con commit o con
 *     rollback— PostgreSQL lo deshace solo. Esa es la razon de que aqui no
 *     haya ningun RESET que se pueda olvidar. Y es exactamente donde falla
 *     este patron cuando se hace mal: con un SET normal, el valor se queda
 *     pegado a la CONEXION, la conexion vuelve al pool, y la siguiente
 *     peticion —de otra persona— la reutiliza con la identidad anterior
 *     todavia puesta. Una fuga de datos entre usuarios, silenciosa.
 *
 *  2. VA DENTRO DE LA TRANSACCION, no antes. Por eso el aspecto tiene un orden
 *     mayor que el del interceptor transaccional (ver RlsConfig): un
 *     set_config local fuera de transaccion no dura mas que la sentencia.
 *
 *  3. SI NO HAY USUARIO, NO SE PONE NADA. Y no poner nada significa, en las
 *     politicas, que no se ve ninguna fila. Es el fallo seguro: mas vale una
 *     lista vacia que los datos de otro.
 *
 * En H2 —toda la suite de pruebas menos las de Testcontainers— no hace nada:
 * set_config no existe ahi y RLS tampoco. Se comprueba una sola vez al
 * arrancar y no en cada llamada.
 */
@Slf4j
@Aspect
@Component
@Order(200)
public class ContextoDeUsuarioEnLaBase {

    @PersistenceContext
    private EntityManager entityManager;

    private final boolean activo;

    public ContextoDeUsuarioEnLaBase(DataSource dataSource,
                                     @Value("${rls.enabled:true}") boolean habilitado) {
        this.activo = habilitado && esPostgres(dataSource);
        if (this.activo) {
            log.info("Row Level Security: la identidad se anunciara a PostgreSQL en cada transaccion");
        }
    }

    @Around("@within(org.springframework.transaction.annotation.Transactional) "
            + "&& within(com.ohchurus.budget.service.impl..*)")
    public Object anunciarQuienPregunta(ProceedingJoinPoint punto) throws Throwable {
        if (activo) {
            Long yo = SecurityUtils.getAuthenticatedUserId();
            if (yo != null) {
                entityManager
                        .createNativeQuery("SELECT set_config('app.user_id', :valor, true)")
                        .setParameter("valor", String.valueOf(yo))
                        .getSingleResult();
            }
        }
        return punto.proceed();
    }

    private static boolean esPostgres(DataSource dataSource) {
        try (Connection conexion = dataSource.getConnection()) {
            String producto = conexion.getMetaData().getDatabaseProductName();
            return producto != null && producto.toLowerCase().contains("postgres");
        } catch (Exception e) {
            /* Si no se puede saber, no se hace: mandar set_config a un motor
               que no lo entiende romperia TODAS las operaciones del servicio,
               que es mucho peor que dejar RLS sin anunciar. */
            log.warn("No pude averiguar el motor de base de datos; RLS no se anunciara: {}", e.getMessage());
            return false;
        }
    }

    /** Para que las pruebas puedan comprobar la decision sin arrancar Postgres. */
    public boolean estaActivo() {
        return activo;
    }

    static int ordenDelAspecto() {
        return 200;
    }

    static int ordenMasBajoPosible() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}

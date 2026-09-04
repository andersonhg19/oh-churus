package com.ohchurus.budget.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * ¿Row Level Security esta puesto, y esta protegiendo?
 *
 * No es lo mismo. Un SUPERUSUARIO se salta las politicas SIEMPRE, incluso con
 * FORCE ROW LEVEL SECURITY. Como la aplicacion conecta hoy como "postgres",
 * las politicas de la migracion V9 estan puestas y dormidas.
 *
 * Esto lo dice en voz alta al arrancar. Sin este aviso, lo que queda es la
 * peor situacion posible de una medida de seguridad: creerla activa. Alguien
 * lee la migracion, ve las politicas, y da por hecho que la base protege — y
 * la base no esta protegiendo nada.
 *
 * No falla ni impide arrancar: no es un error de configuracion, es el estado
 * previsto hasta que se cambien las credenciales. Solo tiene que ser visible.
 */
@Slf4j
@Component
public class ComprobacionDeRls {

    private final DataSource dataSource;
    private final boolean habilitado;

    public ComprobacionDeRls(DataSource dataSource, @Value("${rls.enabled:true}") boolean habilitado) {
        this.dataSource = dataSource;
        this.habilitado = habilitado;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void comprobar() {
        if (!habilitado) return;

        try (Connection conexion = dataSource.getConnection()) {
            String producto = conexion.getMetaData().getDatabaseProductName();
            if (producto == null || !producto.toLowerCase().contains("postgres")) {
                return;
            }

            try (Statement sentencia = conexion.createStatement();
                 ResultSet r = sentencia.executeQuery(
                         "SELECT current_user, rolsuper, rolbypassrls "
                                 + "FROM pg_roles WHERE rolname = current_user")) {

                if (!r.next()) return;
                String usuario = r.getString(1);
                boolean superusuario = r.getBoolean(2);
                boolean puedeSaltarse = r.getBoolean(3);

                if (superusuario || puedeSaltarse) {
                    log.warn("""
                            Row Level Security esta PUESTO pero NO PROTEGE.
                              La aplicacion conecta como '{}', que {} las politicas.
                              Las politicas de la V9 quedan dormidas hasta que la conexion use
                              un rol normal; el como esta escrito al final de esa migracion.
                              Mientras tanto el aislamiento lo sostiene solo el codigo, que es
                              lo que habia antes de la ola 4.""",
                            usuario, superusuario ? "es superusuario y se salta" : "puede saltarse");
                } else {
                    log.info("Row Level Security activo: la base de datos filtra por usuario ({})", usuario);
                }
            }
        } catch (Exception e) {
            /* Comprobar no puede impedir arrancar. */
            log.warn("No pude comprobar el estado de Row Level Security: {}", e.getMessage());
        }
    }
}

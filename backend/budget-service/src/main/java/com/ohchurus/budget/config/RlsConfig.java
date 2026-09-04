package com.ohchurus.budget.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * El orden entre la transaccion y el aspecto que anuncia la identidad.
 *
 * Por defecto el interceptor transaccional de Spring es el MAS INTERNO de
 * todos (orden mas bajo posible), asi que cualquier aspecto propio se ejecuta
 * FUERA de la transaccion. Para lo que hace ContextoDeUsuarioEnLaBase eso no
 * sirve: un set_config local fuera de transaccion no dura mas que la
 * sentencia, y las politicas de RLS verian app.user_id vacio.
 *
 * Poniendole aqui un orden explicito (100) se convierte en el mas externo de
 * los dos, y el aspecto (200) pasa a correr YA DENTRO de la transaccion, que
 * es donde tiene sentido.
 *
 * Es el unico sitio del proyecto que toca el orden de los interceptores, y por
 * eso esta escrito: cambiarlo sin saber esto dejaria RLS sin identidad y —con
 * un rol de aplicacion normal— la aplicacion entera sin ver ni una fila.
 */
@Configuration
@EnableAspectJAutoProxy
@EnableTransactionManagement(order = 100)
public class RlsConfig {
}

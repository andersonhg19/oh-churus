package com.ohchurus.budget.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ============================================================================
 * LA ESPECIFICACION OPENAPI DE budget-service
 * ============================================================================
 *
 * POR QUE SE GENERA Y NO SE ESCRIBE
 * ---------------------------------
 * La lista de endpoints vivia en el README y en una coleccion de Postman, y
 * las dos se quedaron atras: describir la API a mano es firmar que algun dia
 * va a mentir. Aqui la spec sale del codigo, y una prueba
 * (LaSpecPublicadaEstaAlDiaTest) compara lo generado con el JSON commiteado en
 * documentación/api. Cambiar un endpoint sin actualizar la spec rompe la
 * construccion.
 *
 * LA VERSION ES A MANO Y ESO ES LO CORRECTO
 * -----------------------------------------
 * No se coge de la del pom: la version de la API es un contrato con quien la
 * consume y cambia cuando cambia el contrato, no cuando se publica un parche.
 *
 * EL ESQUEMA bearerAuth
 * ---------------------
 * Aqui NO hay un solo endpoint publico: sin `Authorization: Bearer <jwt>` todo
 * responde 401. Declararlo es lo que hace que el boton "Authorize" de Swagger
 * UI sirva para algo.
 */
@Configuration
public class OpenApiConfig {

    /** El nombre tiene que ser el mismo en el esquema y en el requisito. */
    private static final String ESQUEMA_JWT = "bearerAuth";

    @Bean
    public OpenAPI openApiDePresupuesto() {
        return new OpenAPI()
                .info(new Info()
                        .title("Oh Churus! — budget-service")
                        .version("1.0.0")
                        .description("""
                                Categorias en arbol, movimientos, programados, presupuesto por
                                categoria y periodo, nucleo familiar compartido, panel y
                                exportacion a Excel.

                                Todos los endpoints son POST y devuelven HTTP 200 con un
                                ResultDTO: { correct, message, errorCode, object }. La unica
                                excepcion es /v1/export/excel, cuyo camino feliz son los bytes
                                del .xlsx y cuyo camino de error si es un ResultDTO en JSON.

                                Dos reglas del dominio que conviene saber antes de llamar:
                                el periodo NO es el mes natural (cada persona tiene su dia de
                                corte), y un movimiento hijo es detalle del padre mientras que
                                una transferencia no es ingreso ni gasto para nadie."""))
                .components(new Components().addSecuritySchemes(ESQUEMA_JWT,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("El token que devuelve /v1/auth/login de auth-service")))
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA_JWT));
    }
}

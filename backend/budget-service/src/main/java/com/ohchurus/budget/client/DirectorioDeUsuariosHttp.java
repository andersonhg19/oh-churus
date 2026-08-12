package com.ohchurus.budget.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Implementacion sobre HTTP: le pregunta a auth-service.
 *
 * POR QUE ASI Y NO DE OTRA FORMA
 * ------------------------------
 * Hoy los servicios de este proyecto NO se hablan entre si: no hay Feign, ni
 * RestTemplate, ni WebClient en ningun modulo, y cada uno tiene su propia base
 * de datos (auth_db / budget_db), asi que tampoco vale un JOIN.
 *
 * De las opciones posibles se eligio la menos invasiva:
 *   - No se toca auth-service. Se reutiliza su endpoint ya existente
 *     POST /v1/users/all, que ya filtra por correo.
 *   - No se anaden dependencias al pom: RestClient viene con
 *     spring-boot-starter-web, que budget-service ya usa.
 *   - No se inventa un secreto de servicio a servicio: se reenvia el MISMO
 *     token de quien invita. Si el que llama no esta autenticado, auth-service
 *     tampoco le contesta, y la consulta queda auditada a su nombre.
 *
 * El filtro de auth-service es un LIKE, asi que la comparacion exacta del
 * correo se hace aqui: buscar "ana@" no puede devolver el id de "ana@otro.com".
 */
@Slf4j
@Component
public class DirectorioDeUsuariosHttp implements DirectorioDeUsuarios {

    private static final String RUTA_USUARIOS = "/v1/users/all";

    private final RestClient cliente;

    public DirectorioDeUsuariosHttp(@Value("${app.auth-service-url:http://auth-service:8821/oh-churus}")
                                    String urlDeAuth) {
        /* Con timeouts cortos a proposito: si auth-service esta caido, el
           usuario tiene que ver "no se pudo resolver el correo" en un par de
           segundos, no una pantalla colgada. */
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofSeconds(2));
        fabrica.setReadTimeout(Duration.ofSeconds(3));
        this.cliente = RestClient.builder().baseUrl(urlDeAuth).requestFactory(fabrica).build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Long idPorCorreo(String correo) {
        if (correo == null || correo.isBlank()) return null;
        String buscado = correo.trim().toLowerCase();

        String autorizacion = autorizacionDeQuienLlama();
        if (autorizacion == null) {
            log.warn("No hay cabecera Authorization que reenviar a auth-service");
            return null;
        }

        try {
            Map<String, Object> respuesta = cliente.post()
                    .uri(RUTA_USUARIOS)
                    .header(HttpHeaders.AUTHORIZATION, autorizacion)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("email", buscado, "page", 0, "size", 20))
                    .retrieve()
                    .body(Map.class);

            if (respuesta == null || !(respuesta.get("object") instanceof Map)) return null;
            Object lista = ((Map<String, Object>) respuesta.get("object")).get("list");
            if (!(lista instanceof List)) return null;

            for (Object fila : (List<Object>) lista) {
                if (!(fila instanceof Map)) continue;
                Map<String, Object> usuario = (Map<String, Object>) fila;
                Object email = usuario.get("email");
                Object id = usuario.get("id");
                if (email != null && buscado.equalsIgnoreCase(email.toString()) && id instanceof Number) {
                    return ((Number) id).longValue();
                }
            }
            return null;
        } catch (Exception e) {
            log.error("No se pudo resolver el correo {} contra auth-service: {}", buscado, e.getMessage());
            return null;
        }
    }

    /** El token de la peticion en curso, para hablarle a auth-service como quien invita. */
    private String autorizacionDeQuienLlama() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes atributos)) {
            return null;
        }
        return atributos.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
    }
}

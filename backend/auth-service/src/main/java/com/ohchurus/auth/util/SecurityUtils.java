package com.ohchurus.auth.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * La identidad de quien hace la peticion, tomada del token y de ningun otro
 * sitio.
 *
 * Esta clase existia desde el principio pero NO la usaba nadie: el userId
 * llegaba en el cuerpo de cada peticion. Eso convertia la identidad en un
 * parametro mas —indistinguible de categoryId o de amount— y cualquiera podia
 * leer o modificar los datos de otro cambiando un numero.
 *
 * Regla del proyecto: ningun servicio vuelve a aceptar un userId de entrada.
 * Si necesitas saber quien pide algo, se pregunta aqui.
 */
public final class SecurityUtils {

    private SecurityUtils() {}

    public static String getAuthenticatedEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }

    /**
     * Id del usuario autenticado, o null si no hay token. Devolver null y no
     * lanzar es a proposito: quien llame decide si eso es un 401 o un flujo
     * publico, y asi esta clase no impone politica.
     */
    public static Long getAuthenticatedUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return null;
        Object detalles = auth.getDetails();
        return (detalles instanceof Long) ? (Long) detalles : null;
    }

    /**
     * ¿El recurso es de quien lo pide? Un null en el dueno se trata como "no",
     * nunca como "si": un dato sin dueno no es de todos.
     */
    public static boolean esDelUsuario(Long duenoDelRecurso) {
        Long yo = getAuthenticatedUserId();
        return yo != null && yo.equals(duenoDelRecurso);
    }
}

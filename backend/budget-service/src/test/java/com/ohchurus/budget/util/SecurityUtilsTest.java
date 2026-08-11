package com.ohchurus.budget.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SecurityUtils era codigo muerto: existia, tenia prueba, y no lo llamaba
 * nadie. Su prueba antigua incluso fijaba como contrato que estallara con un
 * NullPointerException cuando no habia sesion — es decir, describia un
 * accidente en vez de una decision.
 *
 * Ahora es la unica fuente de identidad del servicio, y su contrato es
 * explicito: sin token devuelve null, y "sin dueno" nunca significa "de todos".
 */
class SecurityUtilsTest {

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    private void autenticar(String email, Long userId) {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(email, null, Collections.emptyList());
        auth.setDetails(userId);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("devuelve el correo del token")
    void devuelveCorreo() {
        autenticar("test@ohchurus.com", 1L);
        assertEquals("test@ohchurus.com", SecurityUtils.getAuthenticatedEmail());
    }

    @Test
    @DisplayName("devuelve el id de usuario que viaja en el token")
    void devuelveUserId() {
        autenticar("test@ohchurus.com", 42L);
        assertEquals(42L, SecurityUtils.getAuthenticatedUserId());
    }

    @Test
    @DisplayName("sin sesion devuelve null, no revienta")
    void sinSesionDevuelveNull() {
        SecurityContextHolder.clearContext();
        assertNull(SecurityUtils.getAuthenticatedEmail());
        assertNull(SecurityUtils.getAuthenticatedUserId());
    }

    @Test
    @DisplayName("un token sin claim de id no suplanta a nadie")
    void tokenSinIdNoSuplanta() {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken("x@y.z", null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);
        assertNull(SecurityUtils.getAuthenticatedUserId());
        assertFalse(SecurityUtils.esDelUsuario(1L));
    }

    @Test
    @DisplayName("esDelUsuario: solo si coincide, y un recurso sin dueno no es de nadie")
    void esDelUsuario() {
        autenticar("a@b.c", 7L);
        assertTrue(SecurityUtils.esDelUsuario(7L));
        assertFalse(SecurityUtils.esDelUsuario(8L));
        assertFalse(SecurityUtils.esDelUsuario(null));
    }
}

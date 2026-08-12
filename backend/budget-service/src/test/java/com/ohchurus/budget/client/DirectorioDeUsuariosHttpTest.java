package com.ohchurus.budget.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("DirectorioDeUsuariosHttp")
class DirectorioDeUsuariosHttpTest {

    /* Se apunta a un puerto donde no hay nada: se comprueba que un directorio
       inalcanzable devuelve null en vez de tumbar la peticion. Ante la duda,
       no se mete a nadie en una casa ajena. */
    private final DirectorioDeUsuariosHttp directorio =
            new DirectorioDeUsuariosHttp("http://127.0.0.1:1/oh-churus");

    @AfterEach
    void limpiarContexto() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("Un correo vacio no se consulta siquiera")
    void correoVacio() {
        assertNull(directorio.idPorCorreo(null));
        assertNull(directorio.idPorCorreo("   "));
    }

    @Test
    @DisplayName("Sin token que reenviar no se resuelve nada")
    void sinToken() {
        MockHttpServletRequest peticion = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(peticion));

        assertNull(directorio.idPorCorreo("ana@ohchurus.com"));
    }

    @Test
    @DisplayName("Si auth-service no responde, devuelve null y no revienta")
    void directorioCaido() {
        MockHttpServletRequest peticion = new MockHttpServletRequest();
        peticion.addHeader("Authorization", "Bearer token-de-prueba");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(peticion));

        assertNull(directorio.idPorCorreo("ana@ohchurus.com"));
    }
}

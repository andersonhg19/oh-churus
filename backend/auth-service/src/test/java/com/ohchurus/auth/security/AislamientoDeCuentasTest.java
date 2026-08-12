package com.ohchurus.auth.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.ohchurus.auth.entity.User;
import com.ohchurus.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * ============================================================================
 * AISLAMIENTO DE CUENTAS
 * ============================================================================
 *
 * La tercera red de seguridad, para el servicio que faltaba.
 *
 * La ola de identidad arreglo budget y despues fasting, pero se salto
 * UserController: cualquier usuario autenticado podia leer la ficha de otro,
 * EDITARLE el correo y la contrasena, DARLE DE BAJA la cuenta con solo su id,
 * y listar el directorio entero de la plataforma con nombre y correo — con un
 * LIKE, ademas, asi que buscar "a" los sacaba a todos.
 *
 * El unico uso legitimo del listado es invitar al nucleo familiar por correo,
 * y ese caso exige conocer el correo de antemano. Por eso el filtro ahora es
 * de igualdad y sin correo solo te devuelve a ti.
 */
@SpringBootTest
@DisplayName("Aislamiento de cuentas: Bruno no toca la cuenta de Ana")
class AislamientoDeCuentasTest {

    @Autowired private WebApplicationContext contexto;
    @Autowired private UserRepository usuarios;
    @Autowired private PasswordEncoder cifrador;
    @Value("${secret}") private String secreto;

    private MockMvc mvc;
    private Long idDeAna;
    private Long idDeBruno;
    private String claveOriginalDeAna;

    @BeforeEach
    void prepararEscenario() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto).apply(springSecurity()).build();
        usuarios.deleteAll();

        User ana = usuarios.save(User.builder()
                .name("Ana").email("ana@ohchurus.com")
                .password(cifrador.encode("ClaveDeAna1")).budgetStartDay(1).active(true).build());
        idDeAna = ana.getId();
        claveOriginalDeAna = ana.getPassword();

        idDeBruno = usuarios.save(User.builder()
                .name("Bruno").email("bruno@ohchurus.com")
                .password(cifrador.encode("ClaveDeBruno1")).budgetStartDay(1).active(true).build())
                .getId();
    }

    private String tokenDeBruno() {
        return "Bearer " + JWT.create().withSubject("bruno@ohchurus.com")
                .withClaim("userId", idDeBruno)
                .withExpiresAt(new Date(System.currentTimeMillis() + 3_600_000))
                .sign(Algorithm.HMAC256(secreto));
    }

    private String atacar(String ruta, String cuerpo) throws Exception {
        return mvc.perform(post(ruta).header("Authorization", tokenDeBruno())
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo == null ? "{}" : cuerpo))
                .andReturn().getResponse().getContentAsString();
    }

    // ========================================================================

    @Test
    @DisplayName("no puede leer la ficha de Ana")
    void leerFicha() throws Exception {
        assertThat(atacar("/v1/users/get/" + idDeAna, null))
                .doesNotContain("ana@ohchurus.com");
    }

    @Test
    @DisplayName("no puede cambiarle el correo ni la contrasena a Ana")
    void secuestrarCuenta() throws Exception {
        atacar("/v1/users/save",
                "{\"id\":" + idDeAna + ",\"name\":\"Ana\",\"email\":\"bruno@evil.com\","
                        + "\"password\":\"NuevaClave1\"}");

        User ana = usuarios.findById(idDeAna).orElseThrow();
        assertThat(ana.getEmail())
                .as("le cambiaron el correo a Ana: pierde el acceso a su cuenta")
                .isEqualTo("ana@ohchurus.com");
        assertThat(ana.getPassword())
                .as("le cambiaron la contrasena a Ana")
                .isEqualTo(claveOriginalDeAna);
    }

    @Test
    @DisplayName("no puede dar de baja la cuenta de Ana")
    void borrarCuenta() throws Exception {
        atacar("/v1/users/delete/" + idDeAna, null);
        assertThat(usuarios.findById(idDeAna))
                .as("desactivaron la cuenta de Ana y se queda sin poder entrar")
                .get().extracting(User::getActive).isEqualTo(true);
    }

    @Test
    @DisplayName("el listado no es un directorio: sin correo solo se ve a si mismo")
    void listadoNoEsDirectorio() throws Exception {
        String r = atacar("/v1/users/all", "{\"page\":0,\"size\":50}");
        assertThat(r)
                .as("Bruno listo a los demas usuarios de la plataforma")
                .doesNotContain("ana@ohchurus.com");
        assertThat(r)
                .as("y deberia seguir viendose a si mismo")
                .contains("bruno@ohchurus.com");
    }

    @Test
    @DisplayName("no puede rastrear correos por coincidencia parcial")
    void nadaDeBusquedaParcial() throws Exception {
        /* Antes el filtro era un LIKE: con "a" salian todos. Ahora es
           igualdad, asi que hay que saber el correo entero — que es
           exactamente el permiso que da poder invitar a alguien. */
        assertThat(atacar("/v1/users/all", "{\"email\":\"ana\",\"page\":0,\"size\":50}"))
                .doesNotContain("ana@ohchurus.com");
    }

    @Test
    @DisplayName("pero SI puede resolver un correo exacto: la invitacion al hogar depende de ello")
    void invitacionPorCorreoSigueFuncionando() throws Exception {
        assertThat(atacar("/v1/users/all", "{\"email\":\"ana@ohchurus.com\",\"page\":0,\"size\":50}"))
                .as("sin esto, invitar a alguien al nucleo familiar deja de funcionar")
                .contains("ana@ohchurus.com");
    }

    @Test
    @DisplayName("con su propia cuenta si puede: leerla, editarla y darse de baja")
    void conLoSuyoSiPuede() throws Exception {
        assertThat(atacar("/v1/users/get/" + idDeBruno, null)).contains("bruno@ohchurus.com");

        atacar("/v1/users/save",
                "{\"id\":" + idDeBruno + ",\"name\":\"Bruno Nuevo\","
                        + "\"email\":\"bruno@ohchurus.com\",\"budgetStartDay\":15}");
        assertThat(usuarios.findById(idDeBruno).orElseThrow().getName()).isEqualTo("Bruno Nuevo");

        atacar("/v1/users/delete/" + idDeBruno, null);
        assertThat(usuarios.findById(idDeBruno).orElseThrow().getActive()).isFalse();
    }
}

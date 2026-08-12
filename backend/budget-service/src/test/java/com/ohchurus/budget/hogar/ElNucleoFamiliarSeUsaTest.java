package com.ohchurus.budget.hogar;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.ohchurus.budget.client.DirectorioDeUsuarios;
import com.ohchurus.budget.entity.BudgetAllocation;
import com.ohchurus.budget.entity.Category;
import com.ohchurus.budget.entity.Household;
import com.ohchurus.budget.entity.HouseholdMember;
import com.ohchurus.budget.enums.CategoryType;
import com.ohchurus.budget.repository.BudgetAllocationRepository;
import com.ohchurus.budget.repository.CategoryRepository;
import com.ohchurus.budget.repository.HouseholdMemberRepository;
import com.ohchurus.budget.repository.HouseholdRepository;
import com.ohchurus.budget.repository.MovementRepository;
import com.ohchurus.budget.repository.ScheduledMovementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * ============================================================================
 * EL NUCLEO FAMILIAR SE PUEDE USAR
 * ============================================================================
 *
 * El nucleo familiar es una de las tres patas del producto y desde la app no
 * se podia usar:
 *
 *   - Para invitar habia que escribir el id de fila de la base de datos.
 *     Nadie conoce su id, asi que nadie podia invitar a nadie.
 *   - Al sacar a un miembro solo se apagaba su fila: sus subcategorias
 *     personales seguian colgando de una categoria del hogar que ya no ve
 *     —desaparecian del arbol sin avisar— y sus asignaciones de presupuesto
 *     sobre categorias del hogar quedaban activas apuntando a algo invisible.
 *
 * Se prueba con la aplicacion entera levantada porque el fallo estaba
 * justamente en el cableado (endpoints, filtros, JPA), no en una funcion
 * aislada. Lo unico simulado es el directorio de usuarios: los usuarios viven
 * en auth_db, en otro servicio.
 */
@SpringBootTest
@DisplayName("El nucleo familiar se puede usar: invitar por correo y expulsar sin dejar restos")
class ElNucleoFamiliarSeUsaTest {

    private static final Long ANA = 1L;
    private static final Long BRUNO = 2L;
    private static final String CORREO_DE_BRUNO = "bruno@ohchurus.com";

    @Autowired private WebApplicationContext contexto;
    @Autowired private HouseholdRepository hogares;
    @Autowired private HouseholdMemberRepository miembros;
    @Autowired private CategoryRepository categorias;
    @Autowired private BudgetAllocationRepository asignaciones;
    @Autowired private MovementRepository movimientos;
    @Autowired private ScheduledMovementRepository programados;

    /* auth-service no se levanta en las pruebas: los usuarios estan en otra
       base de datos y en otro servicio. Se simula solo la traduccion
       correo -> id; todo lo demas es la aplicacion de verdad. */
    @MockBean private DirectorioDeUsuarios directorio;

    @Value("${secret}") private String secreto;

    private MockMvc mvc;
    private Long hogarDeAna;
    private Long categoriaDelHogar;

    @BeforeEach
    void prepararEscenario() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto).apply(springSecurity()).build();

        movimientos.deleteAll();
        asignaciones.deleteAll();
        programados.deleteAll();
        categorias.deleteAll();
        miembros.deleteAll();
        hogares.deleteAll();

        Household hogar = hogares.save(Household.builder().name("Casa de Ana").active(true).build());
        hogarDeAna = hogar.getId();
        miembros.save(HouseholdMember.builder()
                .householdId(hogarDeAna).userId(ANA).role("OWNER").active(true).build());

        categoriaDelHogar = categorias.save(Category.builder()
                .userId(ANA).name("Arriendo compartido").type(CategoryType.EXPENSE)
                .householdId(hogarDeAna).active(true).build()).getId();
    }

    private String tokenDe(Long userId, String email) {
        return "Bearer " + JWT.create()
                .withSubject(email)
                .withClaim("userId", userId)
                .withExpiresAt(new Date(System.currentTimeMillis() + 3_600_000))
                .sign(Algorithm.HMAC256(secreto));
    }

    private String comoAna(String ruta, String cuerpo) throws Exception {
        return llamar(ruta, cuerpo, tokenDe(ANA, "ana@ohchurus.com"));
    }

    private String comoBruno(String ruta, String cuerpo) throws Exception {
        return llamar(ruta, cuerpo, tokenDe(BRUNO, CORREO_DE_BRUNO));
    }

    private String llamar(String ruta, String cuerpo, String token) throws Exception {
        return mvc.perform(post(ruta)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo == null ? "{}" : cuerpo))
                .andReturn().getResponse().getContentAsString();
    }

    // ========================================================================
    @Nested
    @DisplayName("Invitar por correo")
    class InvitarPorCorreo {

        @Test
        @DisplayName("la duena invita escribiendo un correo, no un id de base de datos")
        void invitaPorCorreo() throws Exception {
            when(directorio.idPorCorreo(CORREO_DE_BRUNO)).thenReturn(BRUNO);

            String respuesta = comoAna("/v1/household/invite",
                    "{\"householdId\":" + hogarDeAna + ",\"email\":\"" + CORREO_DE_BRUNO + "\"}");

            assertThat(respuesta).contains("\"correct\":true");
            assertThat(miembros.existsByHouseholdIdAndUserIdAndActiveTrue(hogarDeAna, BRUNO))
                    .as("invitar por correo tenia que meter a Bruno en el hogar")
                    .isTrue();
        }

        @Test
        @DisplayName("un correo que no existe no mete a nadie")
        void correoDesconocido() throws Exception {
            when(directorio.idPorCorreo(anyString())).thenReturn(null);

            String respuesta = comoAna("/v1/household/invite",
                    "{\"householdId\":" + hogarDeAna + ",\"email\":\"fantasma@ohchurus.com\"}");

            assertThat(respuesta).contains("\"correct\":false");
            assertThat(miembros.findByHouseholdIdAndActiveTrue(hogarDeAna)).hasSize(1);
        }

        @Test
        @DisplayName("quien no es dueno no puede invitar, aunque sea miembro")
        void soloElDuenoInvita() throws Exception {
            miembros.save(HouseholdMember.builder()
                    .householdId(hogarDeAna).userId(BRUNO).role("MEMBER").active(true).build());
            when(directorio.idPorCorreo("carla@ohchurus.com")).thenReturn(3L);

            comoBruno("/v1/household/invite",
                    "{\"householdId\":" + hogarDeAna + ",\"email\":\"carla@ohchurus.com\"}");

            assertThat(miembros.existsByHouseholdIdAndUserIdAndActiveTrue(hogarDeAna, 3L))
                    .as("un miembro cualquiera metio gente en la casa")
                    .isFalse();
        }

        @Test
        @DisplayName("no se invita a un hogar que no existe")
        void hogarInexistente() throws Exception {
            when(directorio.idPorCorreo(CORREO_DE_BRUNO)).thenReturn(BRUNO);

            String respuesta = comoAna("/v1/household/invite",
                    "{\"householdId\":999999,\"email\":\"" + CORREO_DE_BRUNO + "\"}");

            assertThat(respuesta).contains("\"correct\":false");
            assertThat(miembros.existsByHouseholdIdAndUserIdAndActiveTrue(999999L, BRUNO)).isFalse();
        }
    }

    // ========================================================================
    @Nested
    @DisplayName("Expulsar sin dejar datos colgando")
    class Expulsar {

        private Long subcategoriaDeBruno;
        private Long asignacionDeBruno;

        @BeforeEach
        void brunoYaViviaEnLaCasa() {
            miembros.save(HouseholdMember.builder()
                    .householdId(hogarDeAna).userId(BRUNO).role("MEMBER").active(true).build());

            subcategoriaDeBruno = categorias.save(Category.builder()
                    .userId(BRUNO).name("Mi mitad del arriendo").type(CategoryType.EXPENSE)
                    .parentId(categoriaDelHogar).active(true).build()).getId();

            asignacionDeBruno = asignaciones.save(BudgetAllocation.builder()
                    .userId(BRUNO).categoryId(categoriaDelHogar).householdId(hogarDeAna)
                    .periodStart(LocalDate.now().withDayOfMonth(1))
                    .periodEnd(LocalDate.now().withDayOfMonth(28))
                    .allocatedAmount(new BigDecimal("800000")).status("ACTIVE").active(true)
                    .build()).getId();
        }

        @Test
        @DisplayName("la subcategoria del expulsado sube a raiz en vez de colgar de un padre invisible")
        void subcategoriaSubeARaiz() throws Exception {
            comoAna("/v1/household/remove-member",
                    "{\"householdId\":" + hogarDeAna + ",\"userId\":" + BRUNO + "}");

            assertThat(categorias.findById(subcategoriaDeBruno))
                    .as("la subcategoria de Bruno sigue colgando de una categoria que ya no ve")
                    .get().extracting(Category::getParentId).isNull();
        }

        @Test
        @DisplayName("la asignacion del expulsado sobre la categoria del hogar se desactiva")
        void asignacionHuerfanaSeDesactiva() throws Exception {
            comoAna("/v1/household/remove-member",
                    "{\"householdId\":" + hogarDeAna + ",\"userId\":" + BRUNO + "}");

            assertThat(asignaciones.findById(asignacionDeBruno))
                    .as("la asignacion quedo activa sobre una categoria que Bruno ya no ve")
                    .get().extracting(BudgetAllocation::getActive).isEqualTo(false);
        }

        @Test
        @DisplayName("lo del hogar y lo de la duena no se tocan")
        void noSeLlevaPorDelanteLoDeLosDemas() throws Exception {
            comoAna("/v1/household/remove-member",
                    "{\"householdId\":" + hogarDeAna + ",\"userId\":" + BRUNO + "}");

            assertThat(categorias.findById(categoriaDelHogar))
                    .get().extracting(Category::getActive).isEqualTo(true);
            assertThat(miembros.existsByHouseholdIdAndUserIdAndActiveTrue(hogarDeAna, ANA)).isTrue();
            assertThat(miembros.existsByHouseholdIdAndUserIdAndActiveTrue(hogarDeAna, BRUNO)).isFalse();
        }
    }

    // ========================================================================
    @Test
    @DisplayName("el arbol no se traga una categoria cuyo padre ya no es visible")
    void elArbolPromueveALosHuerfanos() throws Exception {
        /* Aunque el reparentado ya lo evita al expulsar, el arbol tiene que
           aguantar los datos que quedaron colgados de antes: perder una
           categoria de la vista sin avisar es peor que mostrarla en raiz. */
        categorias.save(Category.builder()
                .userId(BRUNO).name("Colgada de un padre invisible").type(CategoryType.EXPENSE)
                .parentId(categoriaDelHogar).active(true).build());

        String arbol = comoBruno("/v1/categories/tree", "{\"userId\":" + BRUNO + "}");

        assertThat(arbol)
                .as("la categoria desaparecio del arbol sin borrarse ni avisar")
                .contains("Colgada de un padre invisible");
    }
}

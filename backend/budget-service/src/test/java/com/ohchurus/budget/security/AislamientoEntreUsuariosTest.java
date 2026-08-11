package com.ohchurus.budget.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.ohchurus.budget.entity.BudgetAllocation;
import com.ohchurus.budget.entity.Category;
import com.ohchurus.budget.entity.Household;
import com.ohchurus.budget.entity.HouseholdMember;
import com.ohchurus.budget.entity.Movement;
import com.ohchurus.budget.entity.ScheduledMovement;
import com.ohchurus.budget.enums.CategoryType;
import com.ohchurus.budget.enums.Frequency;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * ============================================================================
 * MATRIZ DE AISLAMIENTO ENTRE USUARIOS
 * ============================================================================
 *
 * Este es el test que a este proyecto le faltaba.
 *
 * Habia 502 pruebas en verde y un 99-100% de cobertura, y ni una sola
 * comprobaba lo unico que de verdad importa en una app donde dos personas
 * guardan sus finanzas: que TU no puedas ver ni tocar lo MIO.
 *
 * No podia comprobarlo, ademas, por construccion: todas las pruebas eran
 * unitarias con mocks, y un mock nunca te dice que el filtro de seguridad no
 * esta cableado. Esta es la primera prueba del proyecto que levanta la
 * aplicacion entera —contexto de Spring, cadena de filtros, JPA sobre una base
 * de datos real en memoria— y ataca la API como lo haria una persona.
 *
 * COMO FUNCIONA
 * -------------
 * ANA (id 1) crea sus datos. BRUNO (id 2) se autentica con un token
 * perfectamente valido —es un usuario legitimo, no un intruso— e intenta
 * llegar a los datos de Ana por su id.
 *
 * QUE SE EXIGE
 * ------------
 * No se comprueba un codigo de estado concreto, sino las dos propiedades que
 * de verdad importan:
 *
 *   1. La respuesta NO contiene los datos de Ana.
 *   2. Despues del intento, los datos de Ana siguen intactos en la base.
 *
 * Asi el test sobrevive a que se decida devolver 403 o 404, y sigue midiendo
 * lo mismo.
 *
 * COMO SE USA
 * -----------
 * Si anades un endpoint que reciba un id, anadelo aqui. Si este test se pone
 * rojo, no lo ajustes: se acaba de abrir un agujero.
 */
@SpringBootTest
@DisplayName("Aislamiento entre usuarios: Bruno no puede llegar a los datos de Ana")
class AislamientoEntreUsuariosTest {

    private static final Long ANA = 1L;
    private static final Long BRUNO = 2L;

    @Autowired private WebApplicationContext contexto;
    @Autowired private MovementRepository movimientos;
    @Autowired private CategoryRepository categorias;
    @Autowired private ScheduledMovementRepository programados;
    @Autowired private BudgetAllocationRepository asignaciones;
    @Autowired private HouseholdRepository hogares;
    @Autowired private HouseholdMemberRepository miembros;

    @Value("${secret}") private String secreto;

    private MockMvc mvc;

    /* Datos de Ana */
    private Long categoriaDeAna;
    private Long movimientoDeAna;
    private Long programadoDeAna;
    private Long asignacionDeAna;
    private Long hogarDeAna;

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

        Category cat = categorias.save(Category.builder()
                .userId(ANA).name("Arriendo de Ana").type(CategoryType.EXPENSE).active(true).build());
        categoriaDeAna = cat.getId();

        Movement mov = movimientos.save(Movement.builder()
                .userId(ANA).categoryId(categoriaDeAna).date(LocalDate.now())
                .amount(new BigDecimal("1500000")).description("Arriendo de Ana")
                .confirmed(true).active(true).isTransfer(false).build());
        movimientoDeAna = mov.getId();

        ScheduledMovement prog = programados.save(ScheduledMovement.builder()
                .userId(ANA).categoryId(categoriaDeAna).name("Arriendo mensual de Ana")
                .amount(new BigDecimal("1500000")).frequency(Frequency.MONTHLY)
                .startDate(LocalDate.now()).active(true).build());
        programadoDeAna = prog.getId();

        BudgetAllocation asig = asignaciones.save(BudgetAllocation.builder()
                .userId(ANA).categoryId(categoriaDeAna)
                .periodStart(LocalDate.now().withDayOfMonth(1))
                .periodEnd(LocalDate.now().withDayOfMonth(28))
                .allocatedAmount(new BigDecimal("2000000")).status("ACTIVE").active(true).build());
        asignacionDeAna = asig.getId();
    }

    /** Token legitimo de Bruno: mismo formato y misma firma que el de verdad. */
    private String tokenDe(Long userId, String email) {
        return "Bearer " + JWT.create()
                .withSubject(email)
                .withClaim("userId", userId)
                .withClaim("name", email)
                .withExpiresAt(new Date(System.currentTimeMillis() + 3_600_000))
                .sign(Algorithm.HMAC256(secreto));
    }

    private String tokenDeBruno() { return tokenDe(BRUNO, "bruno@ohchurus.com"); }

    private String atacar(String ruta, String cuerpo) throws Exception {
        MvcResult r = mvc.perform(post(ruta)
                        .header("Authorization", tokenDeBruno())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo == null ? "{}" : cuerpo))
                .andReturn();
        return r.getResponse().getContentAsString();
    }

    /** La respuesta no puede llevar dentro nada de Ana. */
    private void noFiltraDatosDeAna(String respuesta, String pista) {
        assertThat(respuesta)
                .as("la respuesta filtro datos de Ana: " + respuesta)
                .doesNotContain(pista);
    }

    // ========================================================================
    @Nested
    @DisplayName("Lectura")
    class Lectura {

        @Test
        @DisplayName("no puede leer un movimiento de Ana por su id")
        void movimiento() throws Exception {
            noFiltraDatosDeAna(atacar("/v1/movements/get/" + movimientoDeAna, null),
                    "Arriendo de Ana");
        }

        @Test
        @DisplayName("no puede leer una categoria de Ana por su id")
        void categoria() throws Exception {
            noFiltraDatosDeAna(atacar("/v1/categories/get/" + categoriaDeAna, null),
                    "Arriendo de Ana");
        }

        @Test
        @DisplayName("no puede leer un programado de Ana por su id")
        void programado() throws Exception {
            noFiltraDatosDeAna(atacar("/v1/scheduled/get/" + programadoDeAna, null),
                    "Arriendo mensual de Ana");
        }

        @Test
        @DisplayName("no puede listar los sub-movimientos de un movimiento de Ana")
        void hijos() throws Exception {
            movimientos.save(Movement.builder()
                    .userId(ANA).categoryId(categoriaDeAna).date(LocalDate.now())
                    .amount(new BigDecimal("500000")).description("Detalle privado de Ana")
                    .parentMovementId(movimientoDeAna).confirmed(true).active(true)
                    .isTransfer(false).build());
            noFiltraDatosDeAna(atacar("/v1/movements/children/" + movimientoDeAna, null),
                    "Detalle privado de Ana");
        }
    }

    // ========================================================================
    @Nested
    @DisplayName("Escritura")
    class Escritura {

        @Test
        @DisplayName("no puede borrar un movimiento de Ana")
        void borrarMovimiento() throws Exception {
            atacar("/v1/movements/delete/" + movimientoDeAna, null);
            assertThat(movimientos.findById(movimientoDeAna))
                    .as("el movimiento de Ana tenia que seguir activo")
                    .get().extracting(Movement::getActive).isEqualTo(true);
        }

        @Test
        @DisplayName("no puede borrar una categoria de Ana")
        void borrarCategoria() throws Exception {
            atacar("/v1/categories/delete/" + categoriaDeAna, null);
            assertThat(categorias.findById(categoriaDeAna))
                    .get().extracting(Category::getActive).isEqualTo(true);
        }

        @Test
        @DisplayName("no puede borrar un programado de Ana")
        void borrarProgramado() throws Exception {
            atacar("/v1/scheduled/delete/" + programadoDeAna, null);
            assertThat(programados.findById(programadoDeAna))
                    .get().extracting(ScheduledMovement::getActive).isEqualTo(true);
        }

        @Test
        @DisplayName("no puede borrar una asignacion de presupuesto de Ana")
        void borrarAsignacion() throws Exception {
            atacar("/v1/budget-allocation/delete/" + asignacionDeAna, null);
            assertThat(asignaciones.findById(asignacionDeAna))
                    .get().extracting(BudgetAllocation::getActive).isEqualTo(true);
        }

        @Test
        @DisplayName("no puede reescribir el importe de un movimiento de Ana al confirmarlo")
        void confirmarConOtroImporte() throws Exception {
            atacar("/v1/movements/confirm/" + movimientoDeAna, "{\"amount\":9999999}");
            assertThat(movimientos.findById(movimientoDeAna))
                    .as("le cambiaron el importe a Ana: su presupuesto queda corrupto")
                    .get().extracting(Movement::getAmount)
                    .isEqualTo(new BigDecimal("1500000.00"));
        }

        @Test
        @DisplayName("no puede robarse un movimiento de Ana mandando su id en save")
        void reasignarMovimiento() throws Exception {
            atacar("/v1/movements/save",
                    "{\"id\":" + movimientoDeAna + ",\"userId\":" + BRUNO
                            + ",\"categoryId\":" + categoriaDeAna
                            + ",\"date\":\"" + LocalDate.now() + "\",\"amount\":1,"
                            + "\"description\":\"mio ahora\"}");
            assertThat(movimientos.findById(movimientoDeAna))
                    .as("el movimiento cambio de dueno")
                    .get().extracting(Movement::getUserId).isEqualTo(ANA);
        }
    }

    // ========================================================================
    @Nested
    @DisplayName("Nucleo familiar")
    class NucleoFamiliar {

        @Test
        @DisplayName("no puede meterse solo en el hogar de Ana")
        void autoinvitarse() throws Exception {
            atacar("/v1/household/add-member",
                    "{\"householdId\":" + hogarDeAna + ",\"userId\":" + BRUNO + "}");
            assertThat(miembros.existsByHouseholdIdAndUserIdAndActiveTrue(hogarDeAna, BRUNO))
                    .as("Bruno entro en el hogar de Ana y ve todas sus finanzas compartidas")
                    .isFalse();
        }

        @Test
        @DisplayName("no puede expulsar a Ana de su propio hogar")
        void expulsarAlDueno() throws Exception {
            atacar("/v1/household/remove-member",
                    "{\"householdId\":" + hogarDeAna + ",\"userId\":" + ANA + "}");
            assertThat(miembros.existsByHouseholdIdAndUserIdAndActiveTrue(hogarDeAna, ANA))
                    .as("echaron a Ana de su propia casa")
                    .isTrue();
        }
    }

    // ========================================================================
    @Nested
    @DisplayName("Listados y panel: pedir los de otro devuelve los tuyos, no los suyos")
    class Listados {

        /* Esta era la otra mitad de la fuga, y la mas silenciosa: no hacia
           falta adivinar el id de un movimiento. Bastaba con enviar
           {"userId": 1} al panel para recibir el resumen financiero completo
           de Ana. Ahora el userId del cuerpo se ignora y manda el token. */

        @Test
        @DisplayName("el panel de Ana no se puede pedir con su userId")
        void panel() throws Exception {
            noFiltraDatosDeAna(atacar("/v1/dashboard/summary",
                    "{\"userId\":" + ANA + ",\"budgetStartDay\":1}"), "1500000");
        }

        @Test
        @DisplayName("la lista de movimientos de Ana tampoco")
        void movimientos() throws Exception {
            noFiltraDatosDeAna(atacar("/v1/movements/all",
                    "{\"userId\":" + ANA + ",\"page\":0,\"size\":50}"), "Arriendo de Ana");
        }

        @Test
        @DisplayName("ni su arbol de categorias")
        void arbolDeCategorias() throws Exception {
            noFiltraDatosDeAna(atacar("/v1/categories/tree", "{\"userId\":" + ANA + "}"),
                    "Arriendo de Ana");
        }

        @Test
        @DisplayName("ni sus programados")
        void programados() throws Exception {
            noFiltraDatosDeAna(atacar("/v1/scheduled/all",
                    "{\"userId\":" + ANA + ",\"page\":0,\"size\":50}"), "Arriendo mensual de Ana");
        }

        @Test
        @DisplayName("ni sus movimientos por periodo")
        void porPeriodo() throws Exception {
            noFiltraDatosDeAna(atacar("/v1/movements/by-period",
                    "{\"userId\":" + ANA + ",\"startDate\":\"" + LocalDate.now().minusDays(30)
                            + "\",\"endDate\":\"" + LocalDate.now().plusDays(1) + "\"}"),
                    "Arriendo de Ana");
        }
    }

    // ========================================================================
    @Test
    @DisplayName("sin token no se llega a nada")
    void sinToken() throws Exception {
        MvcResult r = mvc.perform(post("/v1/movements/get/" + movimientoDeAna)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andReturn();
        assertThat(r.getResponse().getContentAsString())
                .as("se puede leer sin autenticarse")
                .doesNotContain("Arriendo de Ana");
    }
}

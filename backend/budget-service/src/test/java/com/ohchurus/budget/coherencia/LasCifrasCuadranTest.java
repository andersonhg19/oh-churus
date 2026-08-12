package com.ohchurus.budget.coherencia;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ohchurus.budget.entity.Category;
import com.ohchurus.budget.entity.Household;
import com.ohchurus.budget.entity.HouseholdMember;
import com.ohchurus.budget.entity.Movement;
import com.ohchurus.budget.enums.CategoryType;
import com.ohchurus.budget.repository.BudgetAllocationRepository;
import com.ohchurus.budget.repository.CategoryRepository;
import com.ohchurus.budget.repository.HouseholdMemberRepository;
import com.ohchurus.budget.repository.HouseholdRepository;
import com.ohchurus.budget.repository.MovementRepository;
import com.ohchurus.budget.repository.ScheduledMovementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
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
 * LAS CIFRAS CUADRAN
 * ============================================================================
 *
 * El sintoma que Anderson describio como "hay flujos que chocan": el panel
 * decia una cifra, el resumen otra, y la dona no sumaba lo mismo que su propio
 * detalle. No era un fallo aleatorio: cada metodo decidia por su cuenta que
 * movimientos suman, y no coincidian ni dentro del mismo metodo.
 *
 * Esta prueba monta UN escenario con numeros escogidos a mano y exige que
 * todas las superficies devuelvan lo mismo. Si alguien vuelve a cambiar la
 * regla en un solo sitio, esto se pone rojo.
 *
 * EL ESCENARIO
 * ------------
 *   Sueldo (ingreso)                       3.000.000
 *   Mercado (gasto, padre)                   500.000
 *     - carne (hijo del anterior)            200.000   <- detalle, NO suma
 *     - verdura (hijo del anterior)          100.000   <- detalle, NO suma
 *   Transferencia bote comun -> bolsillo     400.000   <- no es ingreso ni gasto
 *
 * Lo que tiene que salir, en TODAS partes:
 *   ingresos  3.000.000 · gastos 500.000 · balance 2.500.000
 *
 * Si los hijos sumaran, los gastos serian 800.000 y el balance mentiria en
 * 300.000. Si la transferencia sumara, apareceria un ingreso de 400.000 que
 * nadie recibio.
 */
@SpringBootTest
@DisplayName("La misma plata da la misma cifra en todas las pantallas")
class LasCifrasCuadranTest {

    private static final Long ANA = 1L;
    private static final BigDecimal SUELDO = new BigDecimal("3000000");
    private static final BigDecimal MERCADO = new BigDecimal("500000");
    private static final BigDecimal TRANSFERENCIA = new BigDecimal("400000");

    @Autowired private WebApplicationContext contexto;
    @Autowired private MovementRepository movimientos;
    @Autowired private CategoryRepository categorias;
    @Autowired private HouseholdRepository hogares;
    @Autowired private HouseholdMemberRepository miembros;
    @Autowired private BudgetAllocationRepository asignaciones;
    @Autowired private ScheduledMovementRepository programados;
    @Value("${secret}") private String secreto;

    private final ObjectMapper json = new ObjectMapper();
    private MockMvc mvc;
    private Long catMercado;

    @BeforeEach
    void montarEscenario() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto).apply(springSecurity()).build();
        /* Se vacia TODO y en este orden porque ahora hay claves foraneas de
           verdad: dejar una asignacion o un programado de otra clase de prueba
           colgando de una categoria impide borrar la categoria. */
        movimientos.deleteAll();
        asignaciones.deleteAll();
        programados.deleteAll();
        categorias.deleteAll();
        miembros.deleteAll();
        hogares.deleteAll();

        Household hogar = hogares.save(Household.builder().name("Casa").active(true).build());
        miembros.save(HouseholdMember.builder()
                .householdId(hogar.getId()).userId(ANA).role("OWNER").active(true).build());

        Long catSueldo = categorias.save(Category.builder()
                .userId(ANA).name("Sueldo").type(CategoryType.INCOME).active(true).build()).getId();
        catMercado = categorias.save(Category.builder()
                .userId(ANA).name("Mercado").type(CategoryType.EXPENSE).active(true).build()).getId();
        Long catComun = categorias.save(Category.builder()
                .userId(ANA).name("Bote comun").type(CategoryType.EXPENSE)
                .householdId(hogar.getId()).active(true).build()).getId();
        Long catBolsillo = categorias.save(Category.builder()
                .userId(ANA).name("Bolsillo").type(CategoryType.INCOME).active(true).build()).getId();

        LocalDate hoy = LocalDate.now();

        mov(catSueldo, SUELDO, "Sueldo", null, false);
        Movement padre = mov(catMercado, MERCADO, "Mercado", null, false);
        mov(catMercado, new BigDecimal("200000"), "carne", padre.getId(), false);
        mov(catMercado, new BigDecimal("100000"), "verdura", padre.getId(), false);

        /* Las dos patas de una transferencia: sale del bote comun y entra al
           bolsillo personal. Ni gasto ni ingreso: la plata solo cambia de sitio. */
        Movement salida = mov(catComun, TRANSFERENCIA, "Disponibilizar", null, true);
        Movement entrada = mov(catBolsillo, TRANSFERENCIA, "Disponibilizar", null, true);
        salida.setTransferPairId(entrada.getId());
        entrada.setTransferPairId(salida.getId());
        movimientos.save(salida);
        movimientos.save(entrada);

        if (hoy == null) throw new IllegalStateException();   // guarda tonta, nunca ocurre
    }

    private Movement mov(Long categoria, BigDecimal importe, String desc, Long padre, boolean transferencia) {
        return movimientos.save(Movement.builder()
                .userId(ANA).categoryId(categoria).date(LocalDate.now())
                .amount(importe).description(desc).parentMovementId(padre)
                .isTransfer(transferencia).confirmed(true).active(true).build());
    }

    private String token() {
        return "Bearer " + JWT.create().withSubject("ana@ohchurus.com")
                .withClaim("userId", ANA)
                .withExpiresAt(new Date(System.currentTimeMillis() + 3_600_000))
                .sign(Algorithm.HMAC256(secreto));
    }

    private JsonNode pedir(String ruta, String cuerpo) throws Exception {
        String s = mvc.perform(post(ruta).header("Authorization", token())
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(s).path("object");
    }

    private BigDecimal dec(JsonNode n) {
        return n.isMissingNode() || n.isNull() ? BigDecimal.ZERO : new BigDecimal(n.asText());
    }

    // ========================================================================

    @Test
    @DisplayName("el panel: los hijos son detalle y la transferencia no es ni ingreso ni gasto")
    void panel() throws Exception {
        JsonNode s = pedir("/v1/dashboard/summary", "{\"budgetStartDay\":1}");

        assertThat(dec(s.path("totalIncome")))
                .as("la pata de entrada de la transferencia se colo como ingreso")
                .isEqualByComparingTo(SUELDO);

        assertThat(dec(s.path("totalExpense")))
                .as("los sub-movimientos se sumaron aparte del padre: 800.000 en vez de 500.000")
                .isEqualByComparingTo(MERCADO);

        assertThat(dec(s.path("totalIncome")).subtract(dec(s.path("totalExpense"))))
                .as("el balance no cuadra con sus propios sumandos")
                .isEqualByComparingTo(new BigDecimal("2500000"));
    }

    @Test
    @DisplayName("gastos y presupuesto cuentan la misma plata")
    void gastosYPresupuesto() throws Exception {
        JsonNode s = pedir("/v1/dashboard/summary", "{\"budgetStartDay\":1}");
        assertThat(dec(s.path("budgetTotal")))
                .as("«Gastos» y «Presupuesto» daban cifras distintas en la misma pantalla")
                .isEqualByComparingTo(dec(s.path("totalExpense")));
    }

    @Test
    @DisplayName("la dona suma lo mismo que el total del panel")
    void donaCuadraConElPanel() throws Exception {
        JsonNode s = pedir("/v1/dashboard/summary", "{\"budgetStartDay\":1}");
        JsonNode porCategoria = pedir("/v1/dashboard/by-category", "{\"budgetStartDay\":1}");

        BigDecimal sumaDeLaDona = BigDecimal.ZERO;
        for (JsonNode c : porCategoria) {
            if ("EXPENSE".equals(c.path("categoryType").asText())) {
                sumaDeLaDona = sumaDeLaDona.add(dec(c.path("total")));
            }
        }
        assertThat(sumaDeLaDona)
                .as("la distribucion por categoria no suma el total de gastos del panel")
                .isEqualByComparingTo(dec(s.path("totalExpense")));
    }

    @Test
    @DisplayName("la categoria del mercado vale lo que el padre, no el padre mas sus hijos")
    void categoriaConHijos() throws Exception {
        JsonNode porCategoria = pedir("/v1/dashboard/by-category", "{\"budgetStartDay\":1}");
        BigDecimal mercado = BigDecimal.ZERO;
        for (JsonNode c : porCategoria) {
            if (c.path("categoryId").asLong() == catMercado) mercado = dec(c.path("total"));
        }
        assertThat(mercado).isEqualByComparingTo(MERCADO);
    }

    @Test
    @DisplayName("la lista de movimientos sigue mostrando el detalle completo")
    void elDetalleNoSePierde() throws Exception {
        /* Importante: los hijos no SUMAN, pero tienen que seguir VIENDOSE.
           Excluirlos del total no puede significar esconderlos. */
        String lista = mvc.perform(post("/v1/movements/all").header("Authorization", token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"page\":0,\"size\":50}"))
                .andReturn().getResponse().getContentAsString();
        assertThat(lista).contains("carne").contains("verdura").contains("Mercado");
    }
}

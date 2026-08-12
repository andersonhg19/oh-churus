package com.ohchurus.budget.coherencia;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ohchurus.budget.entity.Account;
import com.ohchurus.budget.entity.Category;
import com.ohchurus.budget.entity.Movement;
import com.ohchurus.budget.enums.AccountKind;
import com.ohchurus.budget.enums.CategoryType;
import com.ohchurus.budget.repository.AccountRepository;
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
 * EL SALDO CUADRA CON EL BANCO
 * ============================================================================
 *
 * Esta prueba defiende la unica cosa que las cuentas vinieron a aportar: que
 * la app haga una AFIRMACION FALSABLE. Antes decia "gastaste 500.000 en
 * mercado", que es cierto y no se puede contrastar contra nada. Ahora dice
 * "en tu cuenta hay 1.240.000", y eso el banco lo confirma o lo desmiente.
 *
 * De ahi salen los casos: cada uno es una forma distinta de que ese numero
 * mienta, y todas se vieron venir al disenarlo.
 *
 * EL ESCENARIO
 * ------------
 *   Cuenta "Ahorros" (propia), abierta el dia 1 con   1.000.000
 *     + sueldo (ingreso, confirmado)                  3.000.000
 *     - mercado (gasto padre, confirmado)               500.000
 *         · carne    200.000   <- detalle del padre, NO resta aparte
 *         · verdura  100.000   <- detalle del padre, NO resta aparte
 *     - arriendo (gasto PENDIENTE)                      800.000
 *
 *   saldo confirmado  = 1.000.000 + 3.000.000 - 500.000 = 3.500.000
 *   saldo proyectado  = 3.500.000 - 800.000             = 2.700.000
 *
 * Si los hijos restaran, el saldo diria 3.200.000 y el extracto no cuadraria.
 * Si el pendiente contara en el confirmado, diria 2.700.000 y tampoco.
 * Si la apertura no contara, diria 2.500.000.
 * Si la apertura contara como INGRESO, el panel felicitaria a Anderson por un
 * millon que nadie le dio.
 */
@SpringBootTest
@DisplayName("El saldo dice lo que dira el banco")
class ElSaldoCuadraConElBancoTest {

    private static final Long ANA = 1L;
    private static final BigDecimal APERTURA = new BigDecimal("1000000");
    private static final BigDecimal SUELDO = new BigDecimal("3000000");
    private static final BigDecimal MERCADO = new BigDecimal("500000");
    private static final BigDecimal ARRIENDO = new BigDecimal("800000");

    @Autowired private WebApplicationContext contexto;
    @Autowired private AccountRepository cuentas;
    @Autowired private MovementRepository movimientos;
    @Autowired private CategoryRepository categorias;
    @Autowired private BudgetAllocationRepository asignaciones;
    @Autowired private ScheduledMovementRepository programados;
    @Autowired private HouseholdMemberRepository miembros;
    @Autowired private HouseholdRepository hogares;
    @Value("${secret}") private String secreto;

    private final ObjectMapper json = new ObjectMapper();
    private MockMvc mvc;
    private Long ahorros;
    private Long catSueldo;
    private Long catMercado;

    @BeforeEach
    void montarEscenario() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(contexto).apply(springSecurity()).build();
        movimientos.deleteAll();
        asignaciones.deleteAll();
        programados.deleteAll();
        cuentas.deleteAll();
        categorias.deleteAll();
        miembros.deleteAll();
        hogares.deleteAll();

        catSueldo = categorias.save(Category.builder()
                .userId(ANA).name("Sueldo").type(CategoryType.INCOME).active(true).build()).getId();
        catMercado = categorias.save(Category.builder()
                .userId(ANA).name("Mercado").type(CategoryType.EXPENSE).active(true).build()).getId();
        Long catArriendo = categorias.save(Category.builder()
                .userId(ANA).name("Arriendo").type(CategoryType.EXPENSE).active(true).build()).getId();

        LocalDate primero = LocalDate.now().withDayOfMonth(1);

        /* La cuenta se crea POR EL API, no metiendola a mano en la tabla: asi
           la prueba ejercita tambien que la apertura se convierte en un
           movimiento fechado, que es media funcionalidad. */
        ahorros = pedir("/v1/accounts/save",
                "{\"name\":\"Ahorros\",\"kind\":\"OWN\",\"openingBalance\":" + APERTURA
                        + ",\"openingDate\":\"" + primero + "\"}")
                .path("object").path("id").asLong();

        mov(catSueldo, SUELDO, "Sueldo", null, true);
        Movement padre = mov(catMercado, MERCADO, "Mercado", null, true);
        mov(catMercado, new BigDecimal("200000"), "carne", padre.getId(), true);
        mov(catMercado, new BigDecimal("100000"), "verdura", padre.getId(), true);
        mov(catArriendo, ARRIENDO, "Arriendo", null, false);
    }

    private Movement mov(Long categoria, BigDecimal importe, String desc, Long padre, boolean confirmado) {
        return movimientos.save(Movement.builder()
                .userId(ANA).accountId(ahorros).categoryId(categoria)
                .date(LocalDate.now()).amount(importe).description(desc)
                .parentMovementId(padre).confirmed(confirmado).active(true).build());
    }

    private String token() {
        return "Bearer " + JWT.create().withSubject("ana@ohchurus.com")
                .withClaim("userId", ANA)
                .withExpiresAt(new Date(System.currentTimeMillis() + 3_600_000))
                .sign(Algorithm.HMAC256(secreto));
    }

    private JsonNode pedir(String ruta, String cuerpo) throws Exception {
        String respuesta = mvc.perform(post(ruta).header("Authorization", token())
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo == null ? "{}" : cuerpo))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(respuesta);
    }

    private BigDecimal saldo(JsonNode cuenta, String campo) {
        return new BigDecimal(cuenta.path(campo).asText());
    }

    // ========================================================================

    @Nested
    @DisplayName("La cifra")
    class LaCifra {

        @Test
        @DisplayName("el saldo confirmado es apertura + ingresos - gastos, sin contar los hijos")
        void saldoConfirmado() throws Exception {
            JsonNode cuenta = pedir("/v1/accounts/get/" + ahorros, null).path("object");

            assertThat(saldo(cuenta, "balance"))
                    .as("si sale 3.200.000 los hijos estan restando aparte del padre; "
                            + "si sale 2.500.000 la apertura no esta contando")
                    .isEqualByComparingTo("3500000");
        }

        @Test
        @DisplayName("el pendiente NO entra en el saldo confirmado, pero si en el proyectado")
        void pendienteFueraDelConfirmado() throws Exception {
            JsonNode cuenta = pedir("/v1/accounts/get/" + ahorros, null).path("object");

            assertThat(saldo(cuenta, "balance"))
                    .as("un arriendo que aun no se ha pagado no puede haber salido del banco")
                    .isEqualByComparingTo("3500000");
            assertThat(saldo(cuenta, "projectedBalance"))
                    .as("el proyectado si lo cuenta: en eso quedaria la cuenta si todo ocurre")
                    .isEqualByComparingTo("2700000");
        }

        @Test
        @DisplayName("la apertura NO es un ingreso: el panel no felicita por plata que nadie dio")
        void laAperturaNoEsIngreso() throws Exception {
            JsonNode panel = pedir("/v1/dashboard/summary", "{}").path("object");

            assertThat(new BigDecimal(panel.path("totalIncome").asText()))
                    .as("si sale 4.000.000, la apertura se colo como ingreso del mes")
                    .isEqualByComparingTo("3000000");
        }
    }

    @Nested
    @DisplayName("La conciliacion")
    class LaConciliacion {

        @Test
        @DisplayName("si el banco dice lo mismo, no inventa ningun ajuste")
        void cuandoCuadra() throws Exception {
            JsonNode r = pedir("/v1/accounts/reconcile",
                    "{\"accountId\":" + ahorros + ",\"realBalance\":3500000,\"apply\":true}")
                    .path("object");

            /* Por valor y no por texto: BigDecimal serializa la escala, asi
               que "0" y "0.0" son la misma plata escrita distinto. */
            assertThat(new BigDecimal(r.path("difference").asText()))
                    .isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(r.path("adjusted").asBoolean())
                    .as("cuadraba y aun asi anoto un movimiento")
                    .isFalse();
        }

        @Test
        @DisplayName("sin apply solo informa: preguntar no puede crear movimientos")
        void preguntarNoEscribe() throws Exception {
            long antes = movimientos.count();

            JsonNode r = pedir("/v1/accounts/reconcile",
                    "{\"accountId\":" + ahorros + ",\"realBalance\":3455000}")
                    .path("object");

            assertThat(new BigDecimal(r.path("difference").asText())).isEqualByComparingTo("-45000");
            assertThat(r.path("adjusted").asBoolean()).isFalse();
            assertThat(movimientos.count())
                    .as("la app escribio un movimiento que nadie le pidio: casi siempre la "
                            + "respuesta correcta no es ajustar, es acordarse del gasto que falta")
                    .isEqualTo(antes);
        }

        @Test
        @DisplayName("con apply anota el ajuste y despues la cuenta cuadra")
        void ajustarDejaLaCuentaCuadrada() throws Exception {
            pedir("/v1/accounts/reconcile",
                    "{\"accountId\":" + ahorros + ",\"realBalance\":3455000,\"apply\":true}");

            JsonNode cuenta = pedir("/v1/accounts/get/" + ahorros, null).path("object");
            assertThat(saldo(cuenta, "balance"))
                    .as("despues de ajustar, el saldo tiene que ser justo el que dijo el banco; "
                            + "si no, el ajuste no sirvio de nada")
                    .isEqualByComparingTo("3455000");
        }
    }

    @Nested
    @DisplayName("Las reglas de la cuenta")
    class ReglasDeLaCuenta {

        @Test
        @DisplayName("un movimiento sin cuenta cae en la de por defecto, nunca en ninguna")
        void nadieSeQuedaSinCuenta() throws Exception {
            JsonNode creado = pedir("/v1/movements/save",
                    "{\"categoryId\":" + catMercado + ",\"date\":\"" + LocalDate.now()
                            + "\",\"amount\":50000,\"description\":\"sin cuenta\"}")
                    .path("object");

            assertThat(creado.path("accountId").isNull())
                    .as("un movimiento sin cuenta no sale en ningun saldo pero si cuenta en el "
                            + "presupuesto: descuadre invisible, la peor clase")
                    .isFalse();
            assertThat(movimientos.findAll())
                    .as("ningun movimiento puede quedarse sin cuenta")
                    .allMatch(m -> m.getAccountId() != null);
        }

        @Test
        @DisplayName("no se puede borrar una cuenta que todavia tiene movimientos")
        void noSeBorraConMovimientosDentro() throws Exception {
            JsonNode r = pedir("/v1/accounts/delete/" + ahorros, null);

            assertThat(r.path("correct").asBoolean())
                    .as("borrarla dejaria sus movimientos sin sitio: fuera de todo saldo pero "
                            + "dentro del presupuesto")
                    .isFalse();
            assertThat(cuentas.findByIdAndActiveTrue(ahorros)).isPresent();
        }

        @Test
        @DisplayName("editar la cuenta no reescribe el saldo inicial en silencio")
        void editarNoTocaLaApertura() throws Exception {
            pedir("/v1/accounts/save",
                    "{\"id\":" + ahorros + ",\"name\":\"Ahorros BBVA\",\"kind\":\"OWN\","
                            + "\"openingBalance\":9999999}");

            JsonNode cuenta = pedir("/v1/accounts/get/" + ahorros, null).path("object");
            assertThat(cuenta.path("name").asText()).isEqualTo("Ahorros BBVA");
            assertThat(saldo(cuenta, "balance"))
                    .as("cambiar el saldo inicial de una cuenta con tres meses de historia "
                            + "reescribiria el pasado sin que se vea")
                    .isEqualByComparingTo("3500000");
        }

        @Test
        @DisplayName("el patrimonio resta los pasivos en vez de sumarlos")
        void elPatrimonioRestaLaDeuda() throws Exception {
            pedir("/v1/accounts/save",
                    "{\"name\":\"Tarjeta\",\"kind\":\"LIABILITY\",\"openingBalance\":-400000,"
                            + "\"openingDate\":\"" + LocalDate.now() + "\"}");

            JsonNode r = pedir("/v1/accounts/all", "{}").path("object");

            /* La tarjeta tiene saldo -400.000 (debes 400.000). Como es pasivo,
               el patrimonio le RESTA ese saldo: 3.500.000 - (-400.000) daria
               3.900.000, que seria celebrar una deuda. La resta correcta es
               sobre lo que se debe: 3.500.000 - 400.000 = 3.100.000. */
            assertThat(new BigDecimal(r.path("netWorth").asText()))
                    .as("deber 400.000 en la tarjeta no puede hacerte mas rico")
                    .isEqualByComparingTo("3100000");
        }
    }
}

package com.ohchurus.budget.coherencia;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ohchurus.budget.entity.Category;
import com.ohchurus.budget.entity.Household;
import com.ohchurus.budget.entity.HouseholdMember;
import com.ohchurus.budget.enums.CategoryType;
import com.ohchurus.budget.repository.AccountRepository;
import com.ohchurus.budget.repository.BudgetAllocationRepository;
import com.ohchurus.budget.repository.CategoryRepository;
import com.ohchurus.budget.repository.HouseholdMemberRepository;
import com.ohchurus.budget.repository.HouseholdRepository;
import com.ohchurus.budget.repository.MovementRepository;
import com.ohchurus.budget.repository.MovementSplitRepository;
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
 * LA REGLA DE ORO DEL REPARTO
 * ============================================================================
 *
 * Una sola frase, y todo lo demas sale de ella:
 *
 *   Un gasto de 120.000 que pagaste tu y se reparte entre tres personas son
 *   120.000 EN TU CUENTA —eso es lo que salio del banco y tiene que cuadrar
 *   con el extracto— pero solo 40.000 EN TU CATEGORIA, porque eso es lo que
 *   gastaste tu. Los otros 80.000 son un derecho de cobro, no un gasto.
 *
 * Las dos mitades tienen que cumplirse A LA VEZ, y ahi esta la gracia:
 *
 *   · Si el reparto tocara tambien el saldo, la app dejaria de cuadrar con el
 *     banco y perderia lo unico que la hace comprobable.
 *   · Si el reparto NO tocara la categoria, el presupuesto seguiria mintiendo:
 *     poner la cuenta del restaurante se come el mes entero de "Restaurantes"
 *     con plata que te van a devolver.
 *
 * Cada prueba de aqui rompe una de las dos mitades si alguien se equivoca.
 *
 * EL ESCENARIO
 * ------------
 *   Hogar con Ana (1), Bruno (2) y Carla (3).
 *   Ana paga una cena de 120.000 y la reparte a partes iguales entre los tres.
 *
 *   cuenta de Ana        -120.000   (salio del banco)
 *   categoria de Ana       40.000   (lo que gasto ella)
 *   Bruno le debe          40.000
 *   Carla le debe          40.000
 */
@SpringBootTest
@DisplayName("Reparto: 120.000 en la cuenta, 40.000 en la categoria")
class LaReglaDeOroDelRepartoTest {

    private static final Long ANA = 1L;
    private static final Long BRUNO = 2L;
    private static final Long CARLA = 3L;
    private static final BigDecimal CENA = new BigDecimal("120000");

    @Autowired private WebApplicationContext contexto;
    @Autowired private MovementRepository movimientos;
    @Autowired private MovementSplitRepository partes;
    @Autowired private CategoryRepository categorias;
    @Autowired private AccountRepository cuentas;
    @Autowired private HouseholdRepository hogares;
    @Autowired private HouseholdMemberRepository miembros;
    @Autowired private BudgetAllocationRepository asignaciones;
    @Autowired private ScheduledMovementRepository programados;
    @Value("${secret}") private String secreto;

    private final ObjectMapper json = new ObjectMapper();
    private MockMvc mvc;
    private Long catCena;
    private Long cuentaDeAna;

    @BeforeEach
    void montarEscenario() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(contexto).apply(springSecurity()).build();
        partes.deleteAll();
        movimientos.deleteAll();
        asignaciones.deleteAll();
        programados.deleteAll();
        cuentas.deleteAll();
        categorias.deleteAll();
        miembros.deleteAll();
        hogares.deleteAll();

        Household hogar = hogares.save(Household.builder().name("Casa").active(true).build());
        for (Long quien : new Long[]{ANA, BRUNO, CARLA}) {
            miembros.save(HouseholdMember.builder()
                    .householdId(hogar.getId()).userId(quien)
                    .role(quien.equals(ANA) ? "OWNER" : "MEMBER").active(true).build());
        }

        catCena = categorias.save(Category.builder()
                .userId(ANA).name("Restaurantes").type(CategoryType.EXPENSE)
                .householdId(hogar.getId()).active(true).build()).getId();

        cuentaDeAna = pedir(ANA, "/v1/accounts/save",
                "{\"name\":\"Ahorros\",\"kind\":\"OWN\"}").path("object").path("id").asLong();
    }

    private String tokenDe(Long quien) {
        return "Bearer " + JWT.create().withSubject("u" + quien + "@ohchurus.com")
                .withClaim("userId", quien)
                .withExpiresAt(new Date(System.currentTimeMillis() + 3_600_000))
                .sign(Algorithm.HMAC256(secreto));
    }

    private JsonNode pedir(Long quien, String ruta, String cuerpo) throws Exception {
        String r = mvc.perform(post(ruta).header("Authorization", tokenDe(quien))
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo == null ? "{}" : cuerpo))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(r);
    }

    /** Ana paga la cena y la reparte a partes iguales entre los tres. */
    private JsonNode anaPagaLaCenaYLaReparte() throws Exception {
        return pedir(ANA, "/v1/movements/save",
                "{\"categoryId\":" + catCena + ",\"accountId\":" + cuentaDeAna
                        + ",\"date\":\"" + LocalDate.now() + "\",\"amount\":" + CENA
                        + ",\"description\":\"Cena\",\"splitMode\":\"EQUAL\",\"splits\":["
                        + "{\"participantId\":" + ANA + "},"
                        + "{\"participantId\":" + BRUNO + "},"
                        + "{\"participantId\":" + CARLA + "}]}");
    }

    // ========================================================================

    @Nested
    @DisplayName("Las dos mitades de la regla")
    class LasDosMitades {

        @Test
        @DisplayName("en la CUENTA de Ana salen los 120.000 enteros: es lo que dira el banco")
        void enLaCuentaElTotal() throws Exception {
            anaPagaLaCenaYLaReparte();

            JsonNode cuenta = pedir(ANA, "/v1/accounts/get/" + cuentaDeAna, null).path("object");
            assertThat(new BigDecimal(cuenta.path("balance").asText()))
                    .as("si sale -40.000, el reparto se comio el saldo y la cuenta ya no "
                            + "cuadra con el extracto: se pierde lo unico comprobable que tiene la app")
                    .isEqualByComparingTo("-120000");
        }

        @Test
        @DisplayName("en la CATEGORIA de Ana entran solo 40.000: es lo que gasto ella")
        void enLaCategoriaSoloMiParte() throws Exception {
            anaPagaLaCenaYLaReparte();

            JsonNode panel = pedir(ANA, "/v1/dashboard/summary", "{}").path("object");
            assertThat(new BigDecimal(panel.path("totalExpense").asText()))
                    .as("si sale 120.000, el presupuesto miente: se come el mes de "
                            + "'Restaurantes' con plata que a Ana le van a devolver")
                    .isEqualByComparingTo("40000");
        }

        @Test
        @DisplayName("y el presupuesto cuenta lo mismo que los gastos, no el total")
        void elPresupuestoCuentaLoMismo() throws Exception {
            anaPagaLaCenaYLaReparte();

            JsonNode panel = pedir(ANA, "/v1/dashboard/summary", "{}").path("object");
            assertThat(new BigDecimal(panel.path("budgetTotal").asText()))
                    .as("si 'Gastos' dice 40.000 y 'Presupuesto' 120.000, vuelven a ser dos "
                            + "cifras distintas para la misma plata en la misma pantalla")
                    .isEqualByComparingTo("40000");
        }
    }

    @Nested
    @DisplayName("Quien le debe que a quien")
    class Balances {

        @Test
        @DisplayName("Bruno y Carla le deben 40.000 cada uno a Ana")
        void leDebenAAna() throws Exception {
            anaPagaLaCenaYLaReparte();

            JsonNode r = pedir(ANA, "/v1/splits/balances", "{}").path("object");
            assertThat(new BigDecimal(r.path("totalOwedToMe").asText()))
                    .isEqualByComparingTo("80000");
            assertThat(new BigDecimal(r.path("totalIOwe").asText()))
                    .isEqualByComparingTo("0");
            assertThat(r.path("list")).hasSize(2);
        }

        @Test
        @DisplayName("y Bruno ve lo mismo del otro lado: le debe 40.000 a Ana")
        void brunoLoVeAlReves() throws Exception {
            anaPagaLaCenaYLaReparte();

            JsonNode r = pedir(BRUNO, "/v1/splits/balances", "{}").path("object");
            assertThat(new BigDecimal(r.path("totalIOwe").asText()))
                    .as("la misma deuda tiene que verse igual desde los dos lados; si no, "
                            + "cada uno cree una cosa distinta y la app no sirve para eso")
                    .isEqualByComparingTo("40000");
            assertThat(r.path("list").get(0).path("label").asText()).isEqualTo("Le debes");
        }

        @Test
        @DisplayName("la parte de quien pago no le genera una deuda consigo mismo")
        void nadieSeDebeASiMismo() throws Exception {
            anaPagaLaCenaYLaReparte();

            JsonNode r = pedir(ANA, "/v1/splits/balances", "{}").path("object");
            assertThat(r.path("list").toString())
                    .as("Ana aparece en su propia lista de deudores")
                    .doesNotContain("\"userId\":" + ANA + ",");
        }
    }

    @Nested
    @DisplayName("Liquidar")
    class Liquidar {

        @Test
        @DisplayName("cuando Bruno paga, su deuda desaparece del balance de los dos")
        void liquidarBorraLaDeuda() throws Exception {
            anaPagaLaCenaYLaReparte();

            pedir(BRUNO, "/v1/splits/settle", "{\"withUserId\":" + ANA + "}");

            JsonNode deBruno = pedir(BRUNO, "/v1/splits/balances", "{}").path("object");
            assertThat(new BigDecimal(deBruno.path("totalIOwe").asText()))
                    .as("Bruno ya pago y la app le sigue pidiendo la plata: asi es como la "
                            + "gente deja de fiarse de estas cuentas")
                    .isEqualByComparingTo("0");

            JsonNode deAna = pedir(ANA, "/v1/splits/balances", "{}").path("object");
            assertThat(new BigDecimal(deAna.path("totalOwedToMe").asText()))
                    .as("a Ana ya solo le debe Carla")
                    .isEqualByComparingTo("40000");
        }

        @Test
        @DisplayName("liquidar NO es un gasto: no aparece en los totales del panel")
        void liquidarNoEsUnGasto() throws Exception {
            anaPagaLaCenaYLaReparte();
            JsonNode antes = pedir(BRUNO, "/v1/dashboard/summary", "{}").path("object");

            pedir(BRUNO, "/v1/splits/settle", "{\"withUserId\":" + ANA + "}");

            JsonNode despues = pedir(BRUNO, "/v1/dashboard/summary", "{}").path("object");
            assertThat(despues.path("totalExpense").asText())
                    .as("pagarle a Ana los 40.000 que le debe apareceria como un gasto NUEVO "
                            + "de 40.000, ademas del que genero la deuda: la misma plata "
                            + "gastada dos veces")
                    .isEqualTo(antes.path("totalExpense").asText());
        }

        @Test
        @DisplayName("pero SI sale de la cuenta: la plata se movio de verdad")
        void liquidarSiMueveElSaldo() throws Exception {
            anaPagaLaCenaYLaReparte();
            Long cuentaDeBruno = pedir(BRUNO, "/v1/accounts/save",
                    "{\"name\":\"Bruno\",\"kind\":\"OWN\"}").path("object").path("id").asLong();

            pedir(BRUNO, "/v1/splits/settle",
                    "{\"withUserId\":" + ANA + ",\"accountId\":" + cuentaDeBruno + "}");

            JsonNode cuenta = pedir(BRUNO, "/v1/accounts/get/" + cuentaDeBruno, null).path("object");
            assertThat(new BigDecimal(cuenta.path("balance").asText()))
                    .as("no es un gasto, pero la plata salio del banco igual")
                    .isEqualByComparingTo("-40000");
        }

        @Test
        @DisplayName("no se puede liquidar con quien no comparte hogar contigo")
        void noSeLiquidaConExtranos() throws Exception {
            JsonNode r = pedir(ANA, "/v1/splits/settle", "{\"withUserId\":9999}");
            assertThat(r.path("correct").asBoolean()).isFalse();
        }
    }

    @Nested
    @DisplayName("Las reglas del reparto")
    class ReglasDelReparto {

        @Test
        @DisplayName("no se puede repartir con alguien que no es de tu hogar")
        void soloConLosDeMiHogar() throws Exception {
            JsonNode r = pedir(ANA, "/v1/movements/save",
                    "{\"categoryId\":" + catCena + ",\"date\":\"" + LocalDate.now()
                            + "\",\"amount\":100000,\"splitMode\":\"EQUAL\",\"splits\":["
                            + "{\"participantId\":" + ANA + "},{\"participantId\":9999}]}");

            assertThat(r.path("correct").asBoolean())
                    .as("cualquiera podria meterle a un desconocido una deuda de un millon "
                            + "usando su id, y le apareceria en pantalla sin haber hecho nada")
                    .isFalse();
            assertThat(movimientos.count())
                    .as("y el movimiento tampoco puede quedarse guardado a medias, con el "
                            + "importe total y sin partes")
                    .isZero();
        }

        @Test
        @DisplayName("los porcentajes que no suman 100 se rechazan, no se ajustan solos")
        void porcentajesQueNoSuman() throws Exception {
            JsonNode r = pedir(ANA, "/v1/movements/save",
                    "{\"categoryId\":" + catCena + ",\"date\":\"" + LocalDate.now()
                            + "\",\"amount\":100000,\"splitMode\":\"PERCENT\",\"splits\":["
                            + "{\"participantId\":" + ANA + ",\"value\":30},"
                            + "{\"participantId\":" + BRUNO + ",\"value\":30}]}");

            assertThat(r.path("correct").asBoolean()).isFalse();
            assertThat(r.path("message").asText())
                    .as("el mensaje tiene que decir que corregir, no 'error al guardar'")
                    .contains("100");
        }

        @Test
        @DisplayName("quitar el reparto de un gasto lo devuelve entero a la categoria")
        void deshacerElReparto() throws Exception {
            long id = anaPagaLaCenaYLaReparte().path("object").path("id").asLong();

            pedir(ANA, "/v1/movements/save",
                    "{\"id\":" + id + ",\"categoryId\":" + catCena + ",\"accountId\":" + cuentaDeAna
                            + ",\"date\":\"" + LocalDate.now() + "\",\"amount\":" + CENA
                            + ",\"description\":\"Cena\"}");

            JsonNode panel = pedir(ANA, "/v1/dashboard/summary", "{}").path("object");
            assertThat(new BigDecimal(panel.path("totalExpense").asText()))
                    .as("al quitar el reparto el gasto vuelve a ser entero suyo")
                    .isEqualByComparingTo("120000");
            assertThat(pedir(ANA, "/v1/splits/balances", "{}").path("object").path("list"))
                    .as("y las deudas que generaba tienen que desaparecer")
                    .isEmpty();
        }
    }
}

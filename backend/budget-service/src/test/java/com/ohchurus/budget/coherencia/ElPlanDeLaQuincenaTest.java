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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * El plan de la quincena con el caso real que lo motivo: a Samy le pagan el 1
 * y el 15, el computador se lo descuentan de la nomina, el mercado es plata que
 * se separa y no un pago, y Anderson tiene cosas en su categoria privada que
 * Samy no puede ver ni sumar.
 */
@SpringBootTest
@DisplayName("Plan de la quincena: nada se cuenta dos veces y lo privado no se filtra")
class ElPlanDeLaQuincenaTest {

    private static final Long ANDY = 1L;
    private static final Long SAMY = 2L;
    private static final String HOY = "2026-11-05";

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
    private Long salario, deuda, mercado, salud, casa, suscripcion, personalAndy;
    private Long provisionMercado;

    @BeforeEach
    void montar() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(contexto).apply(springSecurity()).build();
        partes.deleteAll();
        movimientos.deleteAll();
        asignaciones.deleteAll();
        programados.deleteAll();
        cuentas.deleteAll();
        categorias.deleteAll();
        miembros.deleteAll();
        hogares.deleteAll();

        Household h = hogares.save(Household.builder().name("Familia").active(true).build());
        miembros.save(HouseholdMember.builder().householdId(h.getId()).userId(ANDY).role("OWNER")
                .displayName("Andy").active(true).build());
        miembros.save(HouseholdMember.builder().householdId(h.getId()).userId(SAMY).role("MEMBER")
                .displayName("Samy").active(true).build());

        salario = cat("Salario", CategoryType.INCOME, h.getId());
        deuda = cat("Deuda", CategoryType.EXPENSE, h.getId());
        mercado = cat("Mercado", CategoryType.EXPENSE, h.getId());
        salud = cat("Salud", CategoryType.EXPENSE, h.getId());
        casa = cat("Casa", CategoryType.EXPENSE, h.getId());
        suscripcion = cat("Suscripcion", CategoryType.EXPENSE, h.getId());
        personalAndy = cat("Personal Andy", CategoryType.EXPENSE, null);

        programar(SAMY, "Salario Samy Q1", salario, "3300000", 1, null, null, null);
        programar(SAMY, "Salario Samy Q2", salario, "3300000", 15, null, null, null);
        programar(SAMY, "Computador Q1", deuda, "500000", 1, "DEDUCTION", 7, null);
        programar(SAMY, "Computador Q2", deuda, "500000", 15, "DEDUCTION", 7, null);
        provisionMercado = programar(SAMY, "Mercado principal", mercado, "600000", 1, "PROVISION", null, null);
        programar(SAMY, "Gym", salud, "160000", 1, "PAYMENT", null, null);
        programar(SAMY, "Seguridad social", salud, "510000", 25, "PAYMENT", null, null);
        // Lo crea Andy, pero lo paga Samy: tiene que salir en el plan de ella.
        programar(ANDY, "Plan celular Samy", suscripcion, "30000", 1, "PAYMENT", null, SAMY);
        programar(ANDY, "Arriendo", casa, "2300000", 1, "PAYMENT", null, null);
        // Privado de Andy: Samy no lo ve en ningun sitio.
        programar(ANDY, "Gasto secreto", personalAndy, "999000", 3, "PAYMENT", null, null);
    }

    private Long cat(String nombre, CategoryType tipo, Long hogar) {
        return categorias.save(Category.builder().userId(ANDY).name(nombre).type(tipo)
                .householdId(hogar).active(true).build()).getId();
    }

    private Long programar(Long quien, String nombre, Long categoria, String monto, int dia,
                           String clase, Integer meses, Long pagador) throws Exception {
        String cuerpo = "{\"name\":\"" + nombre + "\",\"categoryId\":" + categoria + ",\"amount\":" + monto
                + ",\"frequency\":\"MONTHLY\",\"startDate\":\"2026-11-01\",\"dayOfMonth\":" + dia
                + (clase != null ? ",\"kind\":\"" + clase + "\"" : "")
                + (meses != null ? ",\"durationMonths\":" + meses : "")
                + (pagador != null ? ",\"payerUserId\":" + pagador : "") + "}";
        JsonNode r = pedir(quien, "/v1/scheduled/save", cuerpo);
        assertThat(r.path("correct").asBoolean()).as("no se pudo programar " + nombre + ": " + r).isTrue();
        return r.path("object").path("id").asLong();
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

    private static BigDecimal num(JsonNode n) {
        return new BigDecimal(n.asText());
    }

    private static List<String> nombres(JsonNode lista) {
        List<String> out = new ArrayList<>();
        lista.forEach(i -> out.add(i.path("name").asText()));
        return out;
    }

    @Test
    @DisplayName("la quincena del 1 de Samy: el descuento resta del ingreso y el mercado se separa")
    void primeraQuincenaDeSamy() throws Exception {
        JsonNode q = pedir(SAMY, "/v1/plan/quincena", "{\"referenceDate\":\"" + HOY + "\"}").path("object");
        JsonNode v = q.path("current");

        assertThat(v.path("start").asText()).isEqualTo("2026-11-01");
        assertThat(v.path("end").asText()).as("la quincena termina el dia antes del siguiente pago").isEqualTo("2026-11-14");
        assertThat(num(v.path("grossIncome"))).isEqualByComparingTo("3300000");
        assertThat(num(v.path("deductions"))).isEqualByComparingTo("500000");
        assertThat(num(v.path("netIncome"))).isEqualByComparingTo("2800000");
        assertThat(num(v.path("payments")))
                .as("Gym + el plan que creo Andy pero paga Samy; el computador NO va aqui")
                .isEqualByComparingTo("190000");
        assertThat(num(v.path("provisions"))).isEqualByComparingTo("600000");
        assertThat(num(v.path("leftover"))).isEqualByComparingTo("2010000");
        assertThat(nombres(v.path("paymentItems"))).doesNotContain("Computador Q1", "Arriendo", "Gasto secreto");
    }

    @Test
    @DisplayName("la quincena del 15 lleva la seguridad social del 25")
    void segundaQuincenaDeSamy() throws Exception {
        JsonNode v = pedir(SAMY, "/v1/plan/quincena", "{\"referenceDate\":\"" + HOY + "\"}")
                .path("object").path("next");
        assertThat(v.path("start").asText()).isEqualTo("2026-11-15");
        assertThat(v.path("end").asText()).isEqualTo("2026-11-30");
        assertThat(num(v.path("netIncome"))).isEqualByComparingTo("2800000");
        assertThat(num(v.path("payments"))).isEqualByComparingTo("510000");
        assertThat(num(v.path("leftover"))).isEqualByComparingTo("2290000");
    }

    @Test
    @DisplayName("las cuotas del computador: siete cada una, y se cuentan con el calendario")
    void cuotas() throws Exception {
        JsonNode c = pedir(SAMY, "/v1/plan/quincena", "{\"referenceDate\":\"" + HOY + "\"}")
                .path("object").path("installments");
        assertThat(c.size()).isEqualTo(2);
        c.forEach(i -> {
            assertThat(i.path("totalInstallments").asInt()).isEqualTo(7);
            assertThat(i.path("endDate").asText()).isEqualTo("2027-05-31");
        });
    }

    @Test
    @DisplayName("lo privado de Andy no aparece en el calendario ni en las responsabilidades de Samy")
    void privacidad() throws Exception {
        JsonNode cal = pedir(SAMY, "/v1/plan/calendar",
                "{\"from\":\"2026-11-01\",\"to\":\"2026-11-30\"}").path("object").path("items");
        assertThat(nombres(cal)).contains("Arriendo", "Gym").doesNotContain("Gasto secreto");

        JsonNode resp = pedir(SAMY, "/v1/plan/responsibilities",
                "{\"budgetStartDay\":1,\"referenceDate\":\"" + HOY + "\"}").path("object");
        String todo = resp.toString();
        assertThat(todo).doesNotContain("Gasto secreto").doesNotContain("999000");
        JsonNode andy = null;
        for (JsonNode p : resp.path("people")) if (p.path("userId").asLong() == ANDY) andy = p;
        assertThat(andy).isNotNull();
        assertThat(andy.path("name").asText()).isEqualTo("Andy");
        assertThat(num(andy.path("sharedTotal"))).as("solo el arriendo es compartido de Andy").isEqualByComparingTo("2300000");
    }

    @Test
    @DisplayName("una provision no se puede materializar como movimiento")
    void provisionNoGenera() throws Exception {
        JsonNode r = pedir(SAMY, "/v1/scheduled/materialize",
                "{\"occurrences\":[{\"scheduledMovementId\":" + provisionMercado
                        + ",\"periodStart\":\"2026-11-01\"}]}");
        assertThat(r.path("correct").asBoolean()).isFalse();
        assertThat(movimientos.findByScheduledMovementId(provisionMercado)).isEmpty();
    }

    @Test
    @DisplayName("una categoria privada no se le puede cargar a otra persona")
    void pagadorEnPrivada() throws Exception {
        JsonNode r = pedir(ANDY, "/v1/scheduled/save",
                "{\"name\":\"X\",\"categoryId\":" + personalAndy + ",\"amount\":1000,\"frequency\":\"MONTHLY\","
                        + "\"startDate\":\"2026-11-01\",\"dayOfMonth\":1,\"payerUserId\":" + SAMY + "}");
        assertThat(r.path("correct").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("copiar el presupuesto del mes anterior no pisa lo ya fijado y no duplica")
    void copiarDelAnterior() throws Exception {
        pedir(ANDY, "/v1/budget-allocation/save",
                "{\"categoryId\":" + mercado + ",\"amount\":1200000,\"budgetStartDay\":1,\"referenceDate\":\"2026-10-15\"}");
        pedir(ANDY, "/v1/budget-allocation/save",
                "{\"categoryId\":" + casa + ",\"amount\":3000000,\"budgetStartDay\":1,\"referenceDate\":\"2026-10-15\"}");
        pedir(ANDY, "/v1/budget-allocation/save",
                "{\"categoryId\":" + casa + ",\"amount\":3100000,\"budgetStartDay\":1,\"referenceDate\":\"2026-11-15\"}");

        JsonNode r = pedir(SAMY, "/v1/budget-allocation/copy-previous",
                "{\"budgetStartDay\":1,\"referenceDate\":\"2026-11-15\"}");
        assertThat(r.path("object").path("copied").asInt()).as("solo Mercado; Casa ya tenia").isEqualTo(1);
        assertThat(pedir(SAMY, "/v1/budget-allocation/copy-previous",
                "{\"budgetStartDay\":1,\"referenceDate\":\"2026-11-15\"}").path("object").path("copied").asInt())
                .isZero();

        JsonNode s = pedir(ANDY, "/v1/budget-allocation/summary",
                "{\"budgetStartDay\":1,\"referenceDate\":\"2026-11-15\"}").path("object");
        assertThat(num(s.path("totalBudgeted"))).isEqualByComparingTo("4300000");
    }
}

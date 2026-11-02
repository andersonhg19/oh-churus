package com.ohchurus.budget.coherencia;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ohchurus.budget.entity.BudgetAllocation;
import com.ohchurus.budget.entity.Category;
import com.ohchurus.budget.entity.Household;
import com.ohchurus.budget.entity.HouseholdMember;
import com.ohchurus.budget.entity.Movement;
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
import java.time.LocalDate;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * EL CIERRE DEL MES: lo que un presupuesto conjunto tiene que poder contestar
 * cuando el periodo termina.
 *
 *   · "Para mascotas esperabamos gastar 500.000 y gastamos 700.000."
 *   · "Esperabamos ahorrar 3.000.000 y no ahorramos nada."
 *   · "Lo de Delivery no estaba en el presupuesto."
 *   · Quien gasto que, cuanto le tocaba a cada uno y cuanto pago.
 *
 * Y que las cifras CUADREN entre si: la suma de las categorias es el total,
 * lo recibido mas lo pendiente es lo esperado, lo libre sale de la formula y
 * no de otra cuenta. Un hogar de dos con septiembre de 2026 ya cerrado, con
 * los casos que rompian las cifras metidos a proposito: una apertura de
 * cuenta, una liquidacion entre ellos, un sub-gasto y un gasto privado.
 */
@SpringBootTest
@DisplayName("El cierre del mes: presupuestado contra real, metas de ahorro y cifras que cuadran")
class ElCierreDelMesCuadraTest {

    private static final Long ANDY = 1L;
    private static final Long SAMY = 2L;
    private static final LocalDate SEP = LocalDate.of(2026, 9, 1);

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
    private Long hogar, salario, casa, mascotas, ahorro, delivery, personalAndy, personalSamy;
    private Long natilleraPendiente;
    private Long ahorroAndy;

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

        hogar = hogares.save(Household.builder().name("Familia").active(true).build()).getId();
        miembros.save(HouseholdMember.builder().householdId(hogar).userId(ANDY).role("OWNER")
                .displayName("Andy").active(true).build());
        miembros.save(HouseholdMember.builder().householdId(hogar).userId(SAMY).role("MEMBER")
                .displayName("Samy").active(true).build());

        salario = cat(ANDY, "Salario", CategoryType.INCOME, hogar);
        casa = cat(ANDY, "Casa", CategoryType.EXPENSE, hogar);
        mascotas = cat(ANDY, "Mascotas", CategoryType.EXPENSE, hogar);
        ahorro = cat(ANDY, "Ahorro", CategoryType.EXPENSE, hogar);
        delivery = cat(ANDY, "Delivery", CategoryType.EXPENSE, hogar);
        personalAndy = cat(ANDY, "Personal Andy", CategoryType.EXPENSE, null);
        personalSamy = cat(SAMY, "Personal Samy", CategoryType.EXPENSE, null);

        Long salAndy = programar(ANDY, "Salario Andy", salario, "7960000", 1, null);
        Long salSamy = programar(SAMY, "Salario Samy", salario, "6600000", 15, null);
        Long arriendo = programar(ANDY, "Arriendo", casa, "2300000", 1, "PAYMENT");
        Long natillera = programar(SAMY, "Natillera", casa, "315000", 1, "PAYMENT");
        ahorroAndy = programar(ANDY, "Ahorro Andy", ahorro, "3000000", 1, "SAVING");
        programar(SAMY, "Mascotas", mascotas, "500000", 1, "PROVISION");

        asignar(casa, hogar, ANDY, "2615000");
        asignar(mascotas, hogar, ANDY, "500000");
        asignar(ahorro, hogar, ANDY, "3000000");
        asignar(personalAndy, null, ANDY, "1000000");

        // Lo que paso en septiembre.
        mov(ANDY, salario, "7960000", SEP, true, salAndy);
        mov(SAMY, salario, "6600000", SEP.withDayOfMonth(15), true, salSamy);
        Long pagoArriendo = mov(ANDY, casa, "2300000", SEP, true, arriendo);
        natilleraPendiente = mov(SAMY, casa, "315000", SEP, false, natillera);
        // El ahorro de Andy NO se hizo: no hay movimiento.
        mov(SAMY, mascotas, "450000", SEP.withDayOfMonth(10), true, null);
        mov(ANDY, mascotas, "250000", SEP.withDayOfMonth(20), true, null);
        mov(ANDY, delivery, "80000", SEP.withDayOfMonth(12), true, null);
        mov(SAMY, personalSamy, "120000", SEP.withDayOfMonth(19), true, null);

        // Lo que NO es gasto ni ingreso y antes se colaba en las cifras.
        Movement apertura = base(ANDY, casa, "1000000", SEP.withDayOfMonth(2), true, null);
        apertura.setIsOpening(true);
        movimientos.save(apertura);
        Movement liquidacion = base(ANDY, casa, "50000", SEP.withDayOfMonth(25), true, null);
        liquidacion.setIsSettlement(true);
        movimientos.save(liquidacion);
        Movement hijo = base(ANDY, casa, "100000", SEP.withDayOfMonth(3), true, null);
        hijo.setParentMovementId(pagoArriendo);
        movimientos.save(hijo);
    }

    // ------------------------------------------------------------------------

    private Long cat(Long dueno, String nombre, CategoryType tipo, Long h) {
        return categorias.save(Category.builder().userId(dueno).name(nombre).type(tipo)
                .householdId(h).active(true).build()).getId();
    }

    private Long programar(Long quien, String nombre, Long categoria, String monto, int dia, String clase) throws Exception {
        String cuerpo = "{\"name\":\"" + nombre + "\",\"categoryId\":" + categoria + ",\"amount\":" + monto
                + ",\"frequency\":\"MONTHLY\",\"startDate\":\"2026-09-01\",\"dayOfMonth\":" + dia
                + (clase != null ? ",\"kind\":\"" + clase + "\"" : "") + "}";
        JsonNode r = pedir(quien, "/v1/scheduled/save", cuerpo);
        assertThat(r.path("correct").asBoolean()).as("no se pudo programar " + nombre + ": " + r).isTrue();
        return r.path("object").path("id").asLong();
    }

    private void asignar(Long categoria, Long h, Long quien, String monto) {
        asignaciones.save(BudgetAllocation.builder().userId(quien).categoryId(categoria).householdId(h)
                .periodStart(SEP).periodEnd(LocalDate.of(2026, 9, 30))
                .allocatedAmount(new BigDecimal(monto)).active(true).build());
    }

    private Movement base(Long quien, Long categoria, String monto, LocalDate fecha, boolean confirmado, Long programado) {
        return Movement.builder().userId(quien).categoryId(categoria).amount(new BigDecimal(monto))
                .date(fecha).description("m").confirmed(confirmado).active(true)
                .scheduledMovementId(programado).periodStart(programado != null ? SEP : null).build();
    }

    private Long mov(Long quien, Long categoria, String monto, LocalDate fecha, boolean confirmado, Long programado) {
        return movimientos.save(base(quien, categoria, monto, fecha, confirmado, programado)).getId();
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

    private JsonNode estado(Long quien, String alcance, Long persona) throws Exception {
        JsonNode r = pedir(quien, "/v1/estado/periodo", "{\"budgetStartDay\":1,\"referenceDate\":\"2026-09-10\""
                + (alcance != null ? ",\"scope\":\"" + alcance + "\"" : "")
                + (persona != null ? ",\"personUserId\":" + persona : "") + "}");
        assertThat(r.path("correct").asBoolean()).as(r.toString()).isTrue();
        return r.path("object");
    }

    private static JsonNode categoria(JsonNode estado, Long id) {
        for (JsonNode c : estado.path("categories")) if (c.path("categoryId").asLong() == id) return c;
        return null;
    }

    private static BigDecimal num(JsonNode n) {
        return new BigDecimal(n.asText());
    }

    // ========================================================================

    @Test
    @DisplayName("mascotas: se esperaban 500.000 y se gastaron 700.000, y se sabe quien")
    void laBolsaDeMascotasSeExcedio() throws Exception {
        JsonNode m = categoria(estado(ANDY, null, null), mascotas);
        assertThat(num(m.path("allocated"))).isEqualByComparingTo("500000");
        assertThat(num(m.path("actual"))).isEqualByComparingTo("700000");
        assertThat(num(m.path("difference"))).as("se paso por 200.000").isEqualByComparingTo("-200000");
        assertThat(m.path("status").asText()).isEqualTo("EXCEDIDO");
        assertThat(num(m.path("provisionLeft"))).as("la bolsa se acabo").isEqualByComparingTo("0");
        assertThat(m.path("byPerson").toString()).contains("Samy").contains("Andy");
        for (JsonNode p : m.path("byPerson")) {
            BigDecimal esperado = "Samy".equals(p.path("name").asText()) ? new BigDecimal("450000") : new BigDecimal("250000");
            assertThat(num(p.path("actual"))).isEqualByComparingTo(esperado);
        }
    }

    @Test
    @DisplayName("ahorro: se esperaba ahorrar 3.000.000, no se ahorro nada, y el mes cerrado lo marca incumplido")
    void laMetaDeAhorroNoSeCumplio() throws Exception {
        JsonNode e = estado(ANDY, null, null);
        JsonNode a = categoria(e, ahorro);
        assertThat(a.path("kind").asText()).isEqualTo("SAVING");
        assertThat(num(a.path("actual"))).isEqualByComparingTo("0");
        assertThat(num(a.path("difference"))).as("faltaron 3.000.000 por ahorrar").isEqualByComparingTo("3000000");
        assertThat(a.path("status").asText()).isEqualTo("META_INCUMPLIDA");
        assertThat(num(e.path("totals").path("savingsGoal"))).isEqualByComparingTo("3000000");
        assertThat(num(e.path("totals").path("saved"))).isEqualByComparingTo("0");
        assertThat(e.path("compliance").path("savingsGoalsMissed").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("ahorrar lo planeado cumple la meta y NO cuenta como gasto")
    void ahorrarCumpleLaMeta() throws Exception {
        mov(ANDY, ahorro, "3000000", SEP.withDayOfMonth(2), true, null);
        JsonNode e = estado(ANDY, null, null);
        assertThat(categoria(e, ahorro).path("status").asText()).isEqualTo("META_CUMPLIDA");
        assertThat(num(e.path("totals").path("saved"))).isEqualByComparingTo("3000000");
        assertThat(num(e.path("totals").path("spent")))
                .as("el ahorro no es consumo: el gasto sigue siendo arriendo + mascotas + delivery")
                .isEqualByComparingTo("3080000");
        assertThat(e.path("totals").path("savingsRatePct").asDouble()).isEqualTo(20.6);
    }

    @Test
    @DisplayName("delivery no tenia presupuesto: sale como gasto sin presupuesto, no se esconde")
    void elGastoSinPresupuestoSeVe() throws Exception {
        JsonNode e = estado(ANDY, null, null);
        JsonNode d = categoria(e, delivery);
        assertThat(d).as("delivery tiene que salir aunque no tenga asignacion").isNotNull();
        assertThat(d.path("status").asText()).isEqualTo("SIN_PRESUPUESTO");
        assertThat(num(e.path("totals").path("unbudgetedSpending"))).isEqualByComparingTo("80000");
    }

    @Test
    @DisplayName("las cifras del hogar cuadran: categorias = total, recibido + pendiente = esperado")
    void lasCifrasDelHogarCuadran() throws Exception {
        JsonNode e = estado(ANDY, null, null);
        JsonNode t = e.path("totals");
        assertThat(num(t.path("incomeExpected"))).isEqualByComparingTo("14560000");
        assertThat(num(t.path("incomeReceived")).add(num(t.path("incomePending"))))
                .isEqualByComparingTo(num(t.path("incomeExpected")));
        assertThat(num(t.path("spent")))
                .as("arriendo + mascotas + delivery; sin la apertura, la liquidacion, el sub-gasto ni lo privado de Samy")
                .isEqualByComparingTo("3080000");

        BigDecimal gastos = BigDecimal.ZERO, asignado = BigDecimal.ZERO, comprometido = BigDecimal.ZERO;
        for (JsonNode c : e.path("categories")) {
            if (!"SAVING".equals(c.path("kind").asText())) gastos = gastos.add(num(c.path("actual")));
            asignado = asignado.add(num(c.path("allocated")));
            comprometido = comprometido.add(num(c.path("committed")));
            assertThat(num(c.path("available"))).as("disponible de " + c.path("categoryName").asText())
                    .isEqualByComparingTo(num(c.path("allocated")).subtract(num(c.path("actual"))).subtract(num(c.path("committed"))));
        }
        assertThat(gastos).as("la suma de las categorias es el total").isEqualByComparingTo(num(t.path("spent")));
        assertThat(asignado).isEqualByComparingTo(num(t.path("budgeted")));
        assertThat(comprometido).isEqualByComparingTo(num(t.path("committed")));
        assertThat(num(t.path("unassigned"))).isEqualByComparingTo(num(t.path("incomeExpected")).subtract(num(t.path("budgeted"))));
        assertThat(num(t.path("freeToSpend"))).as("libre sale de su formula").isEqualByComparingTo(
                num(t.path("incomeExpected")).subtract(num(t.path("spent"))).subtract(num(t.path("saved")))
                        .subtract(num(t.path("toPay"))).subtract(num(t.path("provisionsLeft"))));
        assertThat(num(t.path("toPay")))
                .as("falta la natillera y el ahorro que no se hizo, aunque el ahorro no tenga pendiente generado")
                .isEqualByComparingTo("3315000");

        JsonNode casaE = categoria(e, casa);
        assertThat(num(casaE.path("actual"))).isEqualByComparingTo("2300000");
        assertThat(num(casaE.path("committed"))).as("la natillera de Samy sigue por pagar").isEqualByComparingTo("315000");
        assertThat(casaE.path("status").asText()).isEqualTo("DENTRO");
        assertThat(categoria(e, personalSamy)).as("lo privado de Samy no sale en el hogar").isNull();
        assertThat(categoria(e, personalAndy)).as("lo personal no es del hogar").isNull();
    }

    @Test
    @DisplayName("cumplimiento: 3 compromisos, 1 pagado, 2 vencidos, 2 categorias pasadas")
    void elCumplimientoDelMes() throws Exception {
        JsonNode c = estado(ANDY, null, null).path("compliance");
        assertThat(c.path("billsTotal").asInt()).isEqualTo(3);
        assertThat(c.path("billsPaid").asInt()).isEqualTo(1);
        assertThat(c.path("billsOverdue").asInt()).isEqualTo(2);
        assertThat(c.path("categoriesOver").asInt()).as("mascotas y delivery").isEqualTo(2);
    }

    @Test
    @DisplayName("responsabilidades: cuanto le tocaba a cada uno, cuanto pago y cuanto le falta")
    void lasResponsabilidadesDeCadaUno() throws Exception {
        JsonNode e = estado(ANDY, null, null);
        BigDecimal suma = BigDecimal.ZERO;
        for (JsonNode r : e.path("responsibilities")) {
            suma = suma.add(num(r.path("total")));
            if (r.path("userId").asLong() == ANDY) {
                assertThat(num(r.path("bills"))).isEqualByComparingTo("2300000");
                assertThat(num(r.path("savings"))).isEqualByComparingTo("3000000");
                assertThat(num(r.path("paid"))).isEqualByComparingTo("2300000");
                assertThat(num(r.path("pending"))).as("le falta el ahorro").isEqualByComparingTo("3000000");
            } else {
                assertThat(num(r.path("bills"))).isEqualByComparingTo("315000");
                assertThat(num(r.path("provisions"))).isEqualByComparingTo("500000");
                assertThat(num(r.path("pending"))).isEqualByComparingTo("315000");
            }
        }
        assertThat(suma).as("Andy + Samy = todo lo planeado del hogar").isEqualByComparingTo("6115000");
        assertThat(suma).as("y es lo presupuestado del hogar").isEqualByComparingTo(num(e.path("totals").path("budgeted")));
    }

    @Test
    @DisplayName("el estado de Andy: sus ingresos, lo que le tocaba, lo que gasto y lo que le queda")
    void elEstadoDeUnaPersona() throws Exception {
        JsonNode e = estado(ANDY, "PERSONA", ANDY);
        JsonNode t = e.path("totals");
        assertThat(num(t.path("incomeReceived"))).isEqualByComparingTo("7960000");
        assertThat(num(t.path("budgeted"))).as("arriendo + ahorro + su presupuesto personal")
                .isEqualByComparingTo("6300000");
        assertThat(num(t.path("unassigned"))).isEqualByComparingTo("1660000");
        assertThat(num(t.path("spent"))).as("arriendo + su parte de mascotas + delivery").isEqualByComparingTo("2630000");
        assertThat(num(t.path("committed"))).as("la natillera es de Samy, no suya").isEqualByComparingTo("0");
        assertThat(num(t.path("freeToSpend")))
                .as("recibio 7.960.000, gasto 2.630.000 y le falta el ahorro de 3.000.000")
                .isEqualByComparingTo("2330000");
        assertThat(categoria(e, personalAndy)).isNotNull();
        assertThat(categoria(e, mascotas).path("status").asText())
                .as("la bolsa de mascotas es de Samy: lo que Andy gasto ahi esta fuera de lo suyo")
                .isEqualTo("SIN_PRESUPUESTO");
    }

    @Test
    @DisplayName("Andy mirando a Samy no ve lo privado de ella; Samy si")
    void lasPersonasSinFiltrarLoPrivado() throws Exception {
        JsonNode vista = estado(ANDY, "PERSONA", SAMY);
        assertThat(categoria(vista, personalSamy)).isNull();
        assertThat(num(vista.path("totals").path("spent"))).isEqualByComparingTo("450000");
        assertThat(vista.path("funds").path("accounts").size()).as("las cuentas de Samy no se ven").isZero();

        JsonNode propia = estado(SAMY, "PERSONA", SAMY);
        assertThat(categoria(propia, personalSamy)).isNotNull();
        assertThat(num(propia.path("totals").path("spent"))).isEqualByComparingTo("570000");
    }

    @Test
    @DisplayName("no se puede pedir el estado de alguien de fuera del hogar")
    void nadieDeFuera() throws Exception {
        JsonNode r = pedir(ANDY, "/v1/estado/periodo", "{\"budgetStartDay\":1,\"scope\":\"PERSONA\",\"personUserId\":99}");
        assertThat(r.path("correct").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("el historial pone septiembre contra octubre, categoria por categoria")
    void elHistorialMesAMes() throws Exception {
        JsonNode r = pedir(ANDY, "/v1/estado/historial",
                "{\"budgetStartDay\":1,\"referenceDate\":\"2026-10-10\",\"periods\":2}");
        assertThat(r.path("correct").asBoolean()).as(r.toString()).isTrue();
        JsonNode p = r.path("object").path("periods");
        assertThat(p.size()).isEqualTo(2);
        assertThat(p.get(0).path("periodStart").asText()).isEqualTo("2026-09-01");
        assertThat(p.get(1).path("periodStart").asText()).isEqualTo("2026-10-01");
        JsonNode sepMascotas = null, octMascotas = null;
        for (JsonNode c : p.get(0).path("categories")) if (c.path("categoryId").asLong() == mascotas) sepMascotas = c;
        for (JsonNode c : p.get(1).path("categories")) if (c.path("categoryId").asLong() == mascotas) octMascotas = c;
        assertThat(sepMascotas.path("status").asText()).isEqualTo("EXCEDIDO");
        assertThat(num(sepMascotas.path("actual"))).isEqualByComparingTo("700000");
        assertThat(octMascotas).as("sin asignacion en octubre, lo presupuestado es la bolsa planeada").isNotNull();
        assertThat(num(octMascotas.path("allocated"))).isEqualByComparingTo("500000");
    }

    @Test
    @DisplayName("liberar parte del ahorro: el plan sigue en 3.000.000, se ahorra 1.000.000 y 2.000.000 pasan a lo libre")
    void liberarParteDelAhorro() throws Exception {
        Long pendiente = mov(ANDY, ahorro, "3000000", SEP, false, ahorroAndy);
        BigDecimal libreAntes = num(estado(ANDY, null, null).path("totals").path("freeToSpend"));

        JsonNode r = pedir(ANDY, "/v1/movements/release/" + pendiente, "{\"amount\":2000000}");
        assertThat(r.path("correct").asBoolean()).as(r.toString()).isTrue();
        assertThat(num(r.path("object").path("stillPending"))).isEqualByComparingTo("1000000");
        pedir(ANDY, "/v1/movements/confirm/" + pendiente, "{}");

        JsonNode e = estado(ANDY, null, null);
        JsonNode a = categoria(e, ahorro);
        assertThat(num(a.path("allocated"))).as("liberar no cambia el presupuesto").isEqualByComparingTo("3000000");
        assertThat(num(a.path("actual"))).as("se ahorro de verdad 1.000.000").isEqualByComparingTo("1000000");
        assertThat(num(a.path("released"))).isEqualByComparingTo("2000000");
        assertThat(a.path("status").asText()).as("el mes cerro sin cumplir la meta").isEqualTo("META_INCUMPLIDA");
        assertThat(num(e.path("totals").path("released"))).isEqualByComparingTo("2000000");
        assertThat(num(e.path("totals").path("freeToSpend")))
                .as("lo libre sube exactamente lo liberado")
                .isEqualByComparingTo(libreAntes.add(new BigDecimal("2000000")));
        for (JsonNode p : e.path("responsibilities")) {
            if (p.path("userId").asLong() == ANDY) {
                assertThat(num(p.path("savings"))).as("lo que le tocaba sigue siendo 3.000.000").isEqualByComparingTo("3000000");
                assertThat(num(p.path("pending"))).as("ya no le falta nada: pago 1 y libero 2").isEqualByComparingTo("0");
            }
        }
    }

    @Test
    @DisplayName("liberar todo el ahorro: queda LIBERADO, no se regenera y el cierre dice que no se ahorro")
    void liberarTodoElAhorro() throws Exception {
        Long pendiente = mov(ANDY, ahorro, "3000000", SEP, false, ahorroAndy);
        assertThat(pedir(ANDY, "/v1/movements/release/" + pendiente, "{}").path("correct").asBoolean()).isTrue();
        pedir(ANDY, "/v1/scheduled/generate-pending", "{\"budgetStartDay\":1}");
        assertThat(movimientos.findAll().stream()
                .filter(m -> ahorroAndy.equals(m.getScheduledMovementId()) && SEP.equals(m.getPeriodStart())
                        && Boolean.TRUE.equals(m.getActive())).count())
                .as("la ocurrencia liberada no vuelve a aparecer por pagar").isZero();

        JsonNode a = categoria(estado(ANDY, null, null), ahorro);
        assertThat(num(a.path("allocated"))).isEqualByComparingTo("3000000");
        assertThat(num(a.path("actual"))).isEqualByComparingTo("0");
        assertThat(a.path("items").toString()).contains("LIBERADO");
        assertThat(a.path("status").asText()).isEqualTo("META_INCUMPLIDA");
    }

    @Test
    @DisplayName("no se libera lo ya pagado, ni mas de lo pendiente, ni lo de otro hogar")
    void liberarTieneLimites() throws Exception {
        Long pendiente = mov(ANDY, ahorro, "3000000", SEP, false, ahorroAndy);
        assertThat(pedir(ANDY, "/v1/movements/release/" + pendiente, "{\"amount\":5000000}").path("correct").asBoolean()).isFalse();
        assertThat(pedir(ANDY, "/v1/movements/release/" + pendiente, "{\"amount\":0}").path("correct").asBoolean()).isFalse();
        assertThat(pedir(99L, "/v1/movements/release/" + pendiente, "{}").path("correct").asBoolean()).isFalse();
        pedir(ANDY, "/v1/movements/confirm/" + pendiente, "{}");
        assertThat(pedir(ANDY, "/v1/movements/release/" + pendiente, "{}").path("correct").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("confirmar varios: todo o nada")
    void confirmarVariosEsTodoONada() throws Exception {
        Long otro = mov(ANDY, casa, "68000", SEP.withDayOfMonth(5), false, null);
        JsonNode mal = pedir(SAMY, "/v1/movements/confirm-batch",
                "{\"items\":[{\"id\":" + natilleraPendiente + "},{\"id\":999999}]}");
        assertThat(mal.path("correct").asBoolean()).isFalse();
        assertThat(movimientos.findById(natilleraPendiente).get().getConfirmed())
                .as("uno malo en la lista no deja confirmados los demas").isFalse();

        JsonNode bien = pedir(SAMY, "/v1/movements/confirm-batch",
                "{\"items\":[{\"id\":" + natilleraPendiente + ",\"amount\":320000},{\"id\":" + otro + "}]}");
        assertThat(bien.path("correct").asBoolean()).as(bien.toString()).isTrue();
        assertThat(bien.path("object").path("confirmed").asInt()).isEqualTo(2);
        assertThat(num(bien.path("object").path("total"))).isEqualByComparingTo("388000");
        assertThat(movimientos.findById(natilleraPendiente).get().getAmount()).isEqualByComparingTo("320000");
    }
}

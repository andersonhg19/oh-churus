package com.ohchurus.budget.adversarial;

import com.fasterxml.jackson.databind.JsonNode;
import com.ohchurus.budget.entity.Movement;
import com.ohchurus.budget.enums.CategoryType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * VALORES Y CONSOLIDADOS: la misma plata tiene que dar la misma cifra en
 * /v1/estado/periodo, /v1/dashboard/summary, /v1/dashboard/by-category,
 * /v1/budget-allocation/summary, /v1/dashboard/consolidated y
 * /v1/plan/responsibilities.
 *
 * Escenario: periodo 28-sep-2026 a 27-oct-2026 (corte 28, ya cerrado), solo
 * categorias COMPARTIDAS y sin repartos, para que las definiciones de cada
 * pantalla coincidan por construccion. Se meten a proposito una apertura,
 * una liquidacion, un sub-gasto y una transferencia, que no son ni ingreso ni
 * gasto.
 */
@SpringBootTest
@DisplayName("Adversarial · valores y consolidados: todas las pantallas cuentan la misma plata")
class LosValoresCuadranEntrePantallasTest extends EscenarioFinanciero {

    static final LocalDate INI = LocalDate.of(2026, 9, 28);
    static final LocalDate FIN = LocalDate.of(2026, 10, 27);
    static final LocalDate REF = LocalDate.of(2026, 10, 10);
    static final LocalDate OCT = LocalDate.of(2026, 10, 1);

    Long salario, casa, mercado, ahorro;
    Long arriendo, servicios, ahorroProg;

    @BeforeEach
    void montar() throws Exception {
        salario = cat(ANDY, "Salario", CategoryType.INCOME, hogar);
        casa = cat(ANDY, "Casa", CategoryType.EXPENSE, hogar);
        mercado = cat(SAMY, "Mercado", CategoryType.EXPENSE, hogar);
        ahorro = cat(ANDY, "Ahorro", CategoryType.EXPENSE, hogar);

        LocalDate ancla = LocalDate.of(2026, 9, 1);
        Long salAndy = programar(ANDY, programado("Salario Andy", salario, "5000000", "MONTHLY", ancla, 1, null, null));
        Long salSamy = programar(SAMY, programado("Salario Samy", salario, "4000000", "MONTHLY", ancla, 15, null, null));
        arriendo = programar(ANDY, programado("Arriendo", casa, "2000000", "MONTHLY", ancla, 1, "PAYMENT", null));
        servicios = programar(SAMY, programado("Servicios", casa, "200000", "MONTHLY", ancla, 20, "PAYMENT", null));
        ahorroProg = programar(ANDY, programado("Ahorro Andy", ahorro, "1000000", "MONTHLY", ancla, 5, "SAVING", null));
        programar(SAMY, programado("Mercado", mercado, "800000", "MONTHLY", ancla, 1, "PROVISION", null));

        asignar(casa, hogar, ANDY, INI, FIN, "2200000");
        asignar(mercado, hogar, SAMY, INI, FIN, "800000");
        asignar(ahorro, hogar, ANDY, INI, FIN, "1000000");

        movimientos.save(base(ANDY, salario, "5000000", OCT, true, salAndy, OCT));
        movimientos.save(base(SAMY, salario, "4000000", OCT.withDayOfMonth(15), true, salSamy, OCT));
        Long pagoArriendo = movimientos.save(base(ANDY, casa, "2000000", OCT, true, arriendo, OCT)).getId();
        movimientos.save(base(SAMY, casa, "200000", OCT.withDayOfMonth(20), false, servicios, OCT));
        movimientos.save(base(ANDY, ahorro, "1000000", OCT.withDayOfMonth(5), true, ahorroProg, OCT));
        mov(SAMY, mercado, "650000", OCT.withDayOfMonth(12), true);
        mov(ANDY, mercado, "100000", OCT.withDayOfMonth(26), true);

        // Lo que NO es ingreso ni gasto.
        Movement apertura = base(ANDY, casa, "3000000", OCT.withDayOfMonth(2), true, null, null);
        apertura.setIsOpening(true);
        movimientos.save(apertura);
        Movement liquidacion = base(SAMY, casa, "50000", OCT.withDayOfMonth(22), true, null, null);
        liquidacion.setIsSettlement(true);
        movimientos.save(liquidacion);
        Movement hijo = base(ANDY, casa, "120000", OCT.withDayOfMonth(3), true, null, null);
        hijo.setParentMovementId(pagoArriendo);
        movimientos.save(hijo);
        Movement transferencia = base(ANDY, casa, "400000", OCT.withDayOfMonth(7), true, null, null);
        transferencia.setIsTransfer(true);
        movimientos.save(transferencia);
        Movement ingresoTransferido = base(ANDY, salario, "400000", OCT.withDayOfMonth(7), true, null, null);
        ingresoTransferido.setIsTransfer(true);
        movimientos.save(ingresoTransferido);
    }

    // ========================================================================

    @Test
    @DisplayName("el estado del hogar da las cifras esperadas a mano (gasto, ahorro, comprometido, libre)")
    void lasCifrasDelEstadoSonLasDeLaCuentaAMano() throws Exception {
        JsonNode e = hogarEn(ANDY, REF);
        assertThat(e.path("periodStart").asText()).isEqualTo("2026-09-28");
        assertThat(e.path("periodEnd").asText()).isEqualTo("2026-10-27");
        assertThat(t(e, "incomeReceived")).as("sin la transferencia de 400.000").isEqualByComparingTo("9000000");
        assertThat(t(e, "spent")).as("arriendo 2.000.000 + mercado 750.000; sin apertura, liquidacion, hijo ni transferencia")
                .isEqualByComparingTo("2750000");
        assertThat(t(e, "saved")).isEqualByComparingTo("1000000");
        assertThat(t(e, "committed")).isEqualByComparingTo("200000");
        assertThat(t(e, "provisionsLeft")).as("bolsa de mercado 800.000 - 750.000").isEqualByComparingTo("50000");
        assertThat(t(e, "freeToSpend")).isEqualByComparingTo("5000000");
        assertThat(t(e, "unassigned")).isEqualByComparingTo(t(e, "incomeExpected").subtract(t(e, "budgeted")));
    }

    @Test
    @DisplayName("estado vs /dashboard/summary: gasto+ahorro = totalExpense e ingresos = totalIncome")
    void estadoContraResumenDelPanel() throws Exception {
        JsonNode e = hogarEn(ANDY, REF);
        JsonNode d = ok(pedir(ANDY, "/v1/dashboard/summary", periodo(CORTE, REF)));
        assertThat(d.path("periodStart").asText()).isEqualTo(e.path("periodStart").asText());
        assertThat(num(d.path("totalExpense"))).isEqualByComparingTo(t(e, "spent").add(t(e, "saved")));
        assertThat(num(d.path("totalIncome"))).isEqualByComparingTo(t(e, "incomeReceived"));
    }

    @Test
    @DisplayName("estado vs /budget-allocation/summary: categoria por categoria, real, comprometido y asignado")
    void estadoContraResumenDelPresupuesto() throws Exception {
        JsonNode e = hogarEn(ANDY, REF);
        JsonNode s = ok(pedir(ANDY, "/v1/budget-allocation/summary", periodo(CORTE, REF)));
        assertThat(num(s.path("totalActual"))).isEqualByComparingTo(t(e, "spent").add(t(e, "saved")));
        assertThat(num(s.path("totalCommitted"))).isEqualByComparingTo(t(e, "committed"));
        assertThat(num(s.path("totalBudgeted"))).isEqualByComparingTo(t(e, "budgeted"));
        for (JsonNode c : s.path("categories")) {
            JsonNode ec = categoria(e, c.path("categoryId").asLong());
            assertThat(ec).as("la categoria " + c.path("categoryName").asText() + " falta en el estado").isNotNull();
            assertThat(num(c.path("actualAmount"))).as("real de " + c.path("categoryName").asText())
                    .isEqualByComparingTo(num(ec.path("actual")));
            assertThat(num(c.path("committedAmount"))).isEqualByComparingTo(num(ec.path("committed")));
            assertThat(num(c.path("allocatedAmount"))).isEqualByComparingTo(num(ec.path("allocated")));
            assertThat(num(c.path("availableAmount"))).isEqualByComparingTo(num(ec.path("available")));
        }
    }

    @Test
    @DisplayName("estado vs /dashboard/consolidated: lo compartido es todo, y transferencias no son gasto")
    void estadoContraConsolidado() throws Exception {
        JsonNode e = hogarEn(ANDY, REF);
        JsonNode c = ok(pedir(ANDY, "/v1/dashboard/consolidated", periodo(CORTE, REF)));
        assertThat(num(c.path("shared").path("expense"))).isEqualByComparingTo(t(e, "spent").add(t(e, "saved")));
        assertThat(num(c.path("shared").path("income"))).isEqualByComparingTo(t(e, "incomeReceived"));
        assertThat(num(c.path("total").path("expense"))).isEqualByComparingTo(t(e, "spent").add(t(e, "saved")));
        assertThat(num(c.path("budget").path("totalBudgeted"))).isEqualByComparingTo(t(e, "budgeted"));
    }

    @Test
    @DisplayName("estado vs /dashboard/by-category: la dona suma lo mismo que cada categoria")
    void estadoContraLaDona() throws Exception {
        JsonNode e = hogarEn(ANDY, REF);
        JsonNode dona = ok(pedir(ANDY, "/v1/dashboard/by-category", periodo(CORTE, REF)));
        for (JsonNode c : dona) {
            if (!"EXPENSE".equals(c.path("categoryType").asText())) continue;
            JsonNode ec = categoria(e, c.path("categoryId").asLong());
            assertThat(num(c.path("total"))).as("dona de " + c.path("categoryName").asText())
                    .isEqualByComparingTo(num(ec.path("actual")));
        }
    }

    @Test
    @DisplayName("estado vs /plan/responsibilities: lo que le toca a cada uno suma igual")
    void estadoContraResponsabilidadesDelPlan() throws Exception {
        JsonNode e = hogarEn(ANDY, REF);
        JsonNode r = ok(pedir(ANDY, "/v1/plan/responsibilities", periodo(CORTE, REF)));
        BigDecimal sumaEstado = BigDecimal.ZERO;
        for (JsonNode p : e.path("responsibilities")) sumaEstado = sumaEstado.add(num(p.path("total")));
        assertThat(num(r.path("sharedTotal"))).isEqualByComparingTo(sumaEstado).isEqualByComparingTo("4000000");
        for (JsonNode p : r.path("people")) {
            for (JsonNode q : e.path("responsibilities")) {
                if (q.path("userId").asLong() == p.path("userId").asLong()) {
                    assertThat(num(p.path("sharedTotal"))).as("responsable " + p.path("name").asText())
                            .isEqualByComparingTo(num(q.path("total")));
                }
            }
        }
    }

    @Test
    @DisplayName("sin nada privado, Andy y Samy ven exactamente las mismas cifras del hogar")
    void losDosVenLoMismo() throws Exception {
        JsonNode a = hogarEn(ANDY, REF);
        JsonNode s = hogarEn(SAMY, REF);
        assertThat(s.path("totals")).isEqualTo(a.path("totals"));
        assertThat(s.path("compliance")).isEqualTo(a.path("compliance"));
    }

    @Test
    @DisplayName("el historial de un periodo dice lo mismo que el estado de ese periodo")
    void elHistorialNoContradiceAlEstado() throws Exception {
        JsonNode e = hogarEn(ANDY, REF);
        JsonNode h = ok(pedir(ANDY, "/v1/estado/historial",
                "{\"budgetStartDay\":28,\"referenceDate\":\"" + REF + "\",\"periods\":2}"));
        JsonNode ultimo = h.path("periods").get(1);
        assertThat(ultimo.path("periodStart").asText()).isEqualTo("2026-09-28");
        assertThat(ultimo.path("totals")).isEqualTo(e.path("totals"));
        assertThat(h.path("periods").get(0).path("periodStart").asText()).isEqualTo("2026-08-28");
    }

    @Test
    @DisplayName("la suma de las personas (Andy + Samy) es el hogar en gasto de categorias comunes")
    void personasSumanElHogar() throws Exception {
        JsonNode h = hogarEn(ANDY, REF);
        JsonNode a = estado(ANDY, CORTE, REF, "PERSONA", ANDY);
        JsonNode s = estado(ANDY, CORTE, REF, "PERSONA", SAMY);
        assertThat(t(a, "spent").add(t(s, "spent"))).isEqualByComparingTo(t(h, "spent"));
        assertThat(t(a, "saved").add(t(s, "saved"))).isEqualByComparingTo(t(h, "saved"));
        assertThat(t(a, "committed").add(t(s, "committed"))).isEqualByComparingTo(t(h, "committed"));
        assertThat(t(a, "incomeReceived").add(t(s, "incomeReceived"))).isEqualByComparingTo(t(h, "incomeReceived"));
    }

    @Test
    @DisplayName("BUG · con corte 30 el estado calcula OTRO periodo que el panel, el presupuesto y el plan")
    void conCorte30TodasLasPantallasHablanDelMismoPeriodo() throws Exception {
        /* Esperado: corte 30, referencia 15-nov -> periodo 30-oct a 29-nov en
           todas las pantallas (los DTO del panel/presupuesto/plan aceptan 1..31).
           Real: EstadoDelPeriodoService.estado() (linea ~106) convierte en
           silencio cualquier corte > 28 en 1 y devuelve 1-nov a 30-nov. */
        LocalDate ref = LocalDate.of(2026, 11, 15);
        JsonNode d = ok(pedir(ANDY, "/v1/budget-allocation/summary", periodo(30, ref)));
        JsonNode p = ok(pedir(ANDY, "/v1/plan/responsibilities", periodo(30, ref)));
        JsonNode e = estado(ANDY, 30, ref, null, null);
        assertThat(d.path("periodStart").asText()).isEqualTo("2026-10-30");
        assertThat(p.path("periodStart").asText()).isEqualTo("2026-10-30");
        assertThat(e.path("periodStart").asText()).as("el estado debe usar el mismo periodo").isEqualTo("2026-10-30");
    }
}

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
 * /v1/estado/periodo (hogar, por persona, historial) y
 * /v1/plan/responsibilities.
 *
 * Escenario: periodo 28-sep-2026 a 27-oct-2026 (corte 28, ya cerrado), solo
 * categorias COMPARTIDAS y sin repartos, para que las definiciones de cada
 * pantalla coincidan por construccion. Se mete a proposito un sub-gasto,
 * que es detalle y no un gasto aparte.
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

        // El sub-gasto es detalle del padre, no otro gasto.
        Movement hijo = base(ANDY, casa, "120000", OCT.withDayOfMonth(3), true, null, null);
        hijo.setParentMovementId(pagoArriendo);
        movimientos.save(hijo);
    }

    // ========================================================================

    @Test
    @DisplayName("el estado del hogar da las cifras esperadas a mano (gasto, ahorro, comprometido, libre)")
    void lasCifrasDelEstadoSonLasDeLaCuentaAMano() throws Exception {
        JsonNode e = hogarEn(ANDY, REF);
        assertThat(e.path("periodStart").asText()).isEqualTo("2026-09-28");
        assertThat(e.path("periodEnd").asText()).isEqualTo("2026-10-27");
        assertThat(t(e, "incomeReceived")).isEqualByComparingTo("9000000");
        assertThat(t(e, "spent")).as("arriendo 2.000.000 + mercado 750.000; sin el hijo")
                .isEqualByComparingTo("2750000");
        assertThat(t(e, "saved")).isEqualByComparingTo("1000000");
        assertThat(t(e, "committed")).isEqualByComparingTo("200000");
        assertThat(t(e, "provisionsLeft")).as("bolsa de mercado 800.000 - 750.000").isEqualByComparingTo("50000");
        assertThat(t(e, "freeToSpend")).isEqualByComparingTo("5000000");
        assertThat(t(e, "unassigned")).isEqualByComparingTo(t(e, "incomeExpected").subtract(t(e, "budgeted")));
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
    @DisplayName("BUG · con corte 30 el estado calcula el mismo periodo que el plan")
    void conCorte30TodasLasPantallasHablanDelMismoPeriodo() throws Exception {
        /* Esperado: corte 30, referencia 15-nov -> periodo 30-oct a 29-nov en
           todas las pantallas (los DTO del panel/presupuesto/plan aceptan 1..31).
           Real: EstadoDelPeriodoService.estado() (linea ~106) convierte en
           silencio cualquier corte > 28 en 1 y devuelve 1-nov a 30-nov. */
        LocalDate ref = LocalDate.of(2026, 11, 15);
        JsonNode p = ok(pedir(ANDY, "/v1/plan/responsibilities", periodo(30, ref)));
        JsonNode e = estado(ANDY, 30, ref, null, null);
        assertThat(p.path("periodStart").asText()).isEqualTo("2026-10-30");
        assertThat(e.path("periodStart").asText()).as("el estado debe usar el mismo periodo").isEqualTo("2026-10-30");
    }
}

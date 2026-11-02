package com.ohchurus.budget.adversarial;

import com.fasterxml.jackson.databind.JsonNode;
import com.ohchurus.budget.enums.CategoryType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PLANIFICACION FUTURA: periodos que aun no llegan (2027), facturas
 * bimestrales, cuotas que terminan exactamente tras N pagos y deudas que
 * empiezan a mitad de periodo. Corte 28.
 */
@SpringBootTest
@DisplayName("Adversarial · planificacion futura: bimestrales, cuotas que terminan y deudas a mitad de periodo")
class LaPlanificacionFuturaTest extends EscenarioFinanciero {

    Long casa, deudas;

    @BeforeEach
    void montar() {
        casa = cat(ANDY, "Casa", CategoryType.EXPENSE, hogar);
        deudas = cat(ANDY, "Deudas", CategoryType.EXPENSE, hogar);
    }

    /** periodStart -> fechas del programado en ese periodo, para N periodos desde ref. */
    private Map<String, List<String>> porPeriodo(Long programado, LocalDate desde, int n) throws Exception {
        Map<String, List<String>> out = new LinkedHashMap<>();
        LocalDate ref = desde;
        for (int i = 0; i < n; i++) {
            JsonNode e = hogarEn(ANDY, ref);
            List<String> f = new ArrayList<>();
            for (JsonNode it : itemsDe(e, programado)) f.add(it.path("date").asText());
            out.put(e.path("periodStart").asText(), f);
            ref = LocalDate.parse(e.path("periodEnd").asText()).plusDays(1);
        }
        return out;
    }

    @Test
    @DisplayName("el agua bimestral (dia 10) sale un periodo si y otro no durante un ano")
    void bimestralCadaDosPeriodos() throws Exception {
        Long agua = programar(ANDY, programado("Agua", casa, "150000", "BIMONTHLY", LocalDate.of(2027, 1, 10), 10, "PAYMENT", null));
        Map<String, List<String>> m = porPeriodo(agua, LocalDate.of(2027, 1, 10), 12);
        int i = 0;
        for (Map.Entry<String, List<String>> e : m.entrySet()) {
            assertThat(e.getValue()).as("periodo " + e.getKey()).hasSize(i % 2 == 0 ? 1 : 0);
            i++;
        }
        assertThat(m.get("2026-12-28")).containsExactly("2027-01-10");
        assertThat(m.get("2027-02-28")).containsExactly("2027-03-10");
    }

    @Test
    @DisplayName("una cuota a 3 meses que arranca a mitad de periodo: 3 cuotas, la ultima aparece y luego nada")
    void cuotasTerminanExactamente() throws Exception {
        Long tv = programar(ANDY, programado("Televisor", deudas, "400000", "MONTHLY", LocalDate.of(2027, 1, 15), 15,
                "PAYMENT", "\"durationMonths\":3"));
        Map<String, List<String>> m = porPeriodo(tv, LocalDate.of(2026, 12, 30), 6);
        List<String> todas = new ArrayList<>();
        m.values().forEach(todas::addAll);
        assertThat(todas).containsExactly("2027-01-15", "2027-02-15", "2027-03-15");
        JsonNode prog = ok(pedir(ANDY, "/v1/scheduled/get/" + tv, "{}"));
        assertThat(prog.path("endDate").asText()).isEqualTo("2027-04-14");
    }

    @Test
    @DisplayName("una deuda que se toma el 20 y se paga el 5: la primera cuota es el 5 del mes siguiente")
    void deudaQueEmpiezaAMitadDePeriodo() throws Exception {
        Long d = programar(ANDY, programado("Prestamo", deudas, "250000", "MONTHLY", LocalDate.of(2027, 1, 20), 5,
                "PAYMENT", "\"durationMonths\":3"));
        Map<String, List<String>> m = porPeriodo(d, LocalDate.of(2027, 1, 10), 6);
        List<String> todas = new ArrayList<>();
        m.values().forEach(todas::addAll);
        assertThat(todas).as("nunca una cuota anterior al desembolso").containsExactly("2027-02-05", "2027-03-05", "2027-04-05");
    }

    @Test
    @DisplayName("cuota el 31 a 2 meses desde el 31-ene: 31-ene y 28-feb, sin una tercera")
    void cuotaDia31EnMesesCortos() throws Exception {
        Long d = programar(ANDY, programado("Credito 31", deudas, "100000", "MONTHLY", LocalDate.of(2027, 1, 31), 31,
                "PAYMENT", "\"durationMonths\":2"));
        JsonNode cal = ok(pedir(ANDY, "/v1/plan/calendar", "{\"from\":\"2027-01-01\",\"to\":\"2027-06-30\"}"));
        List<String> f = new ArrayList<>();
        for (JsonNode i : cal.path("items")) if (i.path("scheduledMovementId").asLong() == d) f.add(i.path("date").asText());
        assertThat(f).containsExactly("2027-01-31", "2027-02-28");
    }

    @Test
    @DisplayName("una cuota bimestral 'a 4 meses' son 2 cuotas, y el programado dice que le quedan 2")
    void bimestralConDuracion() throws Exception {
        Long d = programar(ANDY, programado("Seguro", deudas, "300000", "BIMONTHLY", LocalDate.of(2027, 1, 10), 10,
                "PAYMENT", "\"durationMonths\":4"));
        Map<String, List<String>> m = porPeriodo(d, LocalDate.of(2027, 1, 10), 6);
        List<String> todas = new ArrayList<>();
        m.values().forEach(todas::addAll);
        assertThat(todas).containsExactly("2027-01-10", "2027-03-10");
        JsonNode prog = ok(pedir(ANDY, "/v1/scheduled/get/" + d, "{}"));
        assertThat(prog.path("remainingOccurrences").asInt()).isEqualTo(2);
    }

    @Test
    @DisplayName("en un periodo futuro todo es PREVISTO: nada vencido, nada pagado, nada comprometido")
    void unPeriodoFuturoNoInventaEstados() throws Exception {
        programar(ANDY, programado("Arriendo", casa, "2000000", "MONTHLY", LocalDate.of(2026, 9, 1), 1, "PAYMENT", null));
        JsonNode e = hogarEn(ANDY, LocalDate.of(2027, 5, 10));
        assertThat(e.path("closed").asBoolean()).isFalse();
        assertThat(e.path("compliance").path("billsOverdue").asInt()).isZero();
        assertThat(e.path("compliance").path("billsPaid").asInt()).isZero();
        assertThat(t(e, "committed")).isEqualByComparingTo("0");
        JsonNode c = categoria(e, casa);
        assertThat(num(c.path("allocated"))).as("lo planeado es el presupuesto").isEqualByComparingTo("2000000");
        assertThat(c.path("items").get(0).path("status").asText()).isEqualTo("PREVISTO");
    }

    @Test
    @DisplayName("BUG · una duracion de 0 o negativa se acepta y deja un programado que no genera nada")
    void duracionInvalidaSeRechaza() throws Exception {
        /* Esperado: 400 (una deuda "a -3 meses" no existe). Real:
           ScheduledMovementSaveDTO.durationMonths no tiene @Min y
           calculateEndDate pone endDate antes de startDate: el programado
           se guarda "activo" y en silencio no produce ninguna cuota. */
        for (String dur : new String[]{"0", "-3"}) {
            JsonNode r = pedir(ANDY, "/v1/scheduled/save", programado("Mal", deudas, "100000", "MONTHLY",
                    LocalDate.of(2027, 1, 10), 10, "PAYMENT", "\"durationMonths\":" + dur));
            assertThat(r.path("correct").asBoolean()).as("durationMonths=" + dur + " -> " + r).isFalse();
        }
    }
}

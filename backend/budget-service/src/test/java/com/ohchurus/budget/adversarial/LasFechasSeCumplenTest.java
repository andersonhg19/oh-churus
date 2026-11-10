package com.ohchurus.budget.adversarial;

import com.fasterxml.jackson.databind.JsonNode;
import com.ohchurus.budget.entity.Movement;
import com.ohchurus.budget.enums.CategoryType;
import com.ohchurus.budget.util.PeriodUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * CUMPLIMIENTO DE FECHAS: el corte del 28, febrero, el dia 31, los fines de
 * semana y lo vencido. Los escenarios que dependen de "hoy" calculan sus
 * fechas a partir de LocalDate.now() para ser deterministas cualquier dia.
 */
@SpringBootTest
@DisplayName("Adversarial · fechas: corte 28, meses cortos, dia 31, fines de semana y vencidos")
class LasFechasSeCumplenTest extends EscenarioFinanciero {

    Long casa;

    @BeforeEach
    void montar() {
        casa = cat(ANDY, "Casa", CategoryType.EXPENSE, hogar);
    }

    @Test
    @DisplayName("PeriodUtils: con cualquier corte 1..31, 2026-2029, los periodos son contiguos y sin huecos")
    void losPeriodosSonContiguos() {
        for (int corte = 1; corte <= 31; corte++) {
            for (LocalDate d = LocalDate.of(2026, 1, 1); d.isBefore(LocalDate.of(2030, 1, 1)); d = d.plusDays(1)) {
                LocalDate ini = PeriodUtils.getStartOfPeriod(corte, d);
                LocalDate fin = PeriodUtils.getEndOfPeriod(corte, ini);
                assertThat(d).as("corte " + corte + " dia " + d).isBetween(ini, fin);
                assertThat(PeriodUtils.getStartOfPeriod(corte, fin.plusDays(1))).as("siguiente de " + ini + " corte " + corte)
                        .isEqualTo(fin.plusDays(1));
                long dias = ChronoUnit.DAYS.between(ini, fin) + 1;
                assertThat(dias).as("duracion corte " + corte + " desde " + ini).isBetween(28L, 31L);
            }
        }
    }

    @Test
    @DisplayName("corte 28: el 27 es del periodo anterior y el 28 abre el nuevo")
    void elDia28AbreElPeriodo() throws Exception {
        mov(ANDY, casa, "27000", LocalDate.of(2026, 10, 27), true);
        mov(ANDY, casa, "28000", LocalDate.of(2026, 10, 28), true);
        JsonNode antes = hogarEn(ANDY, LocalDate.of(2026, 10, 27));
        JsonNode despues = hogarEn(ANDY, LocalDate.of(2026, 10, 28));
        assertThat(antes.path("periodEnd").asText()).isEqualTo("2026-10-27");
        assertThat(t(antes, "spent")).isEqualByComparingTo("27000");
        assertThat(despues.path("periodStart").asText()).isEqualTo("2026-10-28");
        assertThat(t(despues, "spent")).isEqualByComparingTo("28000");
    }

    @Test
    @DisplayName("febrero con corte 28: 28-ene a 27-feb y 28-feb a 27-mar, tambien en bisiesto")
    void febreroConCorte28() throws Exception {
        assertThat(hogarEn(ANDY, LocalDate.of(2027, 2, 27)).path("periodStart").asText()).isEqualTo("2027-01-28");
        assertThat(hogarEn(ANDY, LocalDate.of(2027, 2, 28)).path("periodEnd").asText()).isEqualTo("2027-03-27");
        assertThat(hogarEn(ANDY, LocalDate.of(2028, 2, 29)).path("periodStart").asText()).isEqualTo("2028-02-28");
    }

    @Test
    @DisplayName("un pago el dia 31: 31-ene, 28-feb, 31-mar, 30-abr (vuelve al 31 tras febrero)")
    void elDia31() throws Exception {
        Long p = programar(ANDY, programado("Cuota 31", casa, "100000", "MONTHLY",
                LocalDate.of(2027, 1, 1), 31, "PAYMENT", null));
        JsonNode cal = ok(pedir(ANDY, "/v1/plan/calendar", "{\"from\":\"2027-01-01\",\"to\":\"2027-04-30\"}"));
        List<String> fechas = new ArrayList<>();
        for (JsonNode i : cal.path("items")) if (i.path("scheduledMovementId").asLong() == p) fechas.add(i.path("date").asText());
        assertThat(fechas).containsExactly("2027-01-31", "2027-02-28", "2027-03-31", "2027-04-30");
    }

    @Test
    @DisplayName("lo de ayer sin pagar esta VENCIDO y cuenta en cumplimiento; lo de hoy aun no")
    void loVencidoSeVe() throws Exception {
        LocalDate h = hoy();
        LocalDate ayer = h.minusDays(1);
        int corte = h.getDayOfMonth() == 28 ? 1 : 28;
        assumeTrue(!inicio(corte, h).isAfter(ayer), "ayer y hoy en el mismo periodo");
        Long vencido = programar(ANDY, programado("Factura de ayer", casa, "90000", "MONTHLY", ayer,
                ayer.getDayOfMonth(), "PAYMENT", null));
        Long deHoy = programar(ANDY, programado("Factura de hoy", casa, "60000", "MONTHLY", h,
                h.getDayOfMonth(), "PAYMENT", null));
        generar(ANDY, corte);
        JsonNode e = estado(ANDY, corte, h, null, null);
        List<JsonNode> a = itemsDe(e, vencido);
        List<JsonNode> b = itemsDe(e, deHoy);
        assertThat(a).hasSize(1);
        assertThat(a.get(0).path("status").asText()).isEqualTo("VENCIDO");
        assertThat(b).hasSize(1);
        assertThat(b.get(0).path("status").asText()).isEqualTo("PENDIENTE");
        assertThat(e.path("compliance").path("billsOverdue").asInt()).isEqualTo(1);
        assertThat(t(e, "committed")).as("los dos siguen comprometidos").isEqualByComparingTo("150000");
    }

    @Test
    @DisplayName("BUG · un pago movido al viernes por 'dia habil anterior' nunca tiene pendiente antes de vencer")
    void elPagoAdelantadoPorFinDeSemanaSeGeneraATiempo() throws Exception {
        /* Se busca un corte D tal que el dia D del mes siguiente (inicio del
           proximo periodo) sea SABADO: el pago de ese dia se adelanta al
           viernes, que es el ULTIMO dia del periodo actual. Con corte 28 pasa
           en noviembre de 2026 (28-nov es sabado).
           Esperado: generate-pending crea su pendiente (el plan ya lo muestra
           en este periodo y vence antes del siguiente).
           Real: ScheduledMovementServiceImpl.generatePending usa
           ocurrenciasHasta(finDeLaVentana), que corta por la fecha CANONICA
           (sabado > fin) y no la genera; el siguiente periodo la crea ya
           VENCIDA. PlanFinancieroService.fechas enumera hasta +3 dias y si la
           muestra: plan y generacion no coinciden. */
        LocalDate h = hoy();
        int corte = -1;
        for (int d = 1; d <= 28 && corte < 0; d++) {
            LocalDate f = fin(d, inicio(d, h));
            if (f.plusDays(1).getDayOfWeek() == DayOfWeek.SATURDAY && f.isAfter(h)) corte = d;
        }
        assumeTrue(corte > 0, "no hay corte con fin de periodo en viernes tras hoy");
        LocalDate ini = inicio(corte, h);
        LocalDate viernes = fin(corte, ini);
        Long p = programar(ANDY, programado("Arriendo", casa, "2300000", "MONTHLY", ini, corte, "PAYMENT",
                "\"weekendPolicy\":\"PREVIOUS_BUSINESS_DAY\""));
        generar(ANDY, corte);

        JsonNode e = estado(ANDY, corte, h, null, null);
        boolean planLoMuestra = itemsDe(e, p).stream().anyMatch(i -> i.path("date").asText().equals(viernes.toString()));
        assertThat(planLoMuestra).as("el plan del periodo muestra el pago del " + viernes).isTrue();
        boolean hayPendiente = pendientesDe(p).stream().anyMatch(m -> viernes.equals(m.getDate()));
        assertThat(hayPendiente).as("debe existir el pendiente del " + viernes + " (corte " + corte + ") para poder pagarlo a tiempo")
                .isTrue();
    }

    @org.junit.jupiter.api.Disabled("DECISION PENDIENTE del hogar: un pago que se adelanta por fin de semana "
            + "cuenta en el periodo de su fecha real (flujo de caja, como hoy) o en el de su fecha original "
            + "(presupuesto del mes). Hoy ningun programado de produccion usa politica de fin de semana.")
    @Test
    @DisplayName("HALLAZGO · con politica de fin de semana un pago mensual sale 2 veces en un periodo y 0 en el otro")
    void unPagoMensualSaleUnaVezPorPeriodo() throws Exception {
        /* Corte 28 y arriendo el 28 con "dia habil anterior": el 28-nov-2026
           es sabado y se paga el 27-nov, asi que el periodo 28-oct..27-nov
           tiene DOS arriendos (28-oct y 27-nov) y 28-nov..27-dic ninguno.
           Igual con "dia habil siguiente" el 26-dic-2026 (sabado). Esperado
           (presupuesto por obligacion): una vez por periodo. Real: el
           presupuesto planeado (allocated sin asignacion) se duplica en un
           periodo y queda en cero en el otro. Decision de dominio a validar. */
        Long ant = programar(ANDY, programado("Arriendo 28", casa, "2000000", "MONTHLY",
                LocalDate.of(2026, 10, 28), 28, "PAYMENT", "\"weekendPolicy\":\"PREVIOUS_BUSINESS_DAY\""));
        Long sig = programar(ANDY, programado("Admin 26", casa, "300000", "MONTHLY",
                LocalDate.of(2026, 10, 28), 26, "PAYMENT", "\"weekendPolicy\":\"NEXT_BUSINESS_DAY\""));
        StringBuilder malos = new StringBuilder();
        for (LocalDate ref = LocalDate.of(2026, 11, 10); ref.isBefore(LocalDate.of(2027, 6, 1)); ref = ref.plusMonths(1)) {
            JsonNode e = hogarEn(ANDY, ref);
            int a = itemsDe(e, ant).size(), s = itemsDe(e, sig).size();
            if (a != 1 || s != 1) malos.append(e.path("periodStart").asText()).append(": arriendo=").append(a)
                    .append(" admin=").append(s).append("; ");
        }
        assertThat(malos.toString()).as("periodos con el pago mensual repetido o ausente").isEmpty();
    }

    @Test
    @DisplayName("generar dos veces seguidas no duplica y no adelanta pendientes del periodo siguiente")
    void generarDosVeces() throws Exception {
        LocalDate h = hoy();
        LocalDate ini = inicio(CORTE, h);
        Long p = programar(ANDY, programado("Internet", casa, "120000", "MONTHLY", ini, 27, "PAYMENT", null));
        generar(ANDY, CORTE);
        generar(SAMY, CORTE);
        generar(ANDY, CORTE);
        List<Movement> vivos = vivosDe(p);
        assertThat(vivos).hasSize(1);
        assertThat(vivos.get(0).getDate()).isBetween(ini, fin(CORTE, ini));
    }
}

package com.ohchurus.budget.adversarial;

import com.fasterxml.jackson.databind.JsonNode;
import com.ohchurus.budget.enums.CategoryType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PARAMETROS INVALIDOS: importes nulos, cero o negativos; dias de corte
 * fuera de rango; periodos absurdos; fechas imposibles. Lo minimo exigible:
 * nunca un 500 sin contrato, nunca un dato a medio escribir, y el mismo
 * criterio en todos los endpoints.
 */
@SpringBootTest
@DisplayName("Adversarial · parametros invalidos: importes, cortes, periodos y fechas absurdas")
class ParametrosInvalidosTest extends EscenarioFinanciero {

    Long casa;

    @BeforeEach
    void montar() {
        casa = cat(ANDY, "Casa", CategoryType.EXPENSE, hogar);
    }

    @Test
    @DisplayName("un movimiento con importe 0, negativo o nulo se rechaza y no se guarda")
    void importesInvalidos() throws Exception {
        for (String monto : new String[]{"0", "-1", "null", "\"abc\""}) {
            JsonNode r = pedir(ANDY, "/v1/movements/save", "{\"categoryId\":" + casa
                    + ",\"date\":\"2026-10-10\",\"amount\":" + monto + "}");
            assertThat(r.path("correct").asBoolean()).as("amount=" + monto).isFalse();
        }
        assertThat(movimientos.count()).isZero();
    }

    @Test
    @DisplayName("un programado con importe negativo se rechaza; confirmar con 0 tambien")
    void programadoYConfirmacionInvalidos() throws Exception {
        assertThat(pedir(ANDY, "/v1/scheduled/save", programado("X", casa, "-100", "MONTHLY",
                LocalDate.of(2026, 10, 1), 1, null, null)).path("correct").asBoolean()).isFalse();
        Long p = mov(ANDY, casa, "1000", LocalDate.of(2026, 10, 10), false);
        assertThat(pedir(ANDY, "/v1/movements/confirm/" + p, "{\"amount\":0}").path("correct").asBoolean()).isFalse();
        assertThat(movimientos.findById(p).get().getConfirmed()).isFalse();
    }

    @Test
    @DisplayName("presupuesto sin importe y reasignar sin responsable se rechazan")
    void obligatoriosAusentes() throws Exception {
        assertThat(pedir(ANDY, "/v1/budget-allocation/save", "{\"categoryId\":" + casa + "}")
                .path("correct").asBoolean()).isFalse();
        Long p = mov(ANDY, casa, "1000", LocalDate.of(2026, 10, 10), false);
        assertThat(pedir(ANDY, "/v1/movements/reassign/" + p, "{}").path("correct").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("el estado sin dia de corte usa el 1 (como el resto)")
    void corteNuloEsUno() throws Exception {
        JsonNode r = ok(pedir(ANDY, "/v1/estado/periodo", "{\"referenceDate\":\"2026-10-10\"}"));
        assertThat(r.path("periodStart").asText()).isEqualTo("2026-10-01");
    }

    @Test
    @DisplayName("BUG · el estado acepta corte 0 o 45 en silencio; el resto de endpoints los rechaza")
    void corteFueraDeRangoSeRechaza() throws Exception {
        /* Esperado: el mismo criterio que /dashboard, /budget-allocation y
           /plan (@Min(1) @Max(31) -> 400). Real: EstadoRequestDTO no valida y
           EstadoDelPeriodoService lo cambia a 1 sin avisar. */
        for (int corte : new int[]{0, 45}) {
            assertThat(pedir(ANDY, "/v1/dashboard/summary", periodo(corte, LocalDate.of(2026, 10, 10)))
                    .path("correct").asBoolean()).as("dashboard corte " + corte).isFalse();
            assertThat(pedir(ANDY, "/v1/estado/periodo", periodo(corte, LocalDate.of(2026, 10, 10)))
                    .path("correct").asBoolean()).as("estado corte " + corte).isFalse();
        }
    }

    @Test
    @DisplayName("BUG · /dashboard/summary con budgetStartDay null explicito revienta en vez de usar el 1")
    void corteNuloEnElPanel() throws Exception {
        /* Esperado: como AsignacionPeriodoDTO, null = 1. Real:
           DashboardRequestDTO no tiene el getter defensivo; PeriodUtils
           recibe null y la respuesta es el error 500 generico. */
        JsonNode r = pedir(ANDY, "/v1/dashboard/summary", "{\"budgetStartDay\":null,\"referenceDate\":\"2026-10-10\"}");
        assertThat(r.path("correct").asBoolean()).as(r.toString()).isTrue();
    }

    @Test
    @DisplayName("el historial acota los periodos: 0 o negativo -> 1, 1000 -> 12")
    void periodosAbsurdos() throws Exception {
        for (Object[] c : new Object[][]{{0, 1}, {-5, 1}, {1000, 12}}) {
            JsonNode r = ok(pedir(ANDY, "/v1/estado/historial",
                    "{\"budgetStartDay\":28,\"referenceDate\":\"2026-10-10\",\"periods\":" + c[0] + "}"));
            assertThat(r.path("periods").size()).as("periods=" + c[0]).isEqualTo((int) c[1]);
        }
    }

    @Test
    @DisplayName("BUG · un alcance inventado ('FOO') se trata como PERSONA en vez de rechazarse")
    void alcanceInventado() throws Exception {
        /* Esperado: 400 (solo HOGAR o PERSONA). Real: Alcance.de (linea ~91)
           trata cualquier texto distinto de HOGAR como PERSONA de quien
           pregunta: un cliente con un typo ("HOGARES") recibe otras cifras. */
        JsonNode r = pedir(ANDY, "/v1/estado/periodo", "{\"budgetStartDay\":28,\"scope\":\"HOGARES\"}");
        assertThat(r.path("correct").asBoolean()).as(r.toString()).isFalse();
    }

    @Test
    @DisplayName("fechas imposibles o extremas: respuesta con contrato, nunca un cuelgue")
    void fechasExtremas() throws Exception {
        programar(ANDY, programado("Diario", casa, "1000", "DAILY", LocalDate.of(2026, 1, 1), null, null, null));
        JsonNode mala = pedir(ANDY, "/v1/estado/periodo", "{\"budgetStartDay\":28,\"referenceDate\":\"2026-13-45\"}");
        assertThat(mala.path("correct").asBoolean()).isFalse();
        for (String f : new String[]{"0001-01-01", "9999-12-31"}) {
            JsonNode r = pedir(ANDY, "/v1/estado/periodo", "{\"budgetStartDay\":28,\"referenceDate\":\"" + f + "\"}");
            assertThat(r.has("correct")).as("referenceDate=" + f + " -> " + r).isTrue();
            JsonNode h = pedir(ANDY, "/v1/estado/historial", "{\"budgetStartDay\":28,\"referenceDate\":\"" + f + "\",\"periods\":12}");
            assertThat(h.has("correct")).as("historial " + f).isTrue();
        }
    }

    @Test
    @DisplayName("un diario de 1.000 en un periodo de 30 dias suma 30.000, no 1.000")
    void unDiarioSumaCadaDia() throws Exception {
        programar(ANDY, programado("Diario", casa, "1000", "DAILY", LocalDate.of(2026, 1, 1), null, null, null));
        JsonNode e = hogarEn(ANDY, LocalDate.of(2027, 5, 10)); // 28-abr..27-may = 30 dias
        assertThat(num(categoria(e, casa).path("plannedInItems"))).isEqualByComparingTo("30000");
    }
}

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
 * PRESUPUESTOS VS PAGOS, AHORRO Y METAS, BOLSAS EXCEDIDAS, y "lo libre para
 * gastar". Periodo cerrado 28-sep-2026..27-oct-2026 (corte 28) salvo donde se
 * necesita el periodo en curso.
 */
@SpringBootTest
@DisplayName("Adversarial · presupuesto, ahorro, bolsas y lo libre para gastar")
class PresupuestoAhorroYBolsasTest extends EscenarioFinanciero {

    static final LocalDate INI = LocalDate.of(2026, 9, 28);
    static final LocalDate FIN = LocalDate.of(2026, 10, 27);
    static final LocalDate REF = LocalDate.of(2026, 10, 10);
    static final LocalDate ANCLA = LocalDate.of(2026, 9, 1);

    Long salario, casa, mercado, ahorro, mascotas;

    @BeforeEach
    void montar() {
        salario = cat(ANDY, "Salario", CategoryType.INCOME, hogar);
        casa = cat(ANDY, "Casa", CategoryType.EXPENSE, hogar);
        mercado = cat(SAMY, "Mercado", CategoryType.EXPENSE, hogar);
        ahorro = cat(ANDY, "Ahorro", CategoryType.EXPENSE, hogar);
        mascotas = cat(SAMY, "Mascotas", CategoryType.EXPENSE, hogar);
        mov(ANDY, salario, "3000000", LocalDate.of(2026, 10, 1), true);
    }

    // ---------------------------------------------------------------- ahorro

    @Test
    @DisplayName("ahorrar 400.000 de una meta de 1.000.000 en un mes cerrado: META_INCUMPLIDA, faltan 600.000")
    void metaParcialIncumplida() throws Exception {
        programar(ANDY, programado("Ahorro", ahorro, "1000000", "MONTHLY", ANCLA, 5, "SAVING", null));
        mov(ANDY, ahorro, "400000", LocalDate.of(2026, 10, 5), true);
        JsonNode e = hogarEn(ANDY, REF);
        JsonNode a = categoria(e, ahorro);
        assertThat(a.path("status").asText()).isEqualTo("META_INCUMPLIDA");
        assertThat(num(a.path("difference"))).isEqualByComparingTo("600000");
        assertThat(t(e, "saved")).isEqualByComparingTo("400000");
        assertThat(t(e, "spent")).as("ahorrar no es gastar").isEqualByComparingTo("0");
        assertThat(e.path("compliance").path("savingsGoalsMissed").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("ahorrar mas de la meta la cumple y no cuenta como gasto excedido")
    void sobreAhorroCumple() throws Exception {
        programar(ANDY, programado("Ahorro", ahorro, "1000000", "MONTHLY", ANCLA, 5, "SAVING", null));
        mov(ANDY, ahorro, "1300000", LocalDate.of(2026, 10, 5), true);
        JsonNode e = hogarEn(ANDY, REF);
        assertThat(categoria(e, ahorro).path("status").asText()).isEqualTo("META_CUMPLIDA");
        assertThat(e.path("compliance").path("categoriesOver").asInt()).isZero();
        assertThat(t(e, "savingsRatePct").doubleValue()).isEqualTo(43.3);
    }

    @Test
    @DisplayName("en el periodo en curso una meta sin ahorro aun esta EN_CURSO, no incumplida")
    void metaEnCurso() throws Exception {
        LocalDate h = hoy();
        programar(ANDY, programado("Ahorro", ahorro, "1000000", "MONTHLY", inicio(CORTE, h).minusMonths(2), 27, "SAVING", null));
        JsonNode e = hogarEn(ANDY, h);
        assertThat(categoria(e, ahorro).path("status").asText()).isEqualTo("EN_CURSO");
    }

    @Test
    @DisplayName("BUG · si la meta del mes se fija en 0 (no ahorrar este mes), cerrar el mes no la marca incumplida")
    void metaCeroNoSeIncumple() throws Exception {
        /* Esperado: asignacion explicita de 0 en Ahorro = "este mes no se
           ahorra": ni cumplida ni incumplida, y no suma a savingsGoalsMissed.
           Real: EstadoDelPeriodoService.calcular (linea ~331) exige
           asignado > 0 para cumplir, y cualquier otro caso en mes cerrado es
           META_INCUMPLIDA. */
        programar(ANDY, programado("Ahorro", ahorro, "1000000", "MONTHLY", ANCLA, 5, "SAVING", null));
        asignar(ahorro, hogar, ANDY, INI, FIN, "0");
        JsonNode e = hogarEn(ANDY, REF);
        assertThat(categoria(e, ahorro).path("status").asText()).isNotEqualTo("META_INCUMPLIDA");
        assertThat(e.path("compliance").path("savingsGoalsMissed").asInt()).isZero();
    }

    // ---------------------------------------------------------------- bolsas

    @Test
    @DisplayName("bolsa de mercado de 500.000 con 650.000 gastados: EXCEDIDO, bolsa en 0 (nunca negativa)")
    void bolsaExcedida() throws Exception {
        programar(SAMY, programado("Mercado", mercado, "500000", "MONTHLY", ANCLA, 1, "PROVISION", null));
        mov(SAMY, mercado, "650000", LocalDate.of(2026, 10, 12), true);
        JsonNode e = hogarEn(ANDY, REF);
        JsonNode m = categoria(e, mercado);
        assertThat(m.path("status").asText()).isEqualTo("EXCEDIDO");
        assertThat(num(m.path("provisionLeft"))).isEqualByComparingTo("0");
        assertThat(num(m.path("difference"))).isEqualByComparingTo("-150000");
        assertThat(t(e, "freeToSpend")).isEqualByComparingTo("2350000");
        assertThat(t(e, "committed")).as("una bolsa no genera pendiente").isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("bolsa y factura en la misma categoria: la factura pagada no se come la bolsa")
    void bolsaYFacturaJuntas() throws Exception {
        programar(SAMY, programado("Mascotas", mascotas, "500000", "MONTHLY", ANCLA, 1, "PROVISION", null));
        Long seguro = programar(SAMY, programado("Seguro mascota", mascotas, "100000", "MONTHLY", ANCLA, 3, "PAYMENT", null));
        movimientos.save(base(SAMY, mascotas, "100000", LocalDate.of(2026, 10, 3), true, seguro, LocalDate.of(2026, 10, 1)));
        mov(SAMY, mascotas, "300000", LocalDate.of(2026, 10, 15), true);
        JsonNode m = categoria(hogarEn(ANDY, REF), mascotas);
        assertThat(num(m.path("allocated"))).isEqualByComparingTo("600000");
        assertThat(num(m.path("actual"))).isEqualByComparingTo("400000");
        assertThat(num(m.path("provisionLeft"))).isEqualByComparingTo("200000");
        assertThat(m.path("status").asText()).isEqualTo("DENTRO");
    }

    @Test
    @DisplayName("BUG · una compra pendiente dentro de la bolsa se resta dos veces de lo libre")
    void compraPendienteEnLaBolsaNoSeRestaDosVeces() throws Exception {
        /* Bolsa de mercado 800.000; Samy anota una compra de 300.000 aun sin
           confirmar. Esperado: lo libre es 3.000.000 - 800.000 = 2.200.000 (la
           compra sale DE la bolsa). Real: committed=300.000 y provisionLeft=
           800.000 (solo descuenta lo confirmado), libre = 1.900.000.
           EstadoDelPeriodoService.calcular lineas ~344-345 y ~378. */
        programar(SAMY, programado("Mercado", mercado, "800000", "MONTHLY", ANCLA, 1, "PROVISION", null));
        mov(SAMY, mercado, "300000", LocalDate.of(2026, 10, 20), false);
        JsonNode e = hogarEn(ANDY, REF);
        assertThat(t(e, "freeToSpend")).isEqualByComparingTo("2200000");
    }

    // ---------------------------------------------------------- lo libre

    @Test
    @DisplayName("BUG · lo libre para gastar cambia segun si la pareja ya abrio la app (pendiente generado o no)")
    void loLibreNoDependeDeSiSeGeneroElPendiente() throws Exception {
        /* Periodo en curso. Samy tiene una factura comun de 1.000.000 al final
           del periodo. Mientras Samy no refresca, la factura es PREVISTO y no
           resta; cuando refresca pasa a PENDIENTE y resta. Esperado: la misma
           cifra (la obligacion es la misma). Real: freeToSpend solo resta
           pendientes MATERIALIZADOS (comprometido = movimientos sin
           confirmar), no lo PREVISTO del plan. Igual con el ahorro planeado.
           EstadoDelPeriodoService.calcular linea ~378. */
        LocalDate h = hoy();
        LocalDate ini = inicio(CORTE, h);
        mov(ANDY, salario, "5000000", h, true);
        programar(SAMY, programado("Colegio", casa, "1000000", "MONTHLY", ini, 27, "PAYMENT", null));
        JsonNode antes = hogarEn(ANDY, h);
        generar(SAMY, CORTE);
        JsonNode despues = hogarEn(ANDY, h);
        assertThat(t(despues, "committed")).isEqualByComparingTo("1000000");
        assertThat(t(antes, "freeToSpend")).as("libre antes de que Samy genere su pendiente")
                .isEqualByComparingTo(t(despues, "freeToSpend"));
    }

    // ------------------------------------------------- presupuesto y pagos

    @Test
    @DisplayName("pagos programados confirmados: la categoria compara asignado contra lo pagado de verdad")
    void presupuestoContraPagos() throws Exception {
        Long arr = programar(ANDY, programado("Arriendo", casa, "2000000", "MONTHLY", ANCLA, 1, "PAYMENT", null));
        movimientos.save(base(ANDY, casa, "2100000", LocalDate.of(2026, 10, 1), true, arr, LocalDate.of(2026, 10, 1)));
        asignar(casa, hogar, ANDY, INI, FIN, "2000000");
        JsonNode e = hogarEn(ANDY, REF);
        JsonNode c = categoria(e, casa);
        assertThat(num(c.path("actual"))).as("el monto real pagado, no el programado").isEqualByComparingTo("2100000");
        assertThat(c.path("status").asText()).isEqualTo("EXCEDIDO");
        assertThat(e.path("compliance").path("billsPaid").asInt()).isEqualTo(1);
        JsonNode r = e.path("responsibilities").get(0);
        assertThat(num(r.path("paid"))).isEqualByComparingTo("2100000");
        assertThat(num(r.path("pending"))).as("pagar de mas no deja pendiente negativo").isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("un presupuesto NEGATIVO no se guarda")
    void presupuestoNegativoSeRechaza() throws Exception {
        /* Pasa, pero por la restriccion CHECK de la base (H2 23513), no por
           validacion: AsignacionGuardarDTO.amount solo tiene @NotNull y la
           respuesta es el 500 generico "Error saving budget allocation" en
           vez de un 400 con el campo. Observacion menor de contrato. */
        JsonNode r = pedir(ANDY, "/v1/budget-allocation/save", "{\"categoryId\":" + casa
                + ",\"amount\":-500000,\"budgetStartDay\":28,\"referenceDate\":\"" + REF + "\"}");
        assertThat(r.path("correct").asBoolean()).as(r.toString()).isFalse();
    }

    @Test
    @DisplayName("BUG · presupuestar un INGRESO lo suma como gasto presupuestado en resumen y consolidado, no en el estado")
    void unIngresoPresupuestadoNoEsGastoPresupuestado() throws Exception {
        /* Esperado: las tres pantallas dicen lo mismo en "presupuestado".
           Real: BudgetAllocationServiceImpl.summary/consolidated suman TODAS
           las asignaciones (tambien la de Salario), el estado ignora las de
           categorias INCOME: 5.000.000 de diferencia. */
        asignar(salario, hogar, ANDY, INI, FIN, "5000000");
        asignar(casa, hogar, ANDY, INI, FIN, "2000000");
        JsonNode e = hogarEn(ANDY, REF);
        JsonNode s = ok(pedir(ANDY, "/v1/budget-allocation/summary", periodo(CORTE, REF)));
        JsonNode c = ok(pedir(ANDY, "/v1/dashboard/consolidated", periodo(CORTE, REF)));
        assertThat(t(e, "budgeted")).isEqualByComparingTo("2000000");
        assertThat(num(s.path("totalBudgeted"))).as("resumen del presupuesto").isEqualByComparingTo(t(e, "budgeted"));
        assertThat(num(c.path("budget").path("totalBudgeted"))).as("consolidado").isEqualByComparingTo(t(e, "budgeted"));
    }

    // ------------------------------------------------------------ deducciones

    @Test
    @DisplayName("BUG · un descuento de nomina cuenta como factura vencida en el cumplimiento")
    void unaDeduccionNoEsUnaFacturaVencida() throws Exception {
        /* Esperado: DEDUCTION "nunca aparece ademas como por pagar"
           (PlanFinancieroService, cabecera). Real: el estado la cuenta en
           billsTotal y, sin confirmar, en billsOverdue
           (EstadoDelPeriodoService.calcular lineas ~251-258). */
        Long nomina = cat(ANDY, "Nomina Andy", CategoryType.EXPENSE, null);
        Long d = programar(ANDY, programado("Computador por nomina", nomina, "300000", "MONTHLY", ANCLA, 1, "DEDUCTION", null));
        movimientos.save(base(ANDY, nomina, "300000", LocalDate.of(2026, 10, 1), false, d, LocalDate.of(2026, 10, 1)));
        JsonNode e = estado(ANDY, CORTE, REF, "PERSONA", ANDY);
        assertThat(t(e, "payrollDeductions")).isEqualByComparingTo("300000");
        assertThat(e.path("compliance").path("billsOverdue").asInt()).isZero();
        assertThat(e.path("compliance").path("billsTotal").asInt()).isZero();
    }

    @Test
    @DisplayName("BUG · el plan de la quincena avisa 'vencio' por un descuento de nomina")
    void unaDeduccionNoGeneraAlertaDeVencimiento() throws Exception {
        /* Real: PlanFinancieroService.alertas (linea ~382) solo excluye
           ingresos y provisiones; la deduccion genera VENCIDO/PROXIMO. */
        Long nomina = cat(ANDY, "Nomina Andy", CategoryType.EXPENSE, null);
        programar(ANDY, programado("Salario", salario, "5000000", "MONTHLY", ANCLA, 1, null, null));
        programar(ANDY, programado("Computador por nomina", nomina, "300000", "MONTHLY", ANCLA, 1, "DEDUCTION", null));
        JsonNode q = ok(pedir(ANDY, "/v1/plan/quincena", "{\"referenceDate\":\"2026-10-03\"}"));
        assertThat(q.path("alerts").toString()).doesNotContain("Computador por nomina");
    }
}

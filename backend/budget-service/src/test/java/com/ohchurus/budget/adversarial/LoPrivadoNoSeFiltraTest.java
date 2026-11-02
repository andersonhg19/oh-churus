package com.ohchurus.budget.adversarial;

import com.fasterxml.jackson.databind.JsonNode;
import com.ohchurus.budget.entity.Account;
import com.ohchurus.budget.entity.Category;
import com.ohchurus.budget.entity.HouseholdMember;
import com.ohchurus.budget.entity.Movement;
import com.ohchurus.budget.enums.AccountKind;
import com.ohchurus.budget.enums.CategoryType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PRIVACIDAD Y DATOS EXPUESTOS. Lo privado de Samy (categoria, ingreso,
 * programado, asignacion y cuenta) no puede aparecer en NINGUN campo ni total
 * de NINGUNA respuesta que reciba Andy. Se marcan con importes "firma"
 * (777777, 444444, ...) para poder buscarlos en el texto crudo.
 */
@SpringBootTest
@DisplayName("Adversarial · privacidad: lo privado de la pareja no sale en ninguna respuesta")
class LoPrivadoNoSeFiltraTest extends EscenarioFinanciero {

    static final LocalDate INI = LocalDate.of(2026, 9, 28);
    static final LocalDate FIN = LocalDate.of(2026, 10, 27);
    static final LocalDate REF = LocalDate.of(2026, 10, 10);
    static final String[] FIRMAS = {"Secreto Samy", "Bono Samy", "Terapia Samy", "Cuenta Secreta",
            "777777", "333333", "444444", "555555", "888888"};

    Long casa, secreto, bono;

    @BeforeEach
    void montar() throws Exception {
        casa = cat(ANDY, "Casa", CategoryType.EXPENSE, hogar);
        secreto = cat(SAMY, "Secreto Samy", CategoryType.EXPENSE, null);
        bono = cat(SAMY, "Bono Samy", CategoryType.INCOME, null);

        mov(ANDY, casa, "100000", LocalDate.of(2026, 10, 3), true);
        mov(SAMY, secreto, "777777", LocalDate.of(2026, 10, 4), true);
        mov(SAMY, bono, "333333", LocalDate.of(2026, 10, 5), true);
        programar(SAMY, programado("Terapia Samy", secreto, "444444", "MONTHLY",
                LocalDate.of(2026, 9, 1), 10, "PAYMENT", null));
        asignar(secreto, null, SAMY, INI, FIN, "555555");

        Long cuenta = cuentas.save(Account.builder().userId(SAMY).name("Cuenta Secreta Samy")
                .kind(AccountKind.OWN).isDefault(false).active(true).build()).getId();
        Movement apertura = base(SAMY, bono, "888888", LocalDate.of(2026, 9, 29), true, null, null);
        apertura.setIsOpening(true);
        apertura.setAccountId(cuenta);
        movimientos.save(apertura);
        // un pendiente privado de Samy, para el panel de pendientes
        mov(SAMY, secreto, "444444", LocalDate.of(2026, 10, 10), false);
    }

    private Map<String, String> todoLoQueVeAndy() throws Exception {
        String p = periodo(CORTE, REF);
        Map<String, String> r = new LinkedHashMap<>();
        r.put("estado HOGAR", crudo(ANDY, "/v1/estado/periodo", p));
        r.put("estado PERSONA Samy", crudo(ANDY, "/v1/estado/periodo",
                "{\"budgetStartDay\":28,\"referenceDate\":\"" + REF + "\",\"scope\":\"PERSONA\",\"personUserId\":2}"));
        r.put("estado PERSONA Andy", crudo(ANDY, "/v1/estado/periodo",
                "{\"budgetStartDay\":28,\"referenceDate\":\"" + REF + "\",\"scope\":\"PERSONA\",\"personUserId\":1}"));
        r.put("historial HOGAR", crudo(ANDY, "/v1/estado/historial",
                "{\"budgetStartDay\":28,\"referenceDate\":\"" + REF + "\",\"periods\":3}"));
        r.put("historial Samy", crudo(ANDY, "/v1/estado/historial",
                "{\"budgetStartDay\":28,\"referenceDate\":\"" + REF + "\",\"periods\":3,\"scope\":\"PERSONA\",\"personUserId\":2}"));
        r.put("dashboard/summary", crudo(ANDY, "/v1/dashboard/summary", p));
        r.put("dashboard/by-category", crudo(ANDY, "/v1/dashboard/by-category", p));
        r.put("dashboard/pending", crudo(ANDY, "/v1/dashboard/pending", p));
        r.put("dashboard/trend", crudo(ANDY, "/v1/dashboard/trend", p));
        r.put("dashboard/split-summary", crudo(ANDY, "/v1/dashboard/split-summary", p));
        r.put("dashboard/consolidated", crudo(ANDY, "/v1/dashboard/consolidated", p));
        r.put("budget-allocation/summary", crudo(ANDY, "/v1/budget-allocation/summary", p));
        r.put("budget-allocation/list", crudo(ANDY, "/v1/budget-allocation/list", p));
        r.put("budget-allocation/envelopes", crudo(ANDY, "/v1/budget-allocation/envelopes", p));
        r.put("plan/responsibilities", crudo(ANDY, "/v1/plan/responsibilities", p));
        r.put("plan/calendar", crudo(ANDY, "/v1/plan/calendar", "{\"from\":\"2026-09-01\",\"to\":\"2027-03-31\"}"));
        r.put("plan/quincena", crudo(ANDY, "/v1/plan/quincena", "{\"referenceDate\":\"" + REF + "\"}"));
        r.put("plan/projection", crudo(ANDY, "/v1/plan/projection", "{\"budgetStartDay\":28,\"periods\":6}"));
        r.put("movements/all", crudo(ANDY, "/v1/movements/all", "{\"page\":0,\"size\":500}"));
        r.put("movements/by-period", crudo(ANDY, "/v1/movements/by-period",
                "{\"startDate\":\"2026-09-01\",\"endDate\":\"2026-12-31\"}"));
        r.put("scheduled/all", crudo(ANDY, "/v1/scheduled/all", "{\"page\":0,\"size\":500}"));
        r.put("categories/all", crudo(ANDY, "/v1/categories/all", "{\"page\":0,\"size\":500}"));
        r.put("categories/tree", crudo(ANDY, "/v1/categories/tree", "{}"));
        r.put("accounts/all", crudo(ANDY, "/v1/accounts/all", "{}"));
        return r;
    }

    // ========================================================================

    @Test
    @DisplayName("barrido de 24 endpoints: ningun nombre ni importe privado de Samy llega a Andy")
    void ningunEndpointFiltraLoPrivado() throws Exception {
        for (Map.Entry<String, String> e : todoLoQueVeAndy().entrySet()) {
            for (String firma : FIRMAS) {
                assertThat(e.getValue()).as(e.getKey() + " filtra '" + firma + "'").doesNotContain(firma);
            }
        }
    }

    @Test
    @DisplayName("los totales que ve Andy son los mismos con o sin la vida privada de Samy")
    void losTotalesNoCambianConLoPrivadoDelOtro() throws Exception {
        JsonNode antes = hogarEn(ANDY, REF);
        JsonNode antesSamy = estado(ANDY, CORTE, REF, "PERSONA", SAMY);
        JsonNode resumen = ok(pedir(ANDY, "/v1/dashboard/summary", periodo(CORTE, REF)));
        mov(SAMY, secreto, "1234567", LocalDate.of(2026, 10, 11), true);
        mov(SAMY, bono, "7654321", LocalDate.of(2026, 10, 11), true);
        assertThat(hogarEn(ANDY, REF).path("totals")).isEqualTo(antes.path("totals"));
        assertThat(estado(ANDY, CORTE, REF, "PERSONA", SAMY).path("totals")).isEqualTo(antesSamy.path("totals"));
        JsonNode resumen2 = ok(pedir(ANDY, "/v1/dashboard/summary", periodo(CORTE, REF)));
        assertThat(resumen2.path("totalExpense")).isEqualTo(resumen.path("totalExpense"));
        assertThat(resumen2.path("totalIncome")).isEqualTo(resumen.path("totalIncome"));
    }

    @Test
    @DisplayName("Samy si ve lo suyo en su propio estado (control positivo del barrido)")
    void samyVeLoSuyo() throws Exception {
        JsonNode propia = estado(SAMY, CORTE, REF, "PERSONA", SAMY);
        assertThat(categoria(propia, secreto)).isNotNull();
        assertThat(t(propia, "incomeReceived")).as("bono privado, sin la apertura").isEqualByComparingTo("333333");
    }

    @Test
    @DisplayName("nadie de fuera del hogar puede pedir el estado ni el historial de una persona")
    void nadieDeFuera() throws Exception {
        assertThat(pedir(ANDY, "/v1/estado/periodo", "{\"scope\":\"PERSONA\",\"personUserId\":99}")
                .path("correct").asBoolean()).isFalse();
        assertThat(pedir(ANDY, "/v1/estado/historial", "{\"scope\":\"PERSONA\",\"personUserId\":99}")
                .path("correct").asBoolean()).isFalse();
        assertThat(pedir(EXTRANO, "/v1/estado/periodo", "{\"scope\":\"PERSONA\",\"personUserId\":1}")
                .path("correct").asBoolean()).as("el extrano no puede mirar a Andy").isFalse();
    }

    @Test
    @DisplayName("quien salio del hogar ya no se puede consultar como persona")
    void exMiembroNoSeConsulta() throws Exception {
        for (HouseholdMember m : miembros.findByHouseholdIdAndActiveTrue(hogar)) {
            if (m.getUserId().equals(SAMY)) { m.setActive(false); miembros.save(m); }
        }
        assertThat(pedir(ANDY, "/v1/estado/periodo", "{\"scope\":\"PERSONA\",\"personUserId\":2}")
                .path("correct").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("el extrano ve un estado del hogar vacio: nada de Andy ni Samy")
    void elExtranoNoVeElHogar() throws Exception {
        String r = crudo(EXTRANO, "/v1/estado/periodo", periodo(CORTE, REF));
        assertThat(r).doesNotContain("Casa").doesNotContain("100000");
    }

    @Test
    @DisplayName("Andy no puede reasignar un pendiente privado de Samy ni pasarle un pago a un extrano")
    void reasignarRespetaLaPrivacidad() throws Exception {
        Long privado = mov(SAMY, secreto, "50000", LocalDate.of(2026, 10, 20), false);
        assertThat(pedir(ANDY, "/v1/movements/reassign/" + privado, "{\"payerUserId\":1}")
                .path("correct").asBoolean()).isFalse();
        Long comun = mov(ANDY, casa, "50000", LocalDate.of(2026, 10, 20), false);
        assertThat(pedir(ANDY, "/v1/movements/reassign/" + comun, "{\"payerUserId\":99}")
                .path("correct").asBoolean()).isFalse();
        assertThat(movimientos.findById(comun).get().getUserId()).isEqualTo(ANDY);
    }

    @Test
    @DisplayName("BUG · un extrano puede crear una categoria 'compartida' dentro de un hogar ajeno")
    void unExtranoNoPuedeCrearCategoriasEnElHogar() throws Exception {
        /* Esperado: rechazo (no es miembro del hogar). Real: CategoryServiceImpl
           .createCategory (linea ~91) copia dto.householdId sin comprobar que
           quien crea pertenezca a ese hogar. */
        JsonNode r = pedir(EXTRANO, "/v1/categories/save",
                "{\"name\":\"Intrusa\",\"type\":\"EXPENSE\",\"householdId\":" + hogar + "}");
        assertThat(r.path("correct").asBoolean()).as("crear en un hogar ajeno: " + r).isFalse();
    }

    @Test
    @DisplayName("BUG · los gastos de un extrano se meten en el presupuesto del hogar via esa categoria")
    void losGastosDeUnExtranoNoEntranEnElHogar() throws Exception {
        /* Esperado: el gasto del hogar no cambia. Real: el extrano crea la
           categoria con householdId del hogar, anota 1.000.000 y aparece en el
           "gastado" del hogar de Andy y Samy (findHouseholdConfirmed lo trae
           por la categoria). */
        JsonNode antes = hogarEn(ANDY, REF);
        JsonNode c = pedir(EXTRANO, "/v1/categories/save",
                "{\"name\":\"Intrusa\",\"type\":\"EXPENSE\",\"householdId\":" + hogar + "}");
        Long intrusa = c.path("object").path("id").asLong();
        pedir(EXTRANO, "/v1/movements/save", "{\"categoryId\":" + intrusa
                + ",\"date\":\"2026-10-09\",\"amount\":1000000,\"description\":\"x\"}");
        assertThat(t(hogarEn(ANDY, REF), "spent")).as("gasto del hogar tras el gasto del extrano")
                .isEqualByComparingTo(t(antes, "spent"));
    }

    @Test
    @DisplayName("BUG · si Samy vuelve privada una categoria comun, su asignacion sigue saliendo a Andy")
    void unaCategoriaQueSeVuelvePrivadaNoDejaRastro() throws Exception {
        /* Esperado: Andy deja de ver "Regalos Samy" y su presupuesto. Real: la
           asignacion conserva householdId (se copio al crearla) y
           findAllForUserAndHousehold la sigue trayendo: el estado del hogar y
           /budget-allocation/list muestran nombre e importe de una categoria
           que ya es privada. */
        Long regalos = cat(SAMY, "Regalos Samy", CategoryType.EXPENSE, hogar);
        ok(pedir(SAMY, "/v1/budget-allocation/save", "{\"categoryId\":" + regalos
                + ",\"amount\":222222,\"budgetStartDay\":28,\"referenceDate\":\"" + REF + "\"}"));
        Category c = categorias.findById(regalos).get();
        ok(pedir(SAMY, "/v1/categories/save", "{\"id\":" + regalos + ",\"userId\":2,\"name\":\"Regalos Samy\","
                + "\"type\":\"EXPENSE\"}"));
        assertThat(categorias.findById(c.getId()).get().getHouseholdId()).as("ya es privada").isNull();

        assertThat(crudo(ANDY, "/v1/estado/periodo", periodo(CORTE, REF))).doesNotContain("Regalos Samy").doesNotContain("222222");
        assertThat(crudo(ANDY, "/v1/budget-allocation/list", periodo(CORTE, REF))).doesNotContain("222222");
        assertThat(crudo(ANDY, "/v1/budget-allocation/summary", periodo(CORTE, REF))).doesNotContain("222222");
    }

    @Test
    @DisplayName("los fondos del estado: Andy nunca ve las cuentas privadas de Samy")
    void losFondosNoMuestranCuentasAjenas() throws Exception {
        JsonNode h = hogarEn(ANDY, REF);
        assertThat(h.path("funds").toString()).doesNotContain("Cuenta Secreta");
        JsonNode s = estado(ANDY, CORTE, REF, "PERSONA", SAMY);
        assertThat(s.path("funds").path("accounts").size()).isZero();
    }
}

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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RESPONSABLES DE PAGO Y EDICIONES DESPUES DE CONFIRMAR. Todo en el periodo
 * EN CURSO (corte 28) porque la generacion de pendientes usa la fecha real.
 * Las fechas se calculan desde hoy: el "programado" vence el dia 27, que es
 * el ultimo dia del periodo, asi que su pendiente siempre es futuro.
 */
@SpringBootTest
@DisplayName("Adversarial · responsables de pago, reasignar y editar programados ya confirmados")
class ResponsablesYEdicionesTest extends EscenarioFinanciero {

    Long casa;
    LocalDate h, ini, finP;

    @BeforeEach
    void montar() {
        casa = cat(ANDY, "Casa", CategoryType.EXPENSE, hogar);
        h = hoy();
        ini = inicio(CORTE, h);
        finP = fin(CORTE, ini);
    }

    private String gimnasio(Long id, String monto, LocalDate desde, int dia, String extra) {
        String base = programado("Gimnasio", casa, monto, "MONTHLY", desde, dia, "PAYMENT", extra);
        return id == null ? base : "{\"id\":" + id + "," + base.substring(1);
    }

    private Movement elPendienteDelPeriodo(Long programado) {
        List<Movement> p = pendientesDe(programado).stream()
                .filter(m -> !m.getDate().isBefore(ini) && !m.getDate().isAfter(finP)).toList();
        assertThat(p).as("pendiente del periodo en curso").hasSize(1);
        return p.get(0);
    }

    // ------------------------------------------------------------ reasignar

    @Test
    @DisplayName("reasignar un pendiente comun: pasa al comprometido de Samy y sale del de Andy")
    void reasignarMueveElComprometido() throws Exception {
        Long g = programar(ANDY, gimnasio(null, "150000", ini, 27, null));
        generar(ANDY, CORTE);
        Movement p = elPendienteDelPeriodo(g);
        ok(pedir(ANDY, "/v1/movements/reassign/" + p.getId(), "{\"payerUserId\":2}"));
        assertThat(t(estado(ANDY, CORTE, h, "PERSONA", SAMY), "committed")).isEqualByComparingTo("150000");
        assertThat(t(estado(ANDY, CORTE, h, "PERSONA", ANDY), "committed")).isEqualByComparingTo("0");
        assertThat(t(hogarEn(ANDY, h), "committed")).as("el hogar no cambia").isEqualByComparingTo("150000");
    }

    @Test
    @DisplayName("BUG · tras reasignar, las responsabilidades siguen diciendo que lo paga Andy")
    void reasignarSeVeEnResponsabilidades() throws Exception {
        /* Esperado: "esta vez lo paga Samy" -> en responsibilities (estado y
           /plan/responsibilities) los 150.000 son de Samy. Real: ambas
           agrupan por el pagador del PROGRAMADO (Linea.pagador), no por el
           userId del pendiente reasignado: Andy sigue con 150.000 "por pagar"
           y Samy con 0, contradiciendo el comprometido por persona. */
        Long g = programar(ANDY, gimnasio(null, "150000", ini, 27, null));
        generar(ANDY, CORTE);
        Movement p = elPendienteDelPeriodo(g);
        ok(pedir(ANDY, "/v1/movements/reassign/" + p.getId(), "{\"payerUserId\":2}"));
        BigDecimal deSamy = BigDecimal.ZERO;
        for (JsonNode r : hogarEn(ANDY, h).path("responsibilities"))
            if (r.path("userId").asLong() == SAMY) deSamy = num(r.path("bills"));
        assertThat(deSamy).as("estado.responsibilities de Samy").isEqualByComparingTo("150000");
        BigDecimal planSamy = BigDecimal.ZERO;
        for (JsonNode r : ok(pedir(ANDY, "/v1/plan/responsibilities", periodo(CORTE, h))).path("people"))
            if (r.path("userId").asLong() == SAMY) planSamy = num(r.path("sharedTotal"));
        assertThat(planSamy).as("plan/responsibilities de Samy").isEqualByComparingTo("150000");
    }

    @Test
    @DisplayName("BUG · editar el importe del programado deshace la reasignacion puntual del pendiente")
    void editarImporteNoDeshaceLaReasignacion() throws Exception {
        /* Esperado: subir el gimnasio a 160.000 actualiza el importe del
           pendiente y respeta que ESTA VEZ lo paga Samy. Real:
           ScheduledMovementServiceImpl.realinearOcurrencias (linea ~316)
           devuelve el pendiente al pagador del programado (Andy). */
        Long g = programar(ANDY, gimnasio(null, "150000", ini, 27, null));
        generar(ANDY, CORTE);
        Movement p = elPendienteDelPeriodo(g);
        ok(pedir(ANDY, "/v1/movements/reassign/" + p.getId(), "{\"payerUserId\":2}"));
        ok(pedir(ANDY, "/v1/scheduled/save", gimnasio(g, "160000", ini, 27, null)));
        Movement despues = movimientos.findById(p.getId()).get();
        assertThat(despues.getAmount()).isEqualByComparingTo("160000");
        assertThat(despues.getUserId()).as("sigue siendo de Samy").isEqualTo(SAMY);
    }

    @Test
    @DisplayName("BUG · cambiar solo las notas del programado pisa el importe real que se puso al pendiente")
    void editarNotasNoPisaElImporteDelPendiente() throws Exception {
        /* La factura del agua llego por 175.350 y se corrigio el pendiente.
           Luego alguien edita SOLO las notas del programado. Esperado: el
           pendiente conserva 175.350. Real: realinearOcurrencias (linea ~313)
           reescribe el importe de TODOS los pendientes sin confirmar con el
           del programado (150.000), incluso sin cambio de importe. */
        Long g = programar(ANDY, gimnasio(null, "150000", ini, 27, null));
        generar(ANDY, CORTE);
        Movement p = elPendienteDelPeriodo(g);
        p.setAmount(new BigDecimal("175350"));
        movimientos.save(p);
        ok(pedir(ANDY, "/v1/scheduled/save", gimnasio(g, "150000", ini, 27, "\"notes\":\"ahora con toallas\"")));
        assertThat(movimientos.findById(p.getId()).get().getAmount()).isEqualByComparingTo("175350");
    }

    @Test
    @DisplayName("BUG · corregir el valor de un pendiente desde el formulario lo marca como PAGADO")
    void editarUnPendienteNoLoConfirma() throws Exception {
        /* Esperado: guardar {id, amount, ...} sin "confirmed" deja el
           pendiente pendiente. Real: MovementSaveDTO.confirmed tiene valor
           por defecto TRUE (linea ~65) y updateMovement lo aplica: el
           arriendo queda pagado sin que nadie lo pagara. */
        Long g = programar(ANDY, gimnasio(null, "150000", ini, 27, null));
        generar(ANDY, CORTE);
        Movement p = elPendienteDelPeriodo(g);
        ok(pedir(ANDY, "/v1/movements/save", "{\"id\":" + p.getId() + ",\"categoryId\":" + casa
                + ",\"date\":\"" + p.getDate() + "\",\"amount\":175350,\"description\":\"Gimnasio\"}"));
        assertThat(movimientos.findById(p.getId()).get().getConfirmed()).as("sigue pendiente").isFalse();
    }

    // ---------------------------------------------- editar tras confirmar

    @Test
    @DisplayName("pasar a BOLSA un pago ya confirmado: lo pagado se queda, el pendiente desaparece y no vuelve")
    void pasarAProvisionTrasConfirmar() throws Exception {
        Long g = programar(ANDY, gimnasio(null, "150000", ini.minusMonths(1), 27, null));
        generar(ANDY, CORTE);
        List<Movement> vivos = vivosDe(g);
        assertThat(vivos).as("el del periodo anterior y el de este").hasSize(2);
        Movement anterior = vivos.stream().filter(m -> m.getDate().isBefore(ini)).findFirst().orElseThrow();
        ok(pedir(ANDY, "/v1/movements/confirm/" + anterior.getId(), "{}"));

        ok(pedir(ANDY, "/v1/scheduled/save", gimnasio(g, "150000", ini.minusMonths(1), 27, "\"kind\":\"PROVISION\"")));
        generar(ANDY, CORTE);
        assertThat(pendientesDe(g)).isEmpty();
        assertThat(movimientos.findById(anterior.getId()).get().getConfirmed()).isTrue();
        JsonNode e = hogarEn(ANDY, h);
        assertThat(t(e, "committed")).isEqualByComparingTo("0");
        assertThat(num(categoria(e, casa).path("provision"))).isEqualByComparingTo("150000");
        JsonNode previo = hogarEn(ANDY, ini.minusDays(1));
        assertThat(t(previo, "spent")).as("lo ya pagado sigue contando").isEqualByComparingTo("150000");
    }

    @Test
    @DisplayName("cambiar quien paga tras confirmar: lo confirmado no cambia de dueno, el pendiente si")
    void cambiarPagadorTrasConfirmar() throws Exception {
        Long g = programar(ANDY, gimnasio(null, "150000", ini.minusMonths(1), 27, null));
        generar(ANDY, CORTE);
        Movement anterior = vivosDe(g).stream().filter(m -> m.getDate().isBefore(ini)).findFirst().orElseThrow();
        ok(pedir(ANDY, "/v1/movements/confirm/" + anterior.getId(), "{}"));
        ok(pedir(ANDY, "/v1/scheduled/save", gimnasio(g, "150000", ini.minusMonths(1), 27, "\"payerUserId\":2")));
        assertThat(movimientos.findById(anterior.getId()).get().getUserId()).isEqualTo(ANDY);
        assertThat(elPendienteDelPeriodo(g).getUserId()).isEqualTo(SAMY);
    }

    @Test
    @DisplayName("cambiar el dia (27 -> 20) tras confirmar: una ocurrencia por periodo, sin duplicar")
    void cambiarElDiaTrasConfirmar() throws Exception {
        Long g = programar(ANDY, gimnasio(null, "150000", ini.minusMonths(1), 27, null));
        generar(ANDY, CORTE);
        Movement anterior = vivosDe(g).stream().filter(m -> m.getDate().isBefore(ini)).findFirst().orElseThrow();
        ok(pedir(ANDY, "/v1/movements/confirm/" + anterior.getId(), "{}"));
        ok(pedir(ANDY, "/v1/scheduled/save", gimnasio(g, "150000", ini.minusMonths(1), 20,
                "\"budgetStartDay\":28")));
        generar(ANDY, CORTE);
        generar(ANDY, CORTE);
        long previos = vivosDe(g).stream().filter(m -> m.getDate().isBefore(ini)).count();
        assertThat(previos).as("el periodo anterior sigue con UN gimnasio (el pagado)").isEqualTo(1);
        long actuales = vivosDe(g).stream().filter(m -> !m.getDate().isBefore(ini) && !m.getDate().isAfter(finP)).count();
        assertThat(actuales).as("el periodo en curso tiene a lo sumo uno").isLessThanOrEqualTo(1);
    }

    @Test
    @DisplayName("acortar una deuda a 1 cuota tras pagar la primera: no quedan pendientes ni vuelven a generarse")
    void acortarLaDuracion() throws Exception {
        Long g = programar(ANDY, gimnasio(null, "150000", ini.minusMonths(1), 27, "\"durationMonths\":6"));
        generar(ANDY, CORTE);
        Movement anterior = vivosDe(g).stream().filter(m -> m.getDate().isBefore(ini)).findFirst().orElseThrow();
        ok(pedir(ANDY, "/v1/movements/confirm/" + anterior.getId(), "{}"));
        ok(pedir(ANDY, "/v1/scheduled/save", gimnasio(g, "150000", ini.minusMonths(1), 27, "\"durationMonths\":1")));
        generar(ANDY, CORTE);
        assertThat(pendientesDe(g)).isEmpty();
        assertThat(itemsDe(hogarEn(ANDY, h), g)).isEmpty();
    }

    // ---------------------------------------------------- confirmar varios

    @Test
    @DisplayName("confirmar varios: id repetido, monto negativo o texto -> nada se confirma")
    void confirmarVariosRechazaBasura() throws Exception {
        Long a = mov(ANDY, casa, "10000", ini, false);
        Long b = mov(ANDY, casa, "20000", ini, false);
        assertThat(pedir(ANDY, "/v1/movements/confirm-batch", "{\"items\":[{\"id\":" + a + "},{\"id\":" + a + "}]}")
                .path("correct").asBoolean()).isFalse();
        assertThat(pedir(ANDY, "/v1/movements/confirm-batch", "{\"items\":[{\"id\":" + a + "},{\"id\":" + b + ",\"amount\":-5}]}")
                .path("correct").asBoolean()).isFalse();
        assertThat(pedir(ANDY, "/v1/movements/confirm-batch", "{\"items\":[{\"id\":" + a + "},{\"id\":" + b + ",\"amount\":\"abc\"}]}")
                .path("correct").asBoolean()).isFalse();
        assertThat(pedir(ANDY, "/v1/movements/confirm-batch", "{\"items\":[]}").path("correct").asBoolean()).isFalse();
        assertThat(movimientos.findById(a).get().getConfirmed()).isFalse();
        assertThat(movimientos.findById(b).get().getConfirmed()).isFalse();
    }

    @Test
    @DisplayName("BUG · confirmar varios cuenta como 'confirmado ahora' un pago que ya estaba pagado")
    void confirmarVariosNoRecuentaLoYaPagado() throws Exception {
        /* Esperado: o se rechaza, o confirmed=1 y total=20.000 (solo lo nuevo).
           Real: MovementServiceImpl.confirmarVarios no mira si ya estaba
           confirmado: responde confirmed=2, total=30.000, e incluso le cambia
           el importe si viene "amount". */
        Long ya = mov(ANDY, casa, "10000", ini, true);
        Long b = mov(ANDY, casa, "20000", ini, false);
        JsonNode r = pedir(ANDY, "/v1/movements/confirm-batch", "{\"items\":[{\"id\":" + ya + "},{\"id\":" + b + "}]}");
        if (r.path("correct").asBoolean()) {
            assertThat(r.path("object").path("confirmed").asInt()).isEqualTo(1);
            assertThat(num(r.path("object").path("total"))).isEqualByComparingTo("20000");
        }
    }

    @Test
    @DisplayName("Samy no puede confirmar el pendiente privado de Andy en un lote")
    void confirmarVariosRespetaLaPrivacidad() throws Exception {
        Long privada = cat(ANDY, "Privada Andy", CategoryType.EXPENSE, null);
        Long a = mov(ANDY, privada, "10000", ini, false);
        Long b = mov(SAMY, casa, "20000", ini, false);
        assertThat(pedir(SAMY, "/v1/movements/confirm-batch", "{\"items\":[{\"id\":" + b + "},{\"id\":" + a + "}]}")
                .path("correct").asBoolean()).isFalse();
        assertThat(movimientos.findById(b).get().getConfirmed()).as("todo o nada").isFalse();
    }

    @Test
    @DisplayName("el responsable de un programado tiene que ser del hogar; una clase inventada se rechaza")
    void responsableYClaseValidos() throws Exception {
        assertThat(pedir(ANDY, "/v1/scheduled/save", gimnasio(null, "150000", ini, 27, "\"payerUserId\":99"))
                .path("correct").asBoolean()).isFalse();
        assertThat(pedir(ANDY, "/v1/scheduled/save", programado("X", casa, "1", "MONTHLY", ini, 27, "REGALO", null))
                .path("correct").asBoolean()).isFalse();
    }
}

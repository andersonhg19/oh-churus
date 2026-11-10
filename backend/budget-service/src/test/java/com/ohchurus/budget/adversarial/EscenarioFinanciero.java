package com.ohchurus.budget.adversarial;

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
import com.ohchurus.budget.repository.BudgetAllocationRepository;
import com.ohchurus.budget.repository.CategoryRepository;
import com.ohchurus.budget.repository.HouseholdMemberRepository;
import com.ohchurus.budget.repository.HouseholdRepository;
import com.ohchurus.budget.repository.MovementRepository;
import com.ohchurus.budget.repository.ScheduledMovementRepository;
import com.ohchurus.budget.util.PeriodUtils;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Base comun de la suite adversarial: un hogar de dos (Andy y Samy), un
 * extrano (99) y los ayudantes para hablarle a la API con el token de cada uno.
 *
 * No termina en "Test" a proposito: surefire no la ejecuta sola.
 *
 * Fechas: el servicio usa LocalDate.now() como "hoy" en varias cifras
 * (VENCIDO, closed, ventana de generacion). Los escenarios del pasado usan
 * septiembre-octubre de 2026 con corte 28, ya cerrados; los que dependen de
 * hoy calculan el periodo a partir de LocalDate.now() para no romperse solos.
 */
abstract class EscenarioFinanciero {

    static final Long ANDY = 1L;
    static final Long SAMY = 2L;
    static final Long EXTRANO = 99L;
    static final int CORTE = 28;

    @Autowired WebApplicationContext contexto;
    @Autowired MovementRepository movimientos;
    @Autowired CategoryRepository categorias;
    @Autowired HouseholdRepository hogares;
    @Autowired HouseholdMemberRepository miembros;
    @Autowired BudgetAllocationRepository asignaciones;
    @Autowired ScheduledMovementRepository programados;
    @Value("${secret}") String secreto;

    final ObjectMapper json = new ObjectMapper();
    MockMvc mvc;
    Long hogar;

    @BeforeEach
    void montarBase() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto).apply(springSecurity()).build();
        movimientos.deleteAll();
        asignaciones.deleteAll();
        programados.deleteAll();
        categorias.deleteAll();
        miembros.deleteAll();
        hogares.deleteAll();

        hogar = hogares.save(Household.builder().name("Familia").active(true).build()).getId();
        miembros.save(HouseholdMember.builder().householdId(hogar).userId(ANDY).role("OWNER")
                .displayName("Andy").active(true).build());
        miembros.save(HouseholdMember.builder().householdId(hogar).userId(SAMY).role("MEMBER")
                .displayName("Samy").active(true).build());
    }

    // ------------------------------------------------------------------ fechas

    static LocalDate hoy() { return LocalDate.now(); }

    static LocalDate inicio(int corte, LocalDate ref) { return PeriodUtils.getStartOfPeriod(corte, ref); }

    static LocalDate fin(int corte, LocalDate inicio) { return PeriodUtils.getEndOfPeriod(corte, inicio); }

    // ------------------------------------------------------------------ datos

    Long cat(Long dueno, String nombre, CategoryType tipo, Long h) {
        return categorias.save(Category.builder().userId(dueno).name(nombre).type(tipo)
                .householdId(h).active(true).build()).getId();
    }

    /** Programa por la API (como lo haria la app) y devuelve el id. */
    Long programar(Long quien, String cuerpoJson) throws Exception {
        JsonNode r = pedir(quien, "/v1/scheduled/save", cuerpoJson);
        assertThat(r.path("correct").asBoolean()).as("no se pudo programar: " + r).isTrue();
        return r.path("object").path("id").asLong();
    }

    static String programado(String nombre, Long categoria, String monto, String frecuencia,
                             LocalDate inicio, Integer dia, String clase, String extra) {
        return "{\"name\":\"" + nombre + "\",\"categoryId\":" + categoria + ",\"amount\":" + monto
                + ",\"frequency\":\"" + frecuencia + "\",\"startDate\":\"" + inicio + "\""
                + (dia != null ? ",\"dayOfMonth\":" + dia : "")
                + (clase != null ? ",\"kind\":\"" + clase + "\"" : "")
                + (extra != null ? "," + extra : "") + "}";
    }

    void asignar(Long categoria, Long h, Long quien, LocalDate ini, LocalDate finP, String monto) {
        asignaciones.save(BudgetAllocation.builder().userId(quien).categoryId(categoria).householdId(h)
                .periodStart(ini).periodEnd(finP)
                .allocatedAmount(new BigDecimal(monto)).active(true).build());
    }

    Movement base(Long quien, Long categoria, String monto, LocalDate fecha, boolean confirmado, Long programado,
                  LocalDate clave) {
        return Movement.builder().userId(quien).categoryId(categoria).amount(new BigDecimal(monto))
                .date(fecha).description("m").confirmed(confirmado).active(true)
                .scheduledMovementId(programado).periodStart(clave).build();
    }

    Long mov(Long quien, Long categoria, String monto, LocalDate fecha, boolean confirmado) {
        return movimientos.save(base(quien, categoria, monto, fecha, confirmado, null, null)).getId();
    }

    // ------------------------------------------------------------------ API

    String tokenDe(Long quien) {
        return "Bearer " + JWT.create().withSubject("u" + quien + "@ohchurus.com")
                .withClaim("userId", quien)
                .withExpiresAt(new Date(System.currentTimeMillis() + 3_600_000))
                .sign(Algorithm.HMAC256(secreto));
    }

    JsonNode pedir(Long quien, String ruta, String cuerpo) throws Exception {
        String r = mvc.perform(post(ruta).header("Authorization", tokenDe(quien))
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo == null ? "{}" : cuerpo))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(r);
    }

    String crudo(Long quien, String ruta, String cuerpo) throws Exception {
        return mvc.perform(post(ruta).header("Authorization", tokenDe(quien))
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo == null ? "{}" : cuerpo))
                .andReturn().getResponse().getContentAsString();
    }

    JsonNode ok(JsonNode r) {
        assertThat(r.path("correct").asBoolean()).as(r.toString()).isTrue();
        return r.path("object");
    }

    JsonNode estado(Long quien, int corte, LocalDate ref, String alcance, Long persona) throws Exception {
        return ok(pedir(quien, "/v1/estado/periodo", "{\"budgetStartDay\":" + corte
                + ",\"referenceDate\":\"" + ref + "\""
                + (alcance != null ? ",\"scope\":\"" + alcance + "\"" : "")
                + (persona != null ? ",\"personUserId\":" + persona : "") + "}"));
    }

    JsonNode hogarEn(Long quien, LocalDate ref) throws Exception {
        return estado(quien, CORTE, ref, null, null);
    }

    static String periodo(int corte, LocalDate ref) {
        return "{\"budgetStartDay\":" + corte + ",\"referenceDate\":\"" + ref + "\"}";
    }

    void generar(Long quien, int corte) throws Exception {
        ok(pedir(quien, "/v1/scheduled/generate-pending", "{\"budgetStartDay\":" + corte + "}"));
    }

    static JsonNode categoria(JsonNode estado, Long id) {
        for (JsonNode c : estado.path("categories")) if (c.path("categoryId").asLong() == id) return c;
        return null;
    }

    static List<JsonNode> itemsDe(JsonNode estado, Long scheduledId) {
        List<JsonNode> out = new ArrayList<>();
        for (JsonNode c : estado.path("categories"))
            for (JsonNode i : c.path("items"))
                if (i.path("scheduledMovementId").asLong() == scheduledId) out.add(i);
        for (JsonNode i : estado.path("incomeItems"))
            if (i.path("scheduledMovementId").asLong() == scheduledId) out.add(i);
        return out;
    }

    static BigDecimal num(JsonNode n) {
        if (n == null || n.isMissingNode() || n.isNull()) return BigDecimal.ZERO;
        return new BigDecimal(n.asText());
    }

    static BigDecimal t(JsonNode estado, String campo) {
        return num(estado.path("totals").path(campo));
    }

    List<Movement> pendientesDe(Long programadoId) {
        List<Movement> out = new ArrayList<>();
        for (Movement m : movimientos.findByScheduledMovementId(programadoId))
            if (Boolean.TRUE.equals(m.getActive()) && !Boolean.TRUE.equals(m.getConfirmed())) out.add(m);
        return out;
    }

    List<Movement> vivosDe(Long programadoId) {
        List<Movement> out = new ArrayList<>();
        for (Movement m : movimientos.findByScheduledMovementId(programadoId))
            if (Boolean.TRUE.equals(m.getActive())) out.add(m);
        return out;
    }
}

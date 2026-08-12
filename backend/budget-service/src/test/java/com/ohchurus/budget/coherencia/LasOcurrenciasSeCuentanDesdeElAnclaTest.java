package com.ohchurus.budget.coherencia;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.ohchurus.budget.entity.Category;
import com.ohchurus.budget.entity.Movement;
import com.ohchurus.budget.entity.ScheduledMovement;
import com.ohchurus.budget.enums.CategoryType;
import com.ohchurus.budget.enums.Frequency;
import com.ohchurus.budget.enums.WeekendPolicy;
import com.ohchurus.budget.repository.BudgetAllocationRepository;
import com.ohchurus.budget.repository.CategoryRepository;
import com.ohchurus.budget.repository.HouseholdMemberRepository;
import com.ohchurus.budget.repository.HouseholdRepository;
import com.ohchurus.budget.repository.MovementRepository;
import com.ohchurus.budget.repository.ScheduledMovementRepository;
import com.ohchurus.budget.util.CalendarioDeRecurrencias;
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
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * ============================================================================
 * LAS OCURRENCIAS SE CUENTAN DESDE EL ANCLA
 * ============================================================================
 *
 * Un programado DIARIO tiene que generar una ocurrencia POR DIA. Uno SEMANAL,
 * una por semana. Es lo que dice la etiqueta que el usuario eligio.
 *
 * El generador no hacia eso: recorria PERIODOS de presupuesto —o sea, meses— y
 * dentro de cada periodo se preguntaba "¿aplica esta frecuencia?", una pregunta
 * que solo puede responderse si o no. Con lo cual DAILY, WEEKLY y BIWEEKLY
 * generaban exactamente lo mismo que MONTHLY: un movimiento al mes. Tres de las
 * ocho frecuencias del catalogo eran mentira.
 *
 * La leccion es la misma del motor de tiempo del proyecto hermano: las
 * ocurrencias se enumeran DESDE EL ANCLA (la fecha de inicio del programado),
 * no avanzando "la ultima + un periodo". Contar desde una marca fija no acumula
 * error y no depende de cada cuanto se abra la app.
 */
@SpringBootTest
@DisplayName("Las ocurrencias se cuentan desde el ancla, no una por mes")
class LasOcurrenciasSeCuentanDesdeElAnclaTest {

    private static final Long ANA = 1L;
    private static final int CORTE = 1;

    @Autowired private WebApplicationContext contexto;
    @Autowired private MovementRepository movimientos;
    @Autowired private CategoryRepository categorias;
    @Autowired private ScheduledMovementRepository programados;
    @Autowired private HouseholdRepository hogares;
    @Autowired private HouseholdMemberRepository miembros;
    @Autowired private BudgetAllocationRepository asignaciones;

    @Value("${secret}") private String secreto;

    private MockMvc mvc;
    private Long categoriaDeAna;

    @BeforeEach
    void prepararEscenario() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto).apply(springSecurity()).build();

        movimientos.deleteAll();
        asignaciones.deleteAll();
        programados.deleteAll();
        categorias.deleteAll();
        miembros.deleteAll();
        hogares.deleteAll();

        categoriaDeAna = categorias.save(Category.builder()
                .userId(ANA).name("Diario").type(CategoryType.EXPENSE).active(true).build()).getId();
    }

    private String token() {
        return "Bearer " + JWT.create().withSubject("ana@ohchurus.com")
                .withClaim("userId", ANA)
                .withExpiresAt(new Date(System.currentTimeMillis() + 3_600_000))
                .sign(Algorithm.HMAC256(secreto));
    }

    private Long crearProgramado(String nombre, Frequency frecuencia, LocalDate inicio) {
        return programados.save(ScheduledMovement.builder()
                .userId(ANA).categoryId(categoriaDeAna).name(nombre)
                .amount(new BigDecimal("5000")).frequency(frecuencia)
                .startDate(inicio).active(true).build()).getId();
    }

    private String refrescarElPanel() throws Exception {
        return mvc.perform(post("/v1/scheduled/generate-pending")
                        .header("Authorization", token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"budgetStartDay\":" + CORTE + "}"))
                .andReturn().getResponse().getContentAsString();
    }

    private String materializar(String ocurrenciasJson) throws Exception {
        return mvc.perform(post("/v1/scheduled/materialize")
                        .header("Authorization", token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"occurrences\":" + ocurrenciasJson + "}"))
                .andReturn().getResponse().getContentAsString();
    }

    private List<LocalDate> fechasGeneradasDe(Long programado) {
        return movimientos.findAll().stream()
                .filter(m -> programado.equals(m.getScheduledMovementId()))
                .filter(Movement::getActive)
                .map(Movement::getDate)
                .sorted()
                .collect(Collectors.toList());
    }

    /** Las fechas que la etiqueta del programado promete entre el ancla y hoy. */
    private List<LocalDate> prometidas(LocalDate ancla, int pasoEnDias) {
        List<LocalDate> esperadas = new ArrayList<>();
        for (LocalDate d = ancla; !d.isAfter(LocalDate.now()); d = d.plusDays(pasoEnDias)) {
            esperadas.add(d);
        }
        return esperadas;
    }

    // ========================================================================

    @Test
    @DisplayName("un programado DIARIO genera una ocurrencia por dia, no una al mes")
    void diarioGeneraUnaPorDia() throws Exception {
        LocalDate ancla = LocalDate.now().minusDays(4);
        Long cafe = crearProgramado("Cafe", Frequency.DAILY, ancla);

        refrescarElPanel();

        assertThat(fechasGeneradasDe(cafe))
                .as("DAILY prometia una ocurrencia por dia desde %s y el generador dio una al mes", ancla)
                .containsAll(prometidas(ancla, 1));
    }

    @Test
    @DisplayName("un programado SEMANAL genera una ocurrencia por semana, no una al mes")
    void semanalGeneraUnaPorSemana() throws Exception {
        LocalDate ancla = LocalDate.now().minusWeeks(4);
        Long mercado = crearProgramado("Mercado", Frequency.WEEKLY, ancla);

        refrescarElPanel();

        assertThat(fechasGeneradasDe(mercado))
                .as("WEEKLY prometia una ocurrencia cada 7 dias desde %s", ancla)
                .containsAll(prometidas(ancla, 7));
    }

    @Test
    @DisplayName("un programado QUINCENAL genera una ocurrencia cada 14 dias")
    void quincenalGeneraCada14Dias() throws Exception {
        LocalDate ancla = LocalDate.now().minusWeeks(4);
        Long nomina = crearProgramado("Quincena", Frequency.BIWEEKLY, ancla);

        refrescarElPanel();

        assertThat(fechasGeneradasDe(nomina))
                .as("BIWEEKLY prometia una ocurrencia cada 14 dias desde %s", ancla)
                .containsAll(prometidas(ancla, 14));
    }

    @Test
    @DisplayName("un mensual el dia 31 no se queda en el 28 despues de febrero")
    void elDia31VuelveDespuesDeFebrero() throws Exception {
        /* Este es el error que solo evita contar desde el ancla: enumerando "la
           anterior + un mes", enero 31 -> febrero 28 -> marzo 28, y el dia 31
           no vuelve nunca. Desde el ancla, marzo es 31 otra vez. */
        ScheduledMovement programado = ScheduledMovement.builder()
                .userId(ANA).categoryId(categoriaDeAna).name("Cuota")
                .amount(new BigDecimal("100000")).frequency(Frequency.MONTHLY)
                .startDate(LocalDate.of(2026, 1, 31)).dayOfMonth(31).active(true).build();

        List<LocalDate> canonicas = CalendarioDeRecurrencias
                .ocurrenciasHasta(programado, LocalDate.of(2026, 4, 30)).stream()
                .map(CalendarioDeRecurrencias.Ocurrencia::canonica)
                .collect(Collectors.toList());

        assertThat(canonicas).containsExactly(
                LocalDate.of(2026, 1, 31),
                LocalDate.of(2026, 2, 28),
                LocalDate.of(2026, 3, 31),
                LocalDate.of(2026, 4, 30));
    }

    @Test
    @DisplayName("el patron 'el tercer viernes' cae en el tercer viernes de cada mes")
    void elTercerViernes() {
        /* Asi se paga la nomina en Colombia, y no hay dia del mes que lo diga:
           en agosto de 2026 el tercer viernes es el 21 y en septiembre el 18. */
        ScheduledMovement nomina = ScheduledMovement.builder()
                .userId(ANA).categoryId(categoriaDeAna).name("Nomina")
                .amount(new BigDecimal("3000000")).frequency(Frequency.MONTHLY)
                .startDate(LocalDate.of(2026, 8, 1))
                .weekOfMonth(3).dayOfWeek(DayOfWeek.FRIDAY.getValue())
                .active(true).build();

        List<LocalDate> canonicas = CalendarioDeRecurrencias
                .ocurrenciasHasta(nomina, LocalDate.of(2026, 10, 31)).stream()
                .map(CalendarioDeRecurrencias.Ocurrencia::canonica)
                .collect(Collectors.toList());

        assertThat(canonicas).containsExactly(
                LocalDate.of(2026, 8, 21),
                LocalDate.of(2026, 9, 18),
                LocalDate.of(2026, 10, 16));
        assertThat(canonicas).allMatch(f -> f.getDayOfWeek() == DayOfWeek.FRIDAY);
    }

    @Test
    @DisplayName("la quinta semana significa 'la ultima', no el mes siguiente")
    void laQuintaSemanaEsLaUltima() {
        /* TemporalAdjusters.dayOfWeekInMonth(5, ...) por su cuenta se va al mes
           siguiente cuando no hay quinto viernes, y eso pone el movimiento en
           el mes equivocado. Septiembre de 2026 tiene cuatro viernes. */
        ScheduledMovement ultimo = ScheduledMovement.builder()
                .userId(ANA).categoryId(categoriaDeAna).name("Cierre")
                .amount(new BigDecimal("50000")).frequency(Frequency.MONTHLY)
                .startDate(LocalDate.of(2026, 9, 1))
                .weekOfMonth(5).dayOfWeek(DayOfWeek.FRIDAY.getValue())
                .active(true).build();

        List<LocalDate> canonicas = CalendarioDeRecurrencias
                .ocurrenciasHasta(ultimo, LocalDate.of(2026, 10, 31)).stream()
                .map(CalendarioDeRecurrencias.Ocurrencia::canonica)
                .collect(Collectors.toList());

        assertThat(canonicas).containsExactly(
                LocalDate.of(2026, 9, 25),
                LocalDate.of(2026, 10, 30));
    }

    @Test
    @DisplayName("la politica de fin de semana mueve la fecha pero NUNCA la clave")
    void laPoliticaMueveLaFechaNoLaClave() {
        /* 2026-08-01 es sabado y 2026-11-01 es domingo. Si la politica moviera
           tambien la clave, cambiar de politica duplicaria las ocurrencias que
           ya estaban generadas. */
        ScheduledMovement arriendo = ScheduledMovement.builder()
                .userId(ANA).categoryId(categoriaDeAna).name("Arriendo")
                .amount(new BigDecimal("1500000")).frequency(Frequency.MONTHLY)
                .startDate(LocalDate.of(2026, 8, 1)).dayOfMonth(1)
                .weekendPolicy(WeekendPolicy.PREVIOUS_BUSINESS_DAY)
                .active(true).build();

        CalendarioDeRecurrencias.Ocurrencia agosto = CalendarioDeRecurrencias
                .ocurrenciasHasta(arriendo, LocalDate.of(2026, 8, 31)).get(0);

        assertThat(agosto.canonica()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(agosto.fecha())
                .as("el 1 de agosto de 2026 es sabado: adelantar lo pone en el viernes 31 de julio")
                .isEqualTo(LocalDate.of(2026, 7, 31));
        assertThat(agosto.clave())
                .as("la clave sale del ancla, no de la fecha movida")
                .isEqualTo(LocalDate.of(2026, 8, 1));

        arriendo.setWeekendPolicy(WeekendPolicy.NEXT_BUSINESS_DAY);
        CalendarioDeRecurrencias.Ocurrencia atrasada = CalendarioDeRecurrencias
                .ocurrenciasHasta(arriendo, LocalDate.of(2026, 8, 31)).get(0);
        assertThat(atrasada.fecha()).isEqualTo(LocalDate.of(2026, 8, 3));
        assertThat(atrasada.clave())
                .as("cambiar de politica no puede cambiar la clave: duplicaria lo ya generado")
                .isEqualTo(agosto.clave());

        arriendo.setWeekendPolicy(WeekendPolicy.KEEP);
        assertThat(CalendarioDeRecurrencias.ocurrenciasHasta(arriendo, LocalDate.of(2026, 8, 31))
                .get(0).fecha())
                .as("el valor por defecto no mueve nada")
                .isEqualTo(LocalDate.of(2026, 8, 1));
    }

    // ========================================================================
    // El tope de materializacion: la app no inventa datos

    @Test
    @DisplayName("mas de 5 ocurrencias atrasadas NO se crean solas: se proponen para revisar")
    void demasiadasAtrasadasSeProponen() throws Exception {
        /* Un diario abandonado tres meses son noventa movimientos. Crearlos en
           silencio es escribirle a alguien un historial que nunca reviso. */
        Long cafe = crearProgramado("Cafe olvidado", Frequency.DAILY, LocalDate.now().minusDays(40));

        String respuesta = refrescarElPanel();

        assertThat(fechasGeneradasDe(cafe))
                .as("se materializaron en silencio 40 movimientos que nadie ha visto")
                .isEmpty();
        assertThat(respuesta)
                .as("si no se crean y tampoco se proponen, el programado desaparece sin avisar")
                .contains("\"needsReview\":true")
                .contains("Cafe olvidado");
    }

    @Test
    @DisplayName("las propuestas se pueden aceptar y entonces si se crean")
    void lasPropuestasSeAceptan() throws Exception {
        Long cafe = crearProgramado("Cafe olvidado", Frequency.DAILY, LocalDate.now().minusDays(40));
        refrescarElPanel();

        LocalDate primera = LocalDate.now().minusDays(40);
        LocalDate segunda = LocalDate.now().minusDays(39);
        materializar("[{\"scheduledMovementId\":" + cafe + ",\"periodStart\":\"" + primera + "\"},"
                + "{\"scheduledMovementId\":" + cafe + ",\"periodStart\":\"" + segunda + "\"}]");

        assertThat(fechasGeneradasDe(cafe)).containsExactly(primera, segunda);
    }

    @Test
    @DisplayName("aceptar dos veces la misma ocurrencia no la crea dos veces")
    void aceptarDosVecesNoDuplica() throws Exception {
        Long cafe = crearProgramado("Cafe olvidado", Frequency.DAILY, LocalDate.now().minusDays(40));
        refrescarElPanel();

        String cuerpo = "[{\"scheduledMovementId\":" + cafe + ",\"periodStart\":\""
                + LocalDate.now().minusDays(40) + "\"}]";
        materializar(cuerpo);
        materializar(cuerpo);

        assertThat(fechasGeneradasDe(cafe)).hasSize(1);
    }

    @Test
    @DisplayName("no se puede materializar una fecha que el programado no tiene")
    void noSePuedeInventarUnaOcurrencia() throws Exception {
        /* Si el cuerpo pudiera dictar la fecha, "aceptar una propuesta" seria
           una via para crear el movimiento que a uno le diera la gana. */
        Long nomina = crearProgramado("Quincena", Frequency.BIWEEKLY, LocalDate.now().minusWeeks(4));

        String respuesta = materializar("[{\"scheduledMovementId\":" + nomina
                + ",\"periodStart\":\"" + LocalDate.now().minusDays(3) + "\"}]");

        assertThat(respuesta).contains("\"correct\":false");
        assertThat(fechasGeneradasDe(nomina)).doesNotContain(LocalDate.now().minusDays(3));
    }
}

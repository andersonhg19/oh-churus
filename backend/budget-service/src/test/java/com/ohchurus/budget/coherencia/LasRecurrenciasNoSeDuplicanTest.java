package com.ohchurus.budget.coherencia;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.ohchurus.budget.entity.Category;
import com.ohchurus.budget.entity.Household;
import com.ohchurus.budget.entity.HouseholdMember;
import com.ohchurus.budget.entity.Movement;
import com.ohchurus.budget.entity.ScheduledMovement;
import com.ohchurus.budget.enums.CategoryType;
import com.ohchurus.budget.enums.Frequency;
import com.ohchurus.budget.repository.BudgetAllocationRepository;
import com.ohchurus.budget.repository.CategoryRepository;
import com.ohchurus.budget.repository.HouseholdMemberRepository;
import com.ohchurus.budget.repository.HouseholdRepository;
import com.ohchurus.budget.repository.MovementRepository;
import com.ohchurus.budget.repository.ScheduledMovementRepository;
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
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * ============================================================================
 * LAS RECURRENCIAS NO SE DUPLICAN NI RESUCITAN
 * ============================================================================
 *
 * El panel llama a generate-pending cada vez que se abre. Eso deberia ser
 * inofensivo, y no lo era:
 *
 *   · La clave de idempotencia era la FECHA del pendiente, que el usuario puede
 *     mover. Mover la fecha —o editar el programado— y refrescar creaba un
 *     segundo arriendo del mismo mes.
 *   · Cada miembro del hogar elige su propio dia de corte, asi que Ana con
 *     corte 1 y Bruno con corte 15 abrian periodos distintos para el mismo mes
 *     y el arriendo se generaba dos veces.
 *   · Borrar un pendiente no servia de nada: reaparecia en el siguiente
 *     refresco. No habia manera de decir "este mes esto no".
 *   · El pendiente se creaba a nombre de QUIEN PULSA. En un hogar, el arriendo
 *     de Ana acababa a nombre de Bruno solo porque abrio la app primero.
 *
 * El escenario: un hogar con dos personas, dos programados (uno de cada una) y
 * los dos refrescando el panel una y otra vez con dias de corte distintos.
 */
@SpringBootTest
@DisplayName("Refrescar el panel mil veces no multiplica los pendientes")
class LasRecurrenciasNoSeDuplicanTest {

    private static final Long ANA = 1L;
    private static final Long BRUNO = 2L;

    /* Dias de corte distintos a proposito: es el caso que duplicaba. */
    private static final int CORTE_DE_ANA = 1;
    private static final int CORTE_DE_BRUNO = 15;

    @Autowired private WebApplicationContext contexto;
    @Autowired private MovementRepository movimientos;
    @Autowired private CategoryRepository categorias;
    @Autowired private ScheduledMovementRepository programados;
    @Autowired private HouseholdRepository hogares;
    @Autowired private HouseholdMemberRepository miembros;
    @Autowired private BudgetAllocationRepository asignaciones;

    @Value("${secret}") private String secreto;

    private MockMvc mvc;
    private Long arriendoDeAna;
    private Long internetDeBruno;

    @BeforeEach
    void montarElHogar() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto).apply(springSecurity()).build();
        /* Se vacia TODO y en este orden porque ahora hay claves foraneas de
           verdad: dejar una asignacion de otra clase de prueba colgando de una
           categoria impide borrar la categoria. */
        movimientos.deleteAll();
        asignaciones.deleteAll();
        programados.deleteAll();
        categorias.deleteAll();
        miembros.deleteAll();
        hogares.deleteAll();

        Household hogar = hogares.save(Household.builder().name("Casa").active(true).build());
        miembros.save(HouseholdMember.builder()
                .householdId(hogar.getId()).userId(ANA).role("OWNER").active(true).build());
        miembros.save(HouseholdMember.builder()
                .householdId(hogar.getId()).userId(BRUNO).role("MEMBER").active(true).build());

        /* Categoria COMPARTIDA: es la unica forma de que los dos vean —y por
           tanto puedan generar— el programado del otro. Ahi estaba el lio. */
        Long catCasa = categorias.save(Category.builder()
                .userId(ANA).name("Gastos de la casa").type(CategoryType.EXPENSE)
                .householdId(hogar.getId()).active(true).build()).getId();

        LocalDate haceDosMeses = LocalDate.now().minusMonths(2).withDayOfMonth(1);

        arriendoDeAna = programados.save(ScheduledMovement.builder()
                .userId(ANA).categoryId(catCasa).name("Arriendo")
                .amount(new BigDecimal("1500000")).frequency(Frequency.MONTHLY)
                .startDate(haceDosMeses).dayOfMonth(5).active(true).build()).getId();

        internetDeBruno = programados.save(ScheduledMovement.builder()
                .userId(BRUNO).categoryId(catCasa).name("Internet")
                .amount(new BigDecimal("120000")).frequency(Frequency.MONTHLY)
                .startDate(haceDosMeses).dayOfMonth(20).active(true).build()).getId();
    }

    private String token(Long usuario) {
        return "Bearer " + JWT.create().withSubject("u" + usuario + "@ohchurus.com")
                .withClaim("userId", usuario)
                .withExpiresAt(new Date(System.currentTimeMillis() + 3_600_000))
                .sign(Algorithm.HMAC256(secreto));
    }

    private void refrescarElPanel(Long usuario, int diaDeCorte) throws Exception {
        mvc.perform(post("/v1/scheduled/generate-pending")
                        .header("Authorization", token(usuario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"budgetStartDay\":" + diaDeCorte + "}"))
                .andReturn();
    }

    /** Los dos abren la app varias veces, alternandose, como en la vida real. */
    private void ambosRefrescanCincoVeces() throws Exception {
        for (int i = 0; i < 5; i++) {
            refrescarElPanel(ANA, CORTE_DE_ANA);
            refrescarElPanel(BRUNO, CORTE_DE_BRUNO);
        }
    }

    private List<Movement> ocurrenciasVivasDe(Long programado) {
        return movimientos.findAll().stream()
                .filter(m -> programado.equals(m.getScheduledMovementId()))
                .filter(Movement::getActive)
                .collect(Collectors.toList());
    }

    /** Cuantas ocurrencias vivas hay por periodo. Deberia ser siempre una. */
    private Map<LocalDate, Long> porPeriodo(Long programado) {
        return ocurrenciasVivasDe(programado).stream()
                .collect(Collectors.groupingBy(Movement::getPeriodStart, Collectors.counting()));
    }

    // ========================================================================

    @Test
    @DisplayName("cinco refrescos de los dos miembros dejan UN pendiente por programado y periodo")
    void unoPorProgramadoYPeriodo() throws Exception {
        ambosRefrescanCincoVeces();

        for (Long programado : List.of(arriendoDeAna, internetDeBruno)) {
            Map<LocalDate, Long> conteo = porPeriodo(programado);
            assertThat(conteo).as("el programado %s no genero nada", programado).isNotEmpty();
            assertThat(conteo.values())
                    .as("el programado %s se duplico: %s", programado, conteo)
                    .containsOnly(1L);
        }
    }

    @Test
    @DisplayName("el pendiente queda a nombre del DUENO del programado, no de quien pulsa")
    void aNombreDelDueno() throws Exception {
        /* Bruno es el unico que refresca: sin el arreglo, el arriendo de Ana
           acaba siendo un gasto de Bruno. */
        refrescarElPanel(BRUNO, CORTE_DE_BRUNO);

        assertThat(ocurrenciasVivasDe(arriendoDeAna))
                .as("Bruno refresco el panel y se quedo con el arriendo de Ana")
                .isNotEmpty()
                .allMatch(m -> ANA.equals(m.getUserId()));
        assertThat(ocurrenciasVivasDe(internetDeBruno))
                .isNotEmpty()
                .allMatch(m -> BRUNO.equals(m.getUserId()));
    }

    @Test
    @DisplayName("borrar un pendiente lo OMITE: no resucita en el siguiente refresco")
    void borrarloLoOmite() throws Exception {
        refrescarElPanel(ANA, CORTE_DE_ANA);
        Movement aOmitir = ocurrenciasVivasDe(arriendoDeAna).get(0);
        LocalDate periodoOmitido = aOmitir.getPeriodStart();

        mvc.perform(post("/v1/movements/delete/" + aOmitir.getId())
                        .header("Authorization", token(ANA))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andReturn();

        ambosRefrescanCincoVeces();

        assertThat(porPeriodo(arriendoDeAna))
                .as("el pendiente borrado reaparecio: no habia forma de saltarse un mes")
                .doesNotContainKey(periodoOmitido);
    }

    @Test
    @DisplayName("editar el programado no duplica lo que ya estaba generado")
    void editarElProgramadoNoDuplica() throws Exception {
        ambosRefrescanCincoVeces();
        int antes = ocurrenciasVivasDe(arriendoDeAna).size();

        /* Sube el arriendo y cambia el dia de cobro: antes esto movia la fecha
           esperada y el generador creaba una segunda ocurrencia del mismo mes. */
        mvc.perform(post("/v1/scheduled/save")
                        .header("Authorization", token(ANA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":" + arriendoDeAna + ",\"categoryId\":"
                                + programados.findById(arriendoDeAna).orElseThrow().getCategoryId()
                                + ",\"name\":\"Arriendo\",\"amount\":1700000,"
                                + "\"frequency\":\"MONTHLY\",\"dayOfMonth\":25,"
                                + "\"startDate\":\"" + LocalDate.now().minusMonths(2).withDayOfMonth(1) + "\"}"))
                .andReturn();

        ambosRefrescanCincoVeces();

        assertThat(ocurrenciasVivasDe(arriendoDeAna))
                .as("editar el programado duplico las ocurrencias ya existentes")
                .hasSize(antes);
    }

    @Test
    @DisplayName("lo que se genera NO depende de quien refresca ni de que dia sea")
    void laVentanaNoDependeDeQuienMira() throws Exception {
        /*
         * ESTA PRUEBA NACIO DE UN FALLO QUE APARECIO SOLO.
         *
         * moverLaFechaNoDuplica llevaba dias en verde y un martes se puso roja
         * sin que nadie tocara el codigo de recurrencias: habia cambiado la
         * fecha del sistema. El corte de Ana es el 1 y el de Bruno el 15, asi
         * que a partir del dia 15 el "periodo actual" de Bruno se mete en el
         * mes siguiente — y como la ventana de generacion se calculaba con el
         * dia de corte de QUIEN REFRESCA, el refresco de Bruno le creaba a Ana
         * el arriendo del mes que viene.
         *
         * O sea: el mismo programado generaba un numero distinto de
         * ocurrencias segun quien abriera la app y que dia del mes fuera. Es
         * la misma familia del fallo que ya costo una ola —"el arriendo de uno
         * acababa a nombre del otro solo porque abrio la app primero"— y da la
         * misma sensacion de app inestable: cifras que cambian solas.
         *
         * La regla, que es lo que esta prueba fija: lo TUYO se adelanta hasta
         * el final de TU periodo; lo de OTRO solo se materializa hasta hoy.
         */
        /* Ana refresca primero: su propio calendario decide cuanto se adelanta
           lo suyo. Ese es el numero que Bruno no puede mover. */
        refrescarElPanel(ANA, CORTE_DE_ANA);
        int antesDeQueMireBruno = ocurrenciasVivasDe(arriendoDeAna).size();
        assertThat(antesDeQueMireBruno).isPositive();

        for (int i = 0; i < 5; i++) {
            refrescarElPanel(BRUNO, CORTE_DE_BRUNO);
        }

        assertThat(ocurrenciasVivasDe(arriendoDeAna))
                .as("el refresco de Bruno le creo a Ana ocurrencias que su propio calendario "
                        + "todavia no pedia")
                .hasSize(antesDeQueMireBruno);
    }

    @Test
    @DisplayName("y a nadie se le adelantan pendientes futuros con el calendario de otro")
    void aNadieSeLeAdelantaElFuturoAjeno() throws Exception {
        for (int i = 0; i < 5; i++) {
            refrescarElPanel(BRUNO, CORTE_DE_BRUNO);
        }

        assertThat(ocurrenciasVivasDe(arriendoDeAna))
                .as("materializarle a alguien un pendiente futuro usando TU dia de corte es "
                        + "inventarle datos con una regla que ni siquiera es la suya")
                .allMatch(m -> !m.getDate().isAfter(java.time.LocalDate.now()));
    }

    @Test
    @DisplayName("mover la fecha de un pendiente no hace que se genere otro")
    void moverLaFechaNoDuplica() throws Exception {
        refrescarElPanel(ANA, CORTE_DE_ANA);
        Movement pendiente = ocurrenciasVivasDe(arriendoDeAna).stream()
                .max(java.util.Comparator.comparing(Movement::getPeriodStart)).orElseThrow();
        int antes = ocurrenciasVivasDe(arriendoDeAna).size();

        /* La fecha es del usuario: puede pagar el arriendo cuando quiera. Lo que
           no puede es que moverla invente una ocurrencia nueva. */
        mvc.perform(post("/v1/movements/save")
                        .header("Authorization", token(ANA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":" + pendiente.getId() + ",\"categoryId\":" + pendiente.getCategoryId()
                                + ",\"date\":\"" + pendiente.getDate().minusMonths(1) + "\",\"amount\":1500000,"
                                + "\"description\":\"Arriendo\",\"scheduledMovementId\":" + arriendoDeAna + "}"))
                .andReturn();

        ambosRefrescanCincoVeces();

        assertThat(ocurrenciasVivasDe(arriendoDeAna))
                .as("mover la fecha del pendiente hizo que el generador creara otro")
                .hasSize(antes);
    }

    @Test
    @DisplayName("cada ocurrencia lleva grabado el periodo al que pertenece")
    void cadaOcurrenciaSabeSuPeriodo() throws Exception {
        ambosRefrescanCincoVeces();

        assertThat(ocurrenciasVivasDe(arriendoDeAna))
                .as("sin periodo grabado, la clave de idempotencia vuelve a ser la fecha")
                .isNotEmpty()
                .allMatch(m -> m.getPeriodStart() != null && m.getPeriodStart().getDayOfMonth() == 1);
    }
}

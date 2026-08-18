package com.ohchurus.budget.coherencia;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ohchurus.budget.entity.Category;
import com.ohchurus.budget.entity.Movement;
import com.ohchurus.budget.enums.CategoryType;
import com.ohchurus.budget.repository.AccountRepository;
import com.ohchurus.budget.repository.BudgetAllocationRepository;
import com.ohchurus.budget.repository.CategoryRepository;
import com.ohchurus.budget.repository.HouseholdMemberRepository;
import com.ohchurus.budget.repository.HouseholdRepository;
import com.ohchurus.budget.repository.ImportRuleRepository;
import com.ohchurus.budget.repository.MovementRepository;
import com.ohchurus.budget.repository.ScheduledMovementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * ============================================================================
 * IMPORTAR DOS VECES NO DUPLICA
 * ============================================================================
 *
 * El importador es lo que decide si la app se usa o se abandona —nadie la deja
 * por informes feos, todo el mundo la deja por teclear sesenta movimientos al
 * mes—, pero solo si se puede CONFIAR en el. Y la confianza se pierde de una
 * sola manera: importando el mismo extracto dos veces y encontrandose el mes
 * duplicado.
 *
 * Por eso todo lo de aqui gira sobre lo mismo: que se pueda volver a importar
 * sin miedo, y que la vista previa no escriba nada hasta que se acepte.
 */
@SpringBootTest
@DisplayName("Importar: la vista previa no escribe, y reimportar no duplica")
class ImportarNoDuplicaTest {

    private static final Long ANA = 1L;

    private static final String EXTRACTO = """
            fecha,concepto,valor,referencia
            2026-08-01,"COMPRA EXITO CALLE 80",-45000,REF-1
            2026-08-03,"PAGO NEQUI",-20000,REF-2
            2026-08-05,"ARRIENDO AGOSTO",-1500000,REF-3
            """;

    @Autowired private WebApplicationContext contexto;
    @Autowired private MovementRepository movimientos;
    @Autowired private CategoryRepository categorias;
    @Autowired private AccountRepository cuentas;
    @Autowired private ImportRuleRepository reglas;
    @Autowired private BudgetAllocationRepository asignaciones;
    @Autowired private ScheduledMovementRepository programados;
    @Autowired private HouseholdMemberRepository miembros;
    @Autowired private HouseholdRepository hogares;
    @Value("${secret}") private String secreto;

    private final ObjectMapper json = new ObjectMapper();
    private MockMvc mvc;
    private Long categoria;

    @BeforeEach
    void prepararEscenario() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto).apply(springSecurity()).build();
        movimientos.deleteAll();
        reglas.deleteAll();
        asignaciones.deleteAll();
        programados.deleteAll();
        cuentas.deleteAll();
        categorias.deleteAll();
        miembros.deleteAll();
        hogares.deleteAll();

        categoria = categorias.save(Category.builder()
                .userId(ANA).name("Mercado").type(CategoryType.EXPENSE).active(true).build()).getId();
    }

    private String token() {
        return "Bearer " + JWT.create().withSubject("ana@ohchurus.com")
                .withClaim("userId", ANA)
                .withExpiresAt(new Date(System.currentTimeMillis() + 3_600_000))
                .sign(Algorithm.HMAC256(secreto));
    }

    private JsonNode pedir(String ruta, String cuerpo) throws Exception {
        String r = mvc.perform(post(ruta).header("Authorization", token())
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(r);
    }

    private String mapeo() {
        return "\"dateColumn\":0,\"descriptionColumn\":1,\"amountColumn\":2,"
                + "\"externalIdColumn\":3,\"hasHeader\":true";
    }

    private JsonNode vistaPrevia() throws Exception {
        return pedir("/v1/import/preview",
                "{\"csv\":" + json.writeValueAsString(EXTRACTO) + "," + mapeo() + "}").path("object");
    }

    private JsonNode importarTodo() throws Exception {
        return pedir("/v1/import/confirm",
                "{\"csv\":" + json.writeValueAsString(EXTRACTO) + "," + mapeo() + ",\"rows\":["
                        + "{\"row\":1,\"categoryId\":" + categoria + "},"
                        + "{\"row\":2,\"categoryId\":" + categoria + "},"
                        + "{\"row\":3,\"categoryId\":" + categoria + "}]}").path("object");
    }

    // ========================================================================

    @Nested
    @DisplayName("La vista previa")
    class LaVistaPrevia {

        @Test
        @DisplayName("lee las tres filas y NO escribe ninguna")
        void noEscribeNada() throws Exception {
            JsonNode previa = vistaPrevia();

            assertThat(previa.path("total").asInt()).isEqualTo(3);
            assertThat(previa.path("newRows")).hasSize(3);
            assertThat(movimientos.count())
                    .as("una vista previa que escribe deja de ser una vista previa: con sesenta "
                            + "filas mal metidas la unica salida es borrar el mes entero")
                    .isZero();
        }

        @Test
        @DisplayName("propone gasto o ingreso segun el signo del extracto")
        void proponeElTipo() throws Exception {
            JsonNode primera = vistaPrevia().path("newRows").get(0);
            assertThat(primera.path("suggestedType").asText()).isEqualTo("EXPENSE");
        }

        @Test
        @DisplayName("una fila ilegible se salta en vez de tumbar el archivo entero")
        void filaIlegible() throws Exception {
            /* Los extractos traen totales y pies de pagina. Rechazar el fichero
               por eso obligaria a editarlo a mano antes de importarlo, que es
               justo lo que el importador viene a evitar. */
            String conBasura = EXTRACTO + "TOTAL DEL PERIODO,,,\n";
            JsonNode previa = pedir("/v1/import/preview",
                    "{\"csv\":" + json.writeValueAsString(conBasura) + "," + mapeo() + "}")
                    .path("object");

            assertThat(previa.path("total").asInt()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("Importar")
    class Importar {

        @Test
        @DisplayName("crea los movimientos y les pone cuenta")
        void creaConCuenta() throws Exception {
            JsonNode r = importarTodo();

            assertThat(r.path("created").asInt()).isEqualTo(3);
            assertThat(movimientos.findAll())
                    .as("un movimiento sin cuenta no sale en ningun saldo pero si en el "
                            + "presupuesto: descuadre invisible")
                    .allMatch(m -> m.getAccountId() != null);
        }

        @Test
        @DisplayName("importar el MISMO extracto otra vez no duplica nada")
        void reimportarNoDuplica() throws Exception {
            importarTodo();
            long despuesDeLaPrimera = movimientos.count();

            JsonNode segunda = vistaPrevia();

            assertThat(segunda.path("duplicates"))
                    .as("es la unica forma de perder la confianza en un importador: volver a "
                            + "pasar el extracto y encontrarse el mes por duplicado")
                    .hasSize(3);
            assertThat(segunda.path("newRows")).isEmpty();
            assertThat(movimientos.count()).isEqualTo(despuesDeLaPrimera);
        }

        @Test
        @DisplayName("una fila sin categoria valida se omite, y se dice cual")
        void sinCategoriaSeOmite() throws Exception {
            /* NO se inventa una categoria. Meter sesenta gastos en "Otros" es
               lo que hace que despues nadie se fie del presupuesto, y
               arreglarlo cuesta mas que haberlos tecleado. */
            JsonNode r = pedir("/v1/import/confirm",
                    "{\"csv\":" + json.writeValueAsString(EXTRACTO) + "," + mapeo() + ",\"rows\":["
                            + "{\"row\":1}]}").path("object");

            assertThat(r.path("created").asInt()).isZero();
            assertThat(r.path("skipped").toString()).contains("sin categoria");
        }

        @Test
        @DisplayName("aprende donde pusiste cada cosa y lo sugiere la vez siguiente")
        void aprendeElDiccionario() throws Exception {
            importarTodo();
            movimientos.deleteAll();

            JsonNode previa = vistaPrevia();
            JsonNode primera = previa.path("newRows").get(0);

            assertThat(primera.path("suggestedCategoryId").asLong())
                    .as("si no aprendiera, cada importacion obligaria a clasificar las mismas "
                            + "sesenta descripciones otra vez")
                    .isEqualTo(categoria);
        }
    }

    @Nested
    @DisplayName("El pendiente que estabas esperando")
    class ElPendiente {

        @Test
        @DisplayName("la fila que casa con un pendiente lo CONFIRMA en vez de crear otro")
        void confirmaEnVezDeDuplicar() throws Exception {
            Movement pendiente = movimientos.save(Movement.builder()
                    .userId(ANA).categoryId(categoria).date(LocalDate.parse("2026-08-05"))
                    .amount(new BigDecimal("1500000")).description("Arriendo")
                    .scheduledMovementId(7L).confirmed(false).active(true).build());

            JsonNode previa = vistaPrevia();
            assertThat(previa.path("confirmPending")).hasSize(1);

            long id = previa.path("confirmPending").get(0).path("matchedMovementId").asLong();
            JsonNode r = pedir("/v1/import/confirm",
                    "{\"csv\":" + json.writeValueAsString(EXTRACTO) + "," + mapeo() + ",\"rows\":["
                            + "{\"row\":3,\"confirmsMovementId\":" + id + "}]}").path("object");

            assertThat(r.path("confirmed").asInt()).isEqualTo(1);
            assertThat(movimientos.findById(pendiente.getId()).orElseThrow().getConfirmed())
                    .as("importarlo como nuevo dejaria el pendiente colgando para siempre y el "
                            + "arriendo contado dos veces")
                    .isTrue();
            assertThat(movimientos.count())
                    .as("y no puede haber aparecido un movimiento nuevo")
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("al confirmarlo le queda el identificador del banco para la proxima vez")
        void seQuedaConElIdentificador() throws Exception {
            Movement pendiente = movimientos.save(Movement.builder()
                    .userId(ANA).categoryId(categoria).date(LocalDate.parse("2026-08-05"))
                    .amount(new BigDecimal("1500000")).description("Arriendo")
                    .scheduledMovementId(7L).confirmed(false).active(true).build());

            long id = vistaPrevia().path("confirmPending").get(0).path("matchedMovementId").asLong();
            pedir("/v1/import/confirm",
                    "{\"csv\":" + json.writeValueAsString(EXTRACTO) + "," + mapeo() + ",\"rows\":["
                            + "{\"row\":3,\"confirmsMovementId\":" + id + "}]}");

            assertThat(movimientos.findById(pendiente.getId()).orElseThrow().getExternalId())
                    .as("sin guardarlo, la siguiente importacion tendria que adivinar por importe "
                            + "y fecha teniendo el dato exacto delante")
                    .isEqualTo("REF-3");
        }
    }
}

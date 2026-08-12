package com.ohchurus.budget.coherencia;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ohchurus.budget.dto.output.ResultDTO;
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
import com.ohchurus.budget.service.impl.BudgetAllocationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * ============================================================================
 * LA TRANSFERENCIA ES UN PAR ATOMICO
 * ============================================================================
 *
 * "Disponibilizar" —sacar plata del bote comun del hogar y pasarla al bolsillo
 * personal— no es un movimiento: son DOS, enlazados por transferPairId. Una
 * salida en la categoria compartida y una entrada en la personal, por el mismo
 * importe y el mismo dia.
 *
 * Borrar ya trataba los dos como uno solo. Editar no: corregir una
 * transferencia de 500.000 a 300.000 cambiaba una sola pata y dejaba 200.000
 * que no existen flotando en el consolidado. Y no habia forma de arreglarlo
 * desde la app, porque la otra pata no se puede editar por separado.
 *
 * SE ELIGIO PROPAGAR, NO BLOQUEAR
 * -------------------------------
 * Bloquear la edicion y pedir "anula y rehaz" era mas barato de programar, pero
 * peor de usar: obliga a la persona a borrar y volver a escribir por corregir
 * un cero, pierde la fecha original, y hay que inventar pantalla y mensaje para
 * algo que la app ya sabe hacer sola. Ademas el borrado YA propaga a las dos
 * patas: propagar la edicion es la regla que ya existia, aplicada tambien aqui.
 *
 * LA INVARIANTE
 * -------------
 *      shared.balance + personal.balance == total.balance
 *
 * y no solo para quien hizo la transferencia: tiene que cuadrar tambien cuando
 * la mira la pareja, que ve salir la plata del bote comun pero no la ve entrar
 * en un bolsillo que no es suyo.
 */
@SpringBootTest
@DisplayName("Una transferencia se edita entera o no se edita")
class LaTransferenciaEsUnParTest {

    private static final Long ANA = 1L;
    private static final Long BRUNO = 2L;

    private static final BigDecimal APORTE_COMUN = new BigDecimal("1000000");
    private static final BigDecimal MERCADO = new BigDecimal("300000");
    private static final BigDecimal CAPRICHO_DE_ANA = new BigDecimal("100000");
    private static final BigDecimal TRANSFERENCIA = new BigDecimal("500000");
    private static final BigDecimal TRANSFERENCIA_CORREGIDA = new BigDecimal("300000");

    @Autowired private WebApplicationContext contexto;
    @Autowired private MovementRepository movimientos;
    @Autowired private CategoryRepository categorias;
    @Autowired private HouseholdRepository hogares;
    @Autowired private HouseholdMemberRepository miembros;
    @Autowired private BudgetAllocationRepository asignaciones;
    @Autowired private ScheduledMovementRepository programados;
    @Autowired private BudgetAllocationServiceImpl informes;

    @Value("${secret}") private String secreto;

    private final ObjectMapper json = new ObjectMapper();
    private MockMvc mvc;

    private Long catBoteComun;
    private Long catBolsilloDeAna;
    private Long catCaprichosDeAna;

    /* Las dos patas de la transferencia del escenario. */
    private Long pataQueSale;
    private Long pataQueEntra;

    @BeforeEach
    void montarElHogar() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(contexto).apply(springSecurity()).build();
        /* Se vacia TODO y en este orden porque ahora hay claves foraneas de
           verdad: dejar un programado de otra clase de prueba colgando de una
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

        Long catAporte = categoria(ANA, "Aporte al bote", CategoryType.INCOME, hogar.getId());
        catBoteComun = categoria(ANA, "Bote comun", CategoryType.EXPENSE, hogar.getId());
        catBolsilloDeAna = categoria(ANA, "Bolsillo de Ana", CategoryType.INCOME, null);
        catCaprichosDeAna = categoria(ANA, "Caprichos de Ana", CategoryType.EXPENSE, null);

        movimiento(ANA, catAporte, APORTE_COMUN, "Aporte");
        movimiento(ANA, catBoteComun, MERCADO, "Mercado");
        movimiento(ANA, catCaprichosDeAna, CAPRICHO_DE_ANA, "Libro");

        JsonNode transferencia = pedir(ANA, "/v1/movements/transfer",
                "{\"fromCategoryId\":" + catBoteComun + ",\"toCategoryId\":" + catBolsilloDeAna
                        + ",\"amount\":" + TRANSFERENCIA.toPlainString() + ",\"description\":\"Disponibilizar\"}");
        pataQueSale = transferencia.path("expenseId").asLong();
        pataQueEntra = transferencia.path("incomeId").asLong();
        assertThat(pataQueSale).as("no se creo la transferencia del escenario").isNotZero();
    }

    // ==================== utilidades ====================

    private Long categoria(Long dueno, String nombre, CategoryType tipo, Long hogarId) {
        return categorias.save(Category.builder()
                .userId(dueno).name(nombre).type(tipo).householdId(hogarId).active(true).build()).getId();
    }

    private void movimiento(Long dueno, Long categoria, BigDecimal importe, String desc) {
        movimientos.save(Movement.builder()
                .userId(dueno).categoryId(categoria).date(LocalDate.now())
                .amount(importe).description(desc)
                .isTransfer(false).confirmed(true).active(true).build());
    }

    private String token(Long usuario) {
        return "Bearer " + JWT.create().withSubject("u" + usuario + "@ohchurus.com")
                .withClaim("userId", usuario)
                .withExpiresAt(new Date(System.currentTimeMillis() + 3_600_000))
                .sign(Algorithm.HMAC256(secreto));
    }

    private JsonNode pedir(Long usuario, String ruta, String cuerpo) throws Exception {
        String s = mvc.perform(post(ruta).header("Authorization", token(usuario))
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(s).path("object");
    }

    private Movement enLaBase(Long id) {
        return movimientos.findById(id).orElseThrow();
    }

    /**
     * El consolidado tal y como lo ve un miembro concreto del hogar.
     * No pasa por HTTP porque no hay endpoint: se pide al servicio.
     */
    @SuppressWarnings("unchecked")
    private BigDecimal saldo(Long usuario, String bloque) {
        ResultDTO r = informes.consolidated(usuario, 1, LocalDate.now());
        Map<String, Object> informe = (Map<String, Object>) r.getObject();
        Map<String, Object> parte = (Map<String, Object>) informe.get(bloque);
        return (BigDecimal) parte.get("balance");
    }

    /** La invariante: lo compartido mas lo personal es exactamente el total. */
    private void elConsolidadoCuadraPara(Long usuario, String escenario) {
        assertThat(saldo(usuario, "shared").add(saldo(usuario, "personal")))
                .as("compartido + personal no da el total (%s), usuario %s", escenario, usuario)
                .isEqualByComparingTo(saldo(usuario, "total"));
    }

    // ========================================================================
    @Nested
    @DisplayName("Editar mueve las dos patas")
    class Editar {

        @Test
        @DisplayName("corregir el importe de una pata corrige la otra")
        void elImporteViajaALaOtraPata() throws Exception {
            pedir(ANA, "/v1/movements/save",
                    "{\"id\":" + pataQueSale + ",\"categoryId\":" + catBoteComun
                            + ",\"date\":\"" + LocalDate.now() + "\",\"amount\":"
                            + TRANSFERENCIA_CORREGIDA.toPlainString() + ",\"description\":\"Disponibilizar\"}");

            assertThat(enLaBase(pataQueEntra).getAmount())
                    .as("se corrigio la salida y la entrada se quedo en el importe viejo: "
                            + "el consolidado inventa la diferencia y no hay forma de arreglarlo")
                    .isEqualByComparingTo(TRANSFERENCIA_CORREGIDA);
        }

        @Test
        @DisplayName("corregir la fecha de una pata corrige la otra")
        void laFechaViajaALaOtraPata() throws Exception {
            LocalDate ayer = LocalDate.now().minusDays(1);
            pedir(ANA, "/v1/movements/save",
                    "{\"id\":" + pataQueSale + ",\"categoryId\":" + catBoteComun
                            + ",\"date\":\"" + ayer + "\",\"amount\":" + TRANSFERENCIA.toPlainString()
                            + ",\"description\":\"Disponibilizar\"}");

            assertThat(enLaBase(pataQueEntra).getDate())
                    .as("las dos patas quedaron en dias distintos: la transferencia sale de un "
                            + "periodo y entra en otro")
                    .isEqualTo(ayer);
        }

        @Test
        @DisplayName("confirmar con otro importe tambien mueve las dos")
        void confirmarConOtroImporte() throws Exception {
            pedir(ANA, "/v1/movements/confirm/" + pataQueSale,
                    "{\"amount\":" + TRANSFERENCIA_CORREGIDA.toPlainString() + "}");

            assertThat(enLaBase(pataQueEntra).getAmount())
                    .as("confirmar con importe era la otra puerta para editar una sola pata")
                    .isEqualByComparingTo(TRANSFERENCIA_CORREGIDA);
        }

        @Test
        @DisplayName("una pata no se puede mudar de categoria")
        void noSePuedeMudarDeCategoria() throws Exception {
            /* Si la entrada se mueve a una categoria compartida, el par pasa a
               ser dos salidas del bote comun: la plata desaparece del informe. */
            pedir(ANA, "/v1/movements/save",
                    "{\"id\":" + pataQueEntra + ",\"categoryId\":" + catBoteComun
                            + ",\"date\":\"" + LocalDate.now() + "\",\"amount\":"
                            + TRANSFERENCIA.toPlainString() + ",\"description\":\"Disponibilizar\"}");

            assertThat(enLaBase(pataQueEntra).getCategoryId())
                    .as("la entrada se mudo al bote comun y el par dejo de ser un par")
                    .isEqualTo(catBolsilloDeAna);
        }
    }

    // ========================================================================
    @Nested
    @DisplayName("La invariante: compartido + personal == total, para los dos miembros")
    class Invariante {

        @ParameterizedTest(name = "usuario {0}")
        @ValueSource(longs = {1L, 2L})
        @DisplayName("cuadra con la transferencia recien hecha")
        void recienHecha(long usuario) {
            elConsolidadoCuadraPara(usuario, "transferencia recien hecha");
        }

        @ParameterizedTest(name = "usuario {0}")
        @ValueSource(longs = {1L, 2L})
        @DisplayName("cuadra despues de corregir el importe")
        void trasCorregirElImporte(long usuario) throws Exception {
            pedir(ANA, "/v1/movements/save",
                    "{\"id\":" + pataQueSale + ",\"categoryId\":" + catBoteComun
                            + ",\"date\":\"" + LocalDate.now() + "\",\"amount\":"
                            + TRANSFERENCIA_CORREGIDA.toPlainString() + ",\"description\":\"Disponibilizar\"}");
            elConsolidadoCuadraPara(usuario, "transferencia corregida");
        }

        @ParameterizedTest(name = "usuario {0}")
        @ValueSource(longs = {1L, 2L})
        @DisplayName("cuadra despues de anular la transferencia")
        void trasAnularla(long usuario) throws Exception {
            pedir(ANA, "/v1/movements/delete/" + pataQueSale, "{}");
            elConsolidadoCuadraPara(usuario, "transferencia anulada");
        }

        @Test
        @DisplayName("el bote comun refleja la correccion, no el importe viejo")
        void elBoteComunRefleja() throws Exception {
            pedir(ANA, "/v1/movements/save",
                    "{\"id\":" + pataQueSale + ",\"categoryId\":" + catBoteComun
                            + ",\"date\":\"" + LocalDate.now() + "\",\"amount\":"
                            + TRANSFERENCIA_CORREGIDA.toPlainString() + ",\"description\":\"Disponibilizar\"}");

            // 1.000.000 de aporte - 300.000 de mercado - 300.000 disponibilizados
            assertThat(saldo(ANA, "shared")).isEqualByComparingTo(new BigDecimal("400000"));
        }
    }
}

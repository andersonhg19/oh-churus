package com.ohchurus.budget.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.ohchurus.budget.entity.BudgetAllocation;
import com.ohchurus.budget.entity.Category;
import com.ohchurus.budget.entity.Household;
import com.ohchurus.budget.entity.HouseholdMember;
import com.ohchurus.budget.entity.Movement;
import com.ohchurus.budget.entity.ScheduledMovement;
import com.ohchurus.budget.enums.CategoryType;
import com.ohchurus.budget.enums.Frequency;
import com.ohchurus.budget.entity.Account;
import com.ohchurus.budget.enums.AccountKind;
import com.ohchurus.budget.repository.AccountRepository;
import com.ohchurus.budget.repository.BudgetAllocationRepository;
import com.ohchurus.budget.repository.CategoryRepository;
import com.ohchurus.budget.repository.HouseholdMemberRepository;
import com.ohchurus.budget.repository.HouseholdRepository;
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
import org.springframework.test.web.servlet.MvcResult;
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
 * MATRIZ DE AISLAMIENTO ENTRE USUARIOS
 * ============================================================================
 *
 * Este es el test que a este proyecto le faltaba.
 *
 * Habia 502 pruebas en verde y un 99-100% de cobertura, y ni una sola
 * comprobaba lo unico que de verdad importa en una app donde dos personas
 * guardan sus finanzas: que TU no puedas ver ni tocar lo MIO.
 *
 * No podia comprobarlo, ademas, por construccion: todas las pruebas eran
 * unitarias con mocks, y un mock nunca te dice que el filtro de seguridad no
 * esta cableado. Esta es la primera prueba del proyecto que levanta la
 * aplicacion entera —contexto de Spring, cadena de filtros, JPA sobre una base
 * de datos real en memoria— y ataca la API como lo haria una persona.
 *
 * COMO FUNCIONA
 * -------------
 * ANA (id 1) crea sus datos. BRUNO (id 2) se autentica con un token
 * perfectamente valido —es un usuario legitimo, no un intruso— e intenta
 * llegar a los datos de Ana por su id.
 *
 * QUE SE EXIGE
 * ------------
 * No se comprueba un codigo de estado concreto, sino las dos propiedades que
 * de verdad importan:
 *
 *   1. La respuesta NO contiene los datos de Ana.
 *   2. Despues del intento, los datos de Ana siguen intactos en la base.
 *
 * Asi el test sobrevive a que se decida devolver 403 o 404, y sigue midiendo
 * lo mismo.
 *
 * COMO SE USA
 * -----------
 * Si anades un endpoint que reciba un id, anadelo aqui. Si este test se pone
 * rojo, no lo ajustes: se acaba de abrir un agujero.
 */
@SpringBootTest
@DisplayName("Aislamiento entre usuarios: Bruno no puede llegar a los datos de Ana")
class AislamientoEntreUsuariosTest {

    private static final Long ANA = 1L;
    private static final Long BRUNO = 2L;

    @Autowired private WebApplicationContext contexto;
    @Autowired private MovementRepository movimientos;
    @Autowired private CategoryRepository categorias;
    @Autowired private ScheduledMovementRepository programados;
    @Autowired private BudgetAllocationRepository asignaciones;
    @Autowired private HouseholdRepository hogares;
    @Autowired private HouseholdMemberRepository miembros;

    @Value("${secret}") private String secreto;

    private MockMvc mvc;

    /* Datos de Ana */
    private Long categoriaDeAna;
    private Long movimientoDeAna;
    private Long programadoDeAna;
    private Long asignacionDeAna;
    private Long hogarDeAna;
    private Long cuentaDeAna;

    @Autowired private AccountRepository cuentas;

    @BeforeEach
    void prepararEscenario() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto).apply(springSecurity()).build();

        movimientos.deleteAll();
        cuentas.deleteAll();
        asignaciones.deleteAll();
        programados.deleteAll();
        categorias.deleteAll();
        miembros.deleteAll();
        hogares.deleteAll();

        Household hogar = hogares.save(Household.builder().name("Casa de Ana").active(true).build());
        hogarDeAna = hogar.getId();
        miembros.save(HouseholdMember.builder()
                .householdId(hogarDeAna).userId(ANA).role("OWNER").active(true).build());

        Category cat = categorias.save(Category.builder()
                .userId(ANA).name("Arriendo de Ana").type(CategoryType.EXPENSE).active(true).build());
        categoriaDeAna = cat.getId();

        cuentaDeAna = cuentas.save(Account.builder()
                .userId(ANA).name("Ahorros de Ana").kind(AccountKind.OWN)
                .isDefault(true).active(true).build()).getId();

        Movement mov = movimientos.save(Movement.builder()
                .userId(ANA).accountId(cuentaDeAna).categoryId(categoriaDeAna).date(LocalDate.now())
                .amount(new BigDecimal("1500000")).description("Arriendo de Ana")
                .confirmed(true).active(true).isTransfer(false).build());
        movimientoDeAna = mov.getId();

        ScheduledMovement prog = programados.save(ScheduledMovement.builder()
                .userId(ANA).categoryId(categoriaDeAna).name("Arriendo mensual de Ana")
                .amount(new BigDecimal("1500000")).frequency(Frequency.MONTHLY)
                .startDate(LocalDate.now()).active(true).build());
        programadoDeAna = prog.getId();

        BudgetAllocation asig = asignaciones.save(BudgetAllocation.builder()
                .userId(ANA).categoryId(categoriaDeAna)
                .periodStart(LocalDate.now().withDayOfMonth(1))
                .periodEnd(LocalDate.now().withDayOfMonth(28))
                .allocatedAmount(new BigDecimal("2000000")).status("ACTIVE").active(true).build());
        asignacionDeAna = asig.getId();
    }

    /** Token legitimo de Bruno: mismo formato y misma firma que el de verdad. */
    private String tokenDe(Long userId, String email) {
        return "Bearer " + JWT.create()
                .withSubject(email)
                .withClaim("userId", userId)
                .withClaim("name", email)
                .withExpiresAt(new Date(System.currentTimeMillis() + 3_600_000))
                .sign(Algorithm.HMAC256(secreto));
    }

    private String tokenDeBruno() { return tokenDe(BRUNO, "bruno@ohchurus.com"); }

    private String atacar(String ruta, String cuerpo) throws Exception {
        MvcResult r = mvc.perform(post(ruta)
                        .header("Authorization", tokenDeBruno())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo == null ? "{}" : cuerpo))
                .andReturn();
        return r.getResponse().getContentAsString();
    }

    /** La respuesta no puede llevar dentro nada de Ana. */
    private void noFiltraDatosDeAna(String respuesta, String pista) {
        assertThat(respuesta)
                .as("la respuesta filtro datos de Ana: " + respuesta)
                .doesNotContain(pista);
    }

    // ========================================================================
    @Nested
    @DisplayName("Lectura")
    class Lectura {

        @Test
        @DisplayName("no puede leer un movimiento de Ana por su id")
        void movimiento() throws Exception {
            noFiltraDatosDeAna(atacar("/v1/movements/get/" + movimientoDeAna, null),
                    "Arriendo de Ana");
        }

        @Test
        @DisplayName("no puede leer una categoria de Ana por su id")
        void categoria() throws Exception {
            noFiltraDatosDeAna(atacar("/v1/categories/get/" + categoriaDeAna, null),
                    "Arriendo de Ana");
        }

        @Test
        @DisplayName("no puede leer un programado de Ana por su id")
        void programado() throws Exception {
            noFiltraDatosDeAna(atacar("/v1/scheduled/get/" + programadoDeAna, null),
                    "Arriendo mensual de Ana");
        }

        @Test
        @DisplayName("no puede listar los sub-movimientos de un movimiento de Ana")
        void hijos() throws Exception {
            movimientos.save(Movement.builder()
                    .userId(ANA).categoryId(categoriaDeAna).date(LocalDate.now())
                    .amount(new BigDecimal("500000")).description("Detalle privado de Ana")
                    .parentMovementId(movimientoDeAna).confirmed(true).active(true)
                    .isTransfer(false).build());
            noFiltraDatosDeAna(atacar("/v1/movements/children/" + movimientoDeAna, null),
                    "Detalle privado de Ana");
        }
    }

    // ========================================================================
    @Nested
    @DisplayName("Escritura")
    class Escritura {

        @Test
        @DisplayName("no puede borrar un movimiento de Ana")
        void borrarMovimiento() throws Exception {
            atacar("/v1/movements/delete/" + movimientoDeAna, null);
            assertThat(movimientos.findById(movimientoDeAna))
                    .as("el movimiento de Ana tenia que seguir activo")
                    .get().extracting(Movement::getActive).isEqualTo(true);
        }

        @Test
        @DisplayName("no puede borrar una categoria de Ana")
        void borrarCategoria() throws Exception {
            atacar("/v1/categories/delete/" + categoriaDeAna, null);
            assertThat(categorias.findById(categoriaDeAna))
                    .get().extracting(Category::getActive).isEqualTo(true);
        }

        @Test
        @DisplayName("no puede borrar un programado de Ana")
        void borrarProgramado() throws Exception {
            atacar("/v1/scheduled/delete/" + programadoDeAna, null);
            assertThat(programados.findById(programadoDeAna))
                    .get().extracting(ScheduledMovement::getActive).isEqualTo(true);
        }

        @Test
        @DisplayName("no puede materializar una ocurrencia de un programado de Ana")
        void materializarOcurrenciaAjena() throws Exception {
            /* "Materializar" crea un movimiento a nombre del DUENO del
               programado. Sin control de acceso, Bruno podria escribir gastos
               en la contabilidad de Ana con solo saber el id de su programado
               y una fecha, y ella los veria aparecer sin haberlos pedido. */
            long antes = movimientos.findAll().stream()
                    .filter(m -> programadoDeAna.equals(m.getScheduledMovementId())).count();

            atacar("/v1/scheduled/materialize",
                    "{\"occurrences\":[{\"scheduledMovementId\":" + programadoDeAna
                            + ",\"periodStart\":\"" + LocalDate.now().withDayOfMonth(1) + "\"}]}");

            assertThat(movimientos.findAll().stream()
                    .filter(m -> programadoDeAna.equals(m.getScheduledMovementId())).count())
                    .as("Bruno le escribio un movimiento a Ana en su propia contabilidad")
                    .isEqualTo(antes);
        }

        @Test
        @DisplayName("no puede borrar una asignacion de presupuesto de Ana")
        void borrarAsignacion() throws Exception {
            atacar("/v1/budget-allocation/delete/" + asignacionDeAna, null);
            assertThat(asignaciones.findById(asignacionDeAna))
                    .get().extracting(BudgetAllocation::getActive).isEqualTo(true);
        }

        @Test
        @DisplayName("no puede reescribir el importe de un movimiento de Ana al confirmarlo")
        void confirmarConOtroImporte() throws Exception {
            atacar("/v1/movements/confirm/" + movimientoDeAna, "{\"amount\":9999999}");
            assertThat(movimientos.findById(movimientoDeAna))
                    .as("le cambiaron el importe a Ana: su presupuesto queda corrupto")
                    .get().extracting(Movement::getAmount)
                    .isEqualTo(new BigDecimal("1500000.00"));
        }

        @Test
        @DisplayName("no puede robarse un movimiento de Ana mandando su id en save")
        void reasignarMovimiento() throws Exception {
            atacar("/v1/movements/save",
                    "{\"id\":" + movimientoDeAna + ",\"userId\":" + BRUNO
                            + ",\"categoryId\":" + categoriaDeAna
                            + ",\"date\":\"" + LocalDate.now() + "\",\"amount\":1,"
                            + "\"description\":\"mio ahora\"}");
            assertThat(movimientos.findById(movimientoDeAna))
                    .as("el movimiento cambio de dueno")
                    .get().extracting(Movement::getUserId).isEqualTo(ANA);
        }
    }

    // ========================================================================
    @Nested
    @DisplayName("Creacion: no se puede plantar nada dentro de la cuenta de otro")
    class Creacion {

        /* El ultimo hueco, y el mas silencioso: las lecturas y los borrados ya
           estaban cerrados, pero CREAR seguia tomando el dueno del cuerpo. Se
           demostro con trafico real contra el stack: Ana enviaba
           {"userId": <id de Bruno>} con su propio token y la categoria
           aparecia en el arbol de Bruno. No es solo un dato ajeno: es meterle
           a otro algo que no puso, y que ademas cuenta en sus cifras. */

        @Test
        @DisplayName("no puede crear una categoria a nombre de Ana")
        void categoria() throws Exception {
            atacar("/v1/categories/save",
                    "{\"userId\":" + ANA + ",\"name\":\"Plantada por Bruno\",\"type\":\"EXPENSE\"}");
            assertThat(categorias.findAll())
                    .as("aparecio una categoria a nombre de Ana que Ana no creo")
                    .noneMatch(c -> ANA.equals(c.getUserId())
                            && "Plantada por Bruno".equals(c.getName()));
        }

        @Test
        @DisplayName("no puede crear un movimiento a nombre de Ana")
        void movimiento() throws Exception {
            atacar("/v1/movements/save",
                    "{\"userId\":" + ANA + ",\"categoryId\":" + categoriaDeAna
                            + ",\"date\":\"" + LocalDate.now() + "\",\"amount\":1000,"
                            + "\"description\":\"Plantado por Bruno\"}");
            assertThat(movimientos.findAll())
                    .as("le aparecio a Ana un gasto que no hizo, y le descuadra el mes")
                    .noneMatch(m -> ANA.equals(m.getUserId())
                            && "Plantado por Bruno".equals(m.getDescription()));
        }

        @Test
        @DisplayName("no puede crear un programado a nombre de Ana")
        void programado() throws Exception {
            atacar("/v1/scheduled/save",
                    "{\"userId\":" + ANA + ",\"categoryId\":" + categoriaDeAna
                            + ",\"name\":\"Plantado por Bruno\",\"amount\":1000,"
                            + "\"frequency\":\"MONTHLY\",\"startDate\":\"" + LocalDate.now() + "\"}");
            assertThat(programados.findAll())
                    .noneMatch(p -> ANA.equals(p.getUserId())
                            && "Plantado por Bruno".equals(p.getName()));
        }

        @Test
        @DisplayName("no puede sacar plata del bote comun del hogar de Ana")
        void transferirDesdeElBoteAjeno() throws Exception {
            /*
             * Este caso faltaba, y lo delato el guardarrail: la comprobacion
             * se arreglo en la ronda de control (transfer exige ahora que el
             * hogar de la categoria de origen sea tuyo) pero nadie escribio la
             * prueba, asi que la ruta seguia figurando como deuda pendiente y
             * NingunEndpointSinDueno dejaba de exigirle un caso. Arreglo sin
             * prueba es arreglo que se puede deshacer sin enterarse.
             *
             * El ataque: Bruno tiene un token valido y conoce el id de la
             * categoria compartida del hogar de Ana —basta con que alguna vez
             * se la hayan compartido, o con probar ids—. Antes bastaba con que
             * la categoria de origen fuera COMPARTIDA; nadie miraba de quien
             * era el hogar. Bruno se pasaba el bote comun de otra pareja a su
             * bolsillo.
             */
            Long boteDeAna = categorias.save(Category.builder()
                    .userId(ANA).name("Bote comun de Ana").type(CategoryType.EXPENSE)
                    .householdId(hogarDeAna).active(true).build()).getId();
            Long bolsilloDeBruno = categorias.save(Category.builder()
                    .userId(BRUNO).name("Bolsillo de Bruno").type(CategoryType.INCOME)
                    .active(true).build()).getId();
            long antes = movimientos.count();

            atacar("/v1/movements/transfer",
                    "{\"fromCategoryId\":" + boteDeAna + ",\"toCategoryId\":" + bolsilloDeBruno
                            + ",\"amount\":400000,\"description\":\"robo\"}");

            assertThat(movimientos.count())
                    .as("Bruno se llevo plata del bote comun de la pareja de Ana")
                    .isEqualTo(antes);
        }

        @Test
        @DisplayName("no puede presupuestar sobre una categoria de Ana")
        void asignacion() throws Exception {
            /* Ana ya tiene 2.000.000 presupuestados en esta categoria. Como el
               guardado es un upsert por (categoria, periodo), el ataque no
               crearia una fila nueva: le REESCRIBIRIA la suya. Por eso se
               comprueba el importe y no el numero de filas. */
            atacar("/v1/budget-allocation/save",
                    "{\"categoryId\":" + categoriaDeAna + ",\"amount\":500000,\"budgetStartDay\":1}");
            assertThat(asignaciones.findById(asignacionDeAna))
                    .as("le reescribieron a Ana su presupuesto del mes")
                    .get().extracting(BudgetAllocation::getAllocatedAmount)
                    .isEqualTo(new BigDecimal("2000000.00"));
            assertThat(asignaciones.findAll())
                    .as("ademas le aparecio una asignacion nueva que no puso")
                    .hasSize(1);
        }
    }

    // ========================================================================
    @Nested
    @DisplayName("Nucleo familiar")
    class NucleoFamiliar {

        @Test
        @DisplayName("no puede meterse solo en el hogar de Ana")
        void autoinvitarse() throws Exception {
            atacar("/v1/household/add-member",
                    "{\"householdId\":" + hogarDeAna + ",\"userId\":" + BRUNO + "}");
            assertThat(miembros.existsByHouseholdIdAndUserIdAndActiveTrue(hogarDeAna, BRUNO))
                    .as("Bruno entro en el hogar de Ana y ve todas sus finanzas compartidas")
                    .isFalse();
        }

        @Test
        @DisplayName("no puede expulsar a Ana de su propio hogar")
        void expulsarAlDueno() throws Exception {
            atacar("/v1/household/remove-member",
                    "{\"householdId\":" + hogarDeAna + ",\"userId\":" + ANA + "}");
            assertThat(miembros.existsByHouseholdIdAndUserIdAndActiveTrue(hogarDeAna, ANA))
                    .as("echaron a Ana de su propia casa")
                    .isTrue();
        }
    }

    // ========================================================================
    @Nested
    @DisplayName("Listados y panel: pedir los de otro devuelve los tuyos, no los suyos")
    class Listados {

        /* Esta era la otra mitad de la fuga, y la mas silenciosa: no hacia
           falta adivinar el id de un movimiento. Bastaba con enviar
           {"userId": 1} al panel para recibir el resumen financiero completo
           de Ana. Ahora el userId del cuerpo se ignora y manda el token. */

        @Test
        @DisplayName("el panel de Ana no se puede pedir con su userId")
        void panel() throws Exception {
            noFiltraDatosDeAna(atacar("/v1/dashboard/summary",
                    "{\"userId\":" + ANA + ",\"budgetStartDay\":1}"), "1500000");
        }

        @Test
        @DisplayName("la lista de movimientos de Ana tampoco")
        void movimientos() throws Exception {
            noFiltraDatosDeAna(atacar("/v1/movements/all",
                    "{\"userId\":" + ANA + ",\"page\":0,\"size\":50}"), "Arriendo de Ana");
        }

        @Test
        @DisplayName("ni su arbol de categorias")
        void arbolDeCategorias() throws Exception {
            noFiltraDatosDeAna(atacar("/v1/categories/tree", "{\"userId\":" + ANA + "}"),
                    "Arriendo de Ana");
        }

        @Test
        @DisplayName("ni sus programados")
        void programados() throws Exception {
            noFiltraDatosDeAna(atacar("/v1/scheduled/all",
                    "{\"userId\":" + ANA + ",\"page\":0,\"size\":50}"), "Arriendo mensual de Ana");
        }

        @Test
        @DisplayName("ni sus movimientos por periodo")
        void porPeriodo() throws Exception {
            noFiltraDatosDeAna(atacar("/v1/movements/by-period",
                    "{\"userId\":" + ANA + ",\"startDate\":\"" + LocalDate.now().minusDays(30)
                            + "\",\"endDate\":\"" + LocalDate.now().plusDays(1) + "\"}"),
                    "Arriendo de Ana");
        }
    }

    // ========================================================================
    @Nested
    @DisplayName("Cuentas")
    class Cuentas {

        /*
         * La superficie mas nueva y la mas golosa: la cuenta dice CUANTA PLATA
         * tiene alguien. Leer el saldo de otra persona es peor que leerle un
         * gasto suelto, porque es el resumen de todos.
         */

        @Test
        @DisplayName("no puede ver el saldo de la cuenta de Ana")
        void leerCuenta() throws Exception {
            noFiltraDatosDeAna(atacar("/v1/accounts/get/" + cuentaDeAna, null), "Ahorros de Ana");
        }

        @Test
        @DisplayName("el listado de cuentas no incluye las de Ana")
        void listarCuentas() throws Exception {
            noFiltraDatosDeAna(atacar("/v1/accounts/all", "{}"), "Ahorros de Ana");
        }

        @Test
        @DisplayName("no puede renombrar ni reclasificar la cuenta de Ana")
        void editarCuenta() throws Exception {
            atacar("/v1/accounts/save",
                    "{\"id\":" + cuentaDeAna + ",\"name\":\"Secuestrada\",\"kind\":\"LIABILITY\"}");

            Account cuenta = cuentas.findById(cuentaDeAna).orElseThrow();
            assertThat(cuenta.getName())
                    .as("le renombraron la cuenta a Ana")
                    .isEqualTo("Ahorros de Ana");
            assertThat(cuenta.getUserId())
                    .as("la cuenta cambio de dueno")
                    .isEqualTo(ANA);
        }

        @Test
        @DisplayName("no puede borrar la cuenta de Ana")
        void borrarCuenta() throws Exception {
            atacar("/v1/accounts/delete/" + cuentaDeAna, null);
            assertThat(cuentas.findById(cuentaDeAna).orElseThrow().getActive())
                    .as("le desactivaron la cuenta a Ana y sus movimientos se quedan sin sitio")
                    .isTrue();
        }

        @Test
        @DisplayName("no puede conciliar la cuenta de Ana: eso le inventaria un movimiento")
        void conciliarCuenta() throws Exception {
            long antes = movimientos.count();
            atacar("/v1/accounts/reconcile",
                    "{\"accountId\":" + cuentaDeAna + ",\"realBalance\":1,\"apply\":true}");

            assertThat(movimientos.count())
                    .as("conciliar la cuenta de otro le mete un ajuste dentro de sus cifras")
                    .isEqualTo(antes);
        }

        @Test
        @DisplayName("no puede meter un movimiento suyo dentro de la cuenta de Ana")
        void colarMovimientoEnCuentaAjena() throws Exception {
            /* La variante de cuentas del agujero que costo dos olas: ya no se
               puede mandar un userId ajeno, pero mandar un accountId ajeno
               conseguiria lo mismo por otra columna — descuadrarle el saldo a
               otra persona con un gasto que no hizo. */
            atacar("/v1/movements/save",
                    "{\"categoryId\":" + categoriaDeAna + ",\"accountId\":" + cuentaDeAna
                            + ",\"date\":\"" + LocalDate.now() + "\",\"amount\":99999,"
                            + "\"description\":\"colado\"}");

            assertThat(movimientos.findByAccountIdAndActiveTrue(cuentaDeAna))
                    .as("a Ana le aparecio en su cuenta un movimiento que no hizo")
                    .noneMatch(m -> "colado".equals(m.getDescription()));
        }
    }

    // ========================================================================
    @Nested
    @DisplayName("Reparto")
    class Reparto {

        /*
         * El reparto abre una puerta que las demas funcionalidades no tenian:
         * hasta ahora todo se resumia en "no toques lo de otro". Aqui hay algo
         * peor de lo que se puede hacer con un id ajeno, y es METERLE una
         * deuda a alguien que no ha hecho nada. Por eso repartir exige
         * compartir hogar.
         */

        @Test
        @DisplayName("no puede meterle una deuda a un desconocido usando su id")
        void noSeInventanDeudasAjenas() throws Exception {
            /* Bruno no comparte hogar con Ana. Si esto pasara, a Ana le
               apareceria en su pantalla que le debe 999.999 a un extrano. */
            Long suya = categorias.save(Category.builder()
                    .userId(BRUNO).name("De Bruno").type(CategoryType.EXPENSE).active(true).build())
                    .getId();

            String r = atacar("/v1/movements/save",
                    "{\"categoryId\":" + suya + ",\"date\":\"" + LocalDate.now()
                            + "\",\"amount\":999999,\"splitMode\":\"EQUAL\",\"splits\":["
                            + "{\"participantId\":" + BRUNO + "},{\"participantId\":" + ANA + "}]}");

            assertThat(r)
                    .as("le planto una deuda a Ana sin que ella hiciera nada")
                    .contains("\"correct\":false");
        }

        @Test
        @DisplayName("los balances que ve son los suyos, no los de Ana")
        void balancesPropios() throws Exception {
            noFiltraDatosDeAna(atacar("/v1/splits/balances", "{\"userId\":" + ANA + "}"),
                    "Arriendo de Ana");
        }

        @Test
        @DisplayName("no puede liquidar con alguien de fuera de su hogar")
        void noLiquidaConExtranos() throws Exception {
            long antes = movimientos.count();
            atacar("/v1/splits/settle", "{\"withUserId\":" + ANA + ",\"amount\":500000}");

            assertThat(movimientos.count())
                    .as("liquidar con un extrano le escribe un movimiento a nombre de la "
                            + "relacion que no existe, y le mueve el balance a la otra persona")
                    .isEqualTo(antes);
        }
    }

    // ========================================================================
    @Test
    @DisplayName("sin token no se llega a nada")
    void sinToken() throws Exception {
        MvcResult r = mvc.perform(post("/v1/movements/get/" + movimientoDeAna)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andReturn();
        assertThat(r.getResponse().getContentAsString())
                .as("se puede leer sin autenticarse")
                .doesNotContain("Arriendo de Ana");
    }
}

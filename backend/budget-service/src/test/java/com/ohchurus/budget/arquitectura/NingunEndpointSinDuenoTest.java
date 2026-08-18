package com.ohchurus.budget.arquitectura;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Controller;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ============================================================================
 * NINGUN ENDPOINT SIN DUENO
 * ============================================================================
 *
 * La matriz de aislamiento cubre una parte de la superficie HTTP. El problema
 * nunca fue cuanta: fue que manana alguien anade el endpoint 57 y NADA le
 * obliga a cubrirlo. Un endpoint nuevo que reciba un id y no mire de quien es
 * vuelve a abrir el mismo agujero que costo dos olas cerrar, y la construccion
 * seguiria en verde.
 *
 * ESTA PRUEBA NO COMPRUEBA SEGURIDAD: comprueba que se haya DECIDIDO algo.
 * Recorre por reflexion todos los mapeos de todos los controllers y exige que
 * cada ruta este en uno de estos tres sitios:
 *
 *   1. EN LA MATRIZ. No se escribe a mano: se lee del fichero fuente de
 *      AislamientoEntreUsuariosTest. Si alguien borra un caso de la matriz, la
 *      ruta se queda sin clasificar y esto se pone rojo.
 *   2. EXENTA, con el motivo escrito al lado. Publica, catalogo estatico, o
 *      defendida por la misma comprobacion que una ruta que SI esta en la
 *      matriz (y entonces hay que nombrarla: se verifica que exista alli).
 *   3. PENDIENTE, con el ataque concreto escrito al lado. Es deuda conocida y
 *      visible, no un descuido.
 *
 * Una ruta nueva no cae en ninguno de los tres y la construccion se para.
 */
@DisplayName("Ningun endpoint sin dueno: toda la superficie HTTP esta clasificada")
class NingunEndpointSinDuenoTest {

    private static final String PAQUETE = "com.ohchurus.budget";

    /** La matriz de aislamiento de este servicio. Se lee, no se copia. */
    private static final String MATRIZ =
            "src/test/java/com/ohchurus/budget/security/AislamientoEntreUsuariosTest.java";

    /**
     * Rutas que no necesitan un caso en la matriz, con el motivo al lado.
     *
     * "representante" es la ruta de la matriz que ya demuestra la defensa de
     * esta: si se nombra una, tiene que estar de verdad en la matriz.
     */
    private static final Map<String, Exencion> EXENTAS = new LinkedHashMap<>();
    static {
        // --- Catalogos: la respuesta es la misma para todo el mundo ---
        EXENTAS.put("/v1/categories/type-list", new Exencion(
                "catalogo de tipos de categoria: constantes del enum, no hay datos de nadie", null));
        EXENTAS.put("/v1/scheduled/frequency-list", new Exencion(
                "catalogo de frecuencias: constantes del enum, no hay datos de nadie", null));
        EXENTAS.put("/v1/accounts/kind-list", new Exencion(
                "catalogo de clases de cuenta: constantes del enum, no hay datos de nadie", null));

        // --- No reciben ningun id ajeno: el userId sale del token y el cuerpo se ignora ---
        // El panel es el representante de esta familia porque fue el caso real:
        // enviar {"userId": 1} devolvia el resumen financiero completo de Ana.
        EXENTAS.put("/v1/dashboard/by-category", new Exencion(
                "no recibe ningun id: el userId sale del token y el del cuerpo se ignora",
                "/v1/dashboard/summary"));
        EXENTAS.put("/v1/dashboard/trend", new Exencion(
                "no recibe ningun id: el userId sale del token y el del cuerpo se ignora",
                "/v1/dashboard/summary"));
        EXENTAS.put("/v1/dashboard/pending", new Exencion(
                "no recibe ningun id: el userId sale del token y el del cuerpo se ignora",
                "/v1/dashboard/summary"));
        EXENTAS.put("/v1/dashboard/split-summary", new Exencion(
                "no recibe ningun id: el userId sale del token y el del cuerpo se ignora",
                "/v1/dashboard/summary"));
        EXENTAS.put("/v1/dashboard/consolidated", new Exencion(
                "no recibe ningun id: el userId sale del token y el del cuerpo se ignora",
                "/v1/dashboard/summary"));
        EXENTAS.put("/v1/export/excel", new Exencion(
                "mismo cuerpo y misma identidad que el panel: exporta el periodo de quien pide",
                "/v1/dashboard/summary"));
        EXENTAS.put("/v1/budget-allocation/list", new Exencion(
                "no recibe ningun id: el userId sale del token y el del cuerpo se ignora",
                "/v1/dashboard/summary"));
        EXENTAS.put("/v1/budget-allocation/summary", new Exencion(
                "no recibe ningun id: el userId sale del token y el del cuerpo se ignora",
                "/v1/dashboard/summary"));
        EXENTAS.put("/v1/budget-allocation/envelopes", new Exencion(
                "no recibe ningun id: los sobres son los de quien pide, y del cuerpo solo "
                        + "se miran el dia de corte y la fecha",
                "/v1/dashboard/summary"));
        EXENTAS.put("/v1/categories/all", new Exencion(
                "el controller pisa el userId del filtro con el del token antes de consultar",
                "/v1/categories/tree"));
        EXENTAS.put("/v1/scheduled/generate-pending", new Exencion(
                "genera los pendientes de quien pide: el userId sale del token",
                "/v1/scheduled/all"));
        EXENTAS.put("/v1/household/by-user", new Exencion(
                "devuelve los hogares de quien pide: el userId del cuerpo se ignora",
                "/v1/dashboard/summary"));
        EXENTAS.put("/v1/household/create", new Exencion(
                "crea un hogar nuevo cuyo OWNER es quien llama; no toca nada existente", null));

        // --- Reciben un id ajeno, pero por la MISMA puerta que una ruta de la matriz ---
        EXENTAS.put("/v1/household/invite", new Exencion(
                "invitar por correo resuelve el id y delega en addMember: misma exigencia "
                        + "de ser OWNER del hogar, comprobada en el mismo metodo",
                "/v1/household/add-member"));
    }

    /**
     * Deuda conocida: rutas que SI reciben un identificador que puede ser de
     * otra persona y todavia no tienen su caso en la matriz.
     *
     * Estan aqui escritas con el ataque concreto para que se puedan cerrar,
     * no para que se olviden. Cerrar una de estas exige tocar el servicio, no
     * la prueba: primero se anade el caso a la matriz, se ve rojo, se arregla
     * el codigo y se borra la linea de aqui.
     */
    private static final Map<String, String> PENDIENTES = new LinkedHashMap<>();
    static {
        /*
         * Vacia, y que siga asi.
         *
         * Aqui vivieron cuatro: budget-allocation/save, movements/transfer,
         * scheduled/save y categories/save. Las cuatro se cerraron en la ronda
         * de control y tienen su caso en la seccion "Creacion" de la matriz.
         *
         * Se borran de aqui porque una lista de deuda que nombra deuda ya
         * pagada es peor que no tenerla: la lees, te crees que hay cuatro
         * agujeros abiertos, y dejas de creerte la lista entera. Lo vigila
         * laDeudaNoSePudre, que se pone roja si una ruta esta a la vez en esta
         * lista y en la matriz.
         */
    }

    /** Que hacer cuando esto se pone rojo. Se imprime con el fallo a proposito. */
    private static final String COMO_ARREGLARLO =
            "\n\n  QUE HACER: cada ruta de arriba es superficie HTTP que nadie ha clasificado."
                    + "\n  Elige una de las tres:"
                    + "\n    a) anade su caso a " + MATRIZ
                    + "\n       (Bruno, con token valido, intenta llegar al dato de Ana y no lo consigue);"
                    + "\n    b) si es publica o es un catalogo, anadela a EXENTAS en este fichero"
                    + "\n       con el motivo escrito al lado;"
                    + "\n    c) si recibe un id ajeno y aun no esta defendida, anadela a PENDIENTES"
                    + "\n       describiendo el ataque concreto."
                    + "\n  Esta prueba existe porque la matriz cubria 24 de 56 endpoints y nada"
                    + "\n  obligaba al 57 a estar cubierto.\n";

    // ========================================================================

    @Test
    @DisplayName("toda ruta esta en la matriz, exenta con motivo, o anotada como deuda")
    void todaLaSuperficieHttpEstaClasificada() {
        Set<String> matriz = rutasQueEjercitaLaMatriz();

        List<String> sinClasificar = new ArrayList<>();
        for (Ruta ruta : rutasDeLosControllers()) {
            if (estaEnLaMatriz(ruta.clave(), matriz)) continue;
            if (EXENTAS.containsKey(ruta.clave())) continue;
            if (PENDIENTES.containsKey(ruta.clave())) continue;
            sinClasificar.add(ruta.descripcion());
        }

        assertThat(sinClasificar)
                .as("hay endpoints sin dueno" + COMO_ARREGLARLO)
                .isEmpty();
    }

    @Test
    @DisplayName("la matriz de aislamiento sigue en su sitio y ejercita rutas")
    void laMatrizSigueEnSuSitio() {
        /* Si alguien mueve o renombra la matriz, el resto de esta prueba se
           quedaria sin nada contra lo que comparar y pasaria en verde diciendo
           que todo esta cubierto. */
        assertThat(rutasQueEjercitaLaMatriz())
                .as("no encuentro rutas en " + MATRIZ + ": ¿se movio la matriz de aislamiento? "
                        + "Actualiza la constante MATRIZ de este fichero")
                .isNotEmpty();
    }

    @Test
    @DisplayName("cada exencion que se apoya en otra ruta la nombra, y esa ruta esta en la matriz")
    void lasExencionesSeApoyanEnAlgoQueExiste() {
        Set<String> matriz = rutasQueEjercitaLaMatriz();

        List<String> rotas = new ArrayList<>();
        EXENTAS.forEach((ruta, exencion) -> {
            if (exencion.representante() == null) return;
            if (!estaEnLaMatriz(exencion.representante(), matriz)) {
                rotas.add(ruta + " dice apoyarse en " + exencion.representante()
                        + ", que ya no esta en la matriz");
            }
        });

        assertThat(rotas)
                .as("una exencion se apoya en una ruta que la matriz ya no ejercita: la exencion "
                        + "se quedo sin fundamento, o vuelve a poner ese caso en la matriz o "
                        + "escribe el suyo propio")
                .isEmpty();
    }

    @Test
    @DisplayName("la deuda no se pudre: nada figura como pendiente si ya esta en la matriz")
    void laDeudaNoSePudre() {
        /*
         * Cerrar un agujero y olvidarse de borrar su linea de PENDIENTES deja
         * el fichero diciendo que sigue abierto. Paso de verdad: las cuatro
         * entradas que habia aqui se arreglaron en la ronda de control y
         * siguieron listadas como deuda durante toda esa ronda.
         *
         * Y hay algo peor que la mentira: mientras una ruta figure en
         * PENDIENTES, la comprobacion principal la da por clasificada y deja
         * de exigirle un caso. Una deuda vieja se convierte en un permiso
         * permanente para no probar esa ruta.
         */
        Set<String> matriz = rutasQueEjercitaLaMatriz();

        List<String> yaCerradas = new ArrayList<>();
        PENDIENTES.keySet().forEach(ruta -> {
            if (estaEnLaMatriz(ruta, matriz)) {
                yaCerradas.add(ruta + " figura como deuda pero la matriz ya la ejercita");
            }
        });

        assertThat(yaCerradas)
                .as("borra estas lineas de PENDIENTES: describen agujeros que ya estan cerrados, "
                        + "y mientras esten ahi esta prueba no le exigira un caso a esa ruta")
                .isEmpty();
    }

    @Test
    @DisplayName("el inventario no tiene rutas fantasma")
    void elInventarioNoTieneRutasFantasma() {
        /* Una exencion sobre una ruta que ya no existe es una lista que se
           pudre: el dia que vuelva a existir una ruta con ese nombre, entraria
           exenta sin que nadie lo decida. */
        Set<String> reales = new LinkedHashSet<>();
        rutasDeLosControllers().forEach(r -> reales.add(r.clave()));

        List<String> fantasmas = new ArrayList<>();
        EXENTAS.keySet().forEach(r -> { if (!reales.contains(r)) fantasmas.add("EXENTAS: " + r); });
        PENDIENTES.keySet().forEach(r -> { if (!reales.contains(r)) fantasmas.add("PENDIENTES: " + r); });

        assertThat(fantasmas)
                .as("estas rutas ya no existen en ningun controller: borralas del inventario "
                        + "de este fichero")
                .isEmpty();
    }

    // ========================================================================
    // Herramientas

    /** Un mapeo de un controller, ya resuelto a ruta completa. */
    private record Ruta(String verbo, String ruta, String clase, String metodo) {
        /** La ruta sin la parte variable: /v1/movements/get/{id} -> /v1/movements/get/ */
        String clave() {
            int llave = ruta.indexOf('{');
            return llave < 0 ? ruta : ruta.substring(0, llave);
        }

        String descripcion() {
            return verbo + " " + ruta + "  (" + clase + "#" + metodo + ")";
        }
    }

    private record Exencion(String motivo, String representante) { }

    private static List<Ruta> rutasDeLosControllers() {
        ClassPathScanningCandidateComponentProvider escaner =
                new ClassPathScanningCandidateComponentProvider(false);
        escaner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        escaner.addIncludeFilter(new AnnotationTypeFilter(Controller.class));

        List<Ruta> rutas = new ArrayList<>();
        for (BeanDefinition definicion : escaner.findCandidateComponents(PAQUETE)) {
            Class<?> controller = ClassUtils.resolveClassName(definicion.getBeanClassName(), null);
            RequestMapping raiz = AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class);
            List<String> prefijos = caminos(raiz);

            for (Method metodo : controller.getDeclaredMethods()) {
                RequestMapping mapeo = AnnotatedElementUtils.findMergedAnnotation(metodo, RequestMapping.class);
                if (mapeo == null) continue;
                String verbo = mapeo.method().length == 0 ? "ANY" : mapeo.method()[0].name();
                for (String prefijo : prefijos) {
                    for (String sufijo : caminos(mapeo)) {
                        rutas.add(new Ruta(verbo, prefijo + sufijo,
                                controller.getSimpleName(), metodo.getName()));
                    }
                }
            }
        }
        return rutas;
    }

    private static List<String> caminos(RequestMapping mapeo) {
        Set<String> caminos = new LinkedHashSet<>();
        if (mapeo != null) {
            caminos.addAll(Arrays.asList(mapeo.path()));
            caminos.addAll(Arrays.asList(mapeo.value()));
        }
        if (caminos.isEmpty()) caminos.add("");
        return new ArrayList<>(caminos);
    }

    /** Las rutas que la matriz ataca de verdad, leidas de su codigo fuente. */
    private static Set<String> rutasQueEjercitaLaMatriz() {
        Matcher literal = Pattern.compile("\"(/v1/[^\"]*)\"").matcher(leer(MATRIZ));
        Set<String> rutas = new LinkedHashSet<>();
        while (literal.find()) rutas.add(literal.group(1));
        return rutas;
    }

    /**
     * La matriz escribe las rutas con id como "/v1/movements/get/" + id, asi
     * que para las rutas con {variable} basta con que el literal empiece igual.
     */
    private static boolean estaEnLaMatriz(String clave, Set<String> matriz) {
        if (matriz.contains(clave)) return true;
        return clave.endsWith("/") && matriz.stream().anyMatch(l -> l.startsWith(clave));
    }

    private static String leer(String rutaRelativa) {
        Path fichero = raizDelModulo().resolve(rutaRelativa);
        try {
            return Files.readString(fichero, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("No puedo leer " + fichero, e);
        }
    }

    /** Maven lanza las pruebas desde la raiz del modulo; el IDE, a veces no. */
    private static Path raizDelModulo() {
        Path actual = Paths.get("").toAbsolutePath();
        for (Path candidato : List.of(actual,
                actual.resolve("budget-service"),
                actual.resolve("backend/budget-service"))) {
            if (Files.isDirectory(candidato.resolve("src/main/java/com/ohchurus/budget"))) {
                return candidato;
            }
        }
        throw new IllegalStateException("No encuentro la raiz de budget-service desde " + actual);
    }
}

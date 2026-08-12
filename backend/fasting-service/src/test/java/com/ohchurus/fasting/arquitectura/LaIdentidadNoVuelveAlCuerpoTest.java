package com.ohchurus.fasting.arquitectura;

import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.util.ClassUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ============================================================================
 * LA IDENTIDAD NO VUELVE AL CUERPO
 * ============================================================================
 *
 * Quien eres lo dice el token, nunca la peticion. Este servicio fue el ultimo
 * en enterarse: sus trece endpoints leian body.getUserId() cuando budget ya
 * estaba arreglado, y con eso la frase "la identidad sale del JWT" era falsa en
 * la mitad del producto.
 *
 * Vigila las dos formas que tiene la identidad de volver al cuerpo:
 *
 *   1. Un @NotNull sobre un campo userId de un DTO de entrada.
 *   2. Un body.get("userId") o un dto.getUserId() dentro de un controller: el
 *      controller haciendo caso de lo que el cliente dice ser. Eso es lo que
 *      de verdad decide, y aqui ya no ocurre en ningun sitio.
 *
 * SOBRE LA DEUDA CONGELADA
 * ------------------------
 * Los DTO de ayuno todavia declaran @NotNull sobre userId. Ya no hace dano
 * —el controller no lo mira, la identidad sale de SecurityUtils— pero obliga
 * al frontend a seguir mandando un campo que se tira, y es una senal
 * equivocada para quien lea el DTO manana y piense que ese numero significa
 * algo. Estan listados uno a uno en DEUDA_CONGELADA: la lista solo puede
 * encoger. Si aparece un DTO nuevo con @NotNull sobre userId, esto se pone
 * rojo; si alguien limpia uno de los listados, tambien, para que se borre la
 * linea y la deuda se vea bajar.
 */
@DisplayName("La identidad no vuelve al cuerpo: el userId sale del token")
class LaIdentidadNoVuelveAlCuerpoTest {

    private static final String PAQUETE_DTOS = "com.ohchurus.fasting.dto.input";
    private static final String CONTROLLERS = "src/main/java/com/ohchurus/fasting/controller";

    /** DTOs a los que se les permite exigir un userId por un motivo de fondo. Ninguno. */
    private static final Map<String, String> DTOS_EXENTOS = new LinkedHashMap<>();

    /**
     * DTOs que todavia lo exigen por compatibilidad con el frontend, no porque
     * el servidor lo use. Deuda visible y congelada: no puede crecer.
     */
    private static final Map<String, String> DEUDA_CONGELADA = new LinkedHashMap<>();
    static {
        String motivo = "el controller ya lo ignora (usa SecurityUtils); el @NotNull sobrevive "
                + "porque el frontend sigue enviando el campo y quitarlo es un cambio de contrato";
        DEUDA_CONGELADA.put("AguaMetaDTO", motivo);
        DEUDA_CONGELADA.put("AguaVasosDTO", motivo);
        DEUDA_CONGELADA.put("AyunoFinDTO", motivo);
        DEUDA_CONGELADA.put("AyunoInicioDTO", motivo);
        DEUDA_CONGELADA.put("AyunoPeriodoDTO", motivo);
        DEUDA_CONGELADA.put("AyunoPlanDTO", motivo);
        DEUDA_CONGELADA.put("AyunoUsuarioDTO", motivo);
    }

    /**
     * Controllers a los que se les permite leer el userId del cuerpo, con el
     * numero exacto de veces. El numero es el candado: una lectura mas y esto
     * se pone rojo aunque el fichero ya estuviera en la lista. Vacio: aqui ya
     * no lo lee nadie, y esa es la parte que de verdad protege.
     */
    private static final Map<String, Permiso> CONTROLLERS_EXENTOS = new LinkedHashMap<>();

    private static final String COMO_ARREGLARLO =
            "\n\n  QUE HACER: el userId lo pone el token, no el cliente."
                    + "\n  Usa SecurityUtils.getAuthenticatedUserId() en el controller y deja el"
                    + "\n  campo del DTO sin @NotNull (el frontend puede seguir enviandolo: se ignora)."
                    + "\n  La lista DEUDA_CONGELADA de este fichero no es un sitio donde apuntarse:"
                    + "\n  esta congelada a proposito y solo puede encoger.\n";

    // ========================================================================

    @Test
    @DisplayName("ningun DTO de entrada nuevo exige un userId")
    void ningunDtoExigeElUserId() {
        List<String> culpables = new ArrayList<>();
        for (Class<?> dto : dtosDeEntrada()) {
            String nombre = dto.getSimpleName();
            if (DTOS_EXENTOS.containsKey(nombre) || DEUDA_CONGELADA.containsKey(nombre)) continue;
            for (Field campo : dto.getDeclaredFields()) {
                if ("userId".equals(campo.getName()) && campo.isAnnotationPresent(NotNull.class)) {
                    culpables.add(nombre + ".userId");
                }
            }
        }

        assertThat(culpables)
                .as("estos DTO obligan al cliente a declarar quien es" + COMO_ARREGLARLO)
                .isEmpty();
    }

    @Test
    @DisplayName("la deuda congelada solo puede encoger")
    void laDeudaCongeladaSoloEncoge() {
        /* Cuando alguien limpie uno de estos DTO, esto se pone rojo con una
           buena noticia: hay que borrar la linea. Es la unica forma de que una
           lista de deuda no se convierta en un cajon donde todo cabe. */
        List<String> yaLimpios = new ArrayList<>();
        for (String nombre : DEUDA_CONGELADA.keySet()) {
            if (!exigeElUserId(nombre)) yaLimpios.add(nombre);
        }

        assertThat(yaLimpios)
                .as("buena noticia: estos DTO ya no exigen el userId. Borra su linea de "
                        + "DEUDA_CONGELADA en este fichero para que la deuda se vea bajar")
                .isEmpty();
    }

    @Test
    @DisplayName("los DTO exentos siguen siendolo por el motivo escrito")
    void lasExencionesDeDtoNoSePudren() {
        List<String> obsoletos = new ArrayList<>();
        for (String nombre : DTOS_EXENTOS.keySet()) {
            if (!exigeElUserId(nombre)) obsoletos.add(nombre);
        }

        assertThat(obsoletos)
                .as("buena noticia: estos DTO ya no exigen el userId. Borra su linea de "
                        + "DTOS_EXENTOS en este fichero")
                .isEmpty();
    }

    @Test
    @DisplayName("ningun controller lee el userId del cuerpo")
    void ningunControllerLeeElUserIdDelCuerpo() {
        List<String> culpables = new ArrayList<>();

        for (Path fichero : ficherosDeControllers()) {
            String nombre = fichero.getFileName().toString();
            int lecturas = vecesQueLeeElUserId(sinComentarios(leer(fichero)));
            int permitidas = CONTROLLERS_EXENTOS.containsKey(nombre)
                    ? CONTROLLERS_EXENTOS.get(nombre).veces() : 0;

            if (lecturas > permitidas) {
                culpables.add(nombre + ": lee el userId del cuerpo " + lecturas
                        + " veces y solo hay " + permitidas + " justificadas");
            }
        }

        assertThat(culpables)
                .as("un controller volvio a creerse lo que el cliente dice ser" + COMO_ARREGLARLO)
                .isEmpty();
    }

    @Test
    @DisplayName("las lecturas justificadas siguen siendo exactamente las que dice la lista")
    void lasExencionesDeControllerNoSePudren() {
        List<String> desajustes = new ArrayList<>();

        CONTROLLERS_EXENTOS.forEach((nombre, permiso) -> {
            Path fichero = raizDelModulo().resolve(CONTROLLERS).resolve(nombre);
            if (!Files.exists(fichero)) {
                desajustes.add(nombre + " ya no existe: borra su linea de CONTROLLERS_EXENTOS");
                return;
            }
            int lecturas = vecesQueLeeElUserId(sinComentarios(leer(fichero)));
            if (lecturas < permiso.veces()) {
                desajustes.add(nombre + " ya solo lo lee " + lecturas + " veces (la lista dice "
                        + permiso.veces() + "): baja el numero o borra la linea");
            }
        });

        assertThat(desajustes)
                .as("el candado de las exenciones quedo por encima de la realidad y deja hueco "
                        + "para una lectura nueva sin que nadie se entere")
                .isEmpty();
    }

    // ========================================================================
    // Herramientas

    private record Permiso(int veces, String motivo) { }

    private static boolean exigeElUserId(String nombreDeClase) {
        return dtosDeEntrada().stream()
                .filter(d -> d.getSimpleName().equals(nombreDeClase))
                .flatMap(d -> Stream.of(d.getDeclaredFields()))
                .anyMatch(c -> "userId".equals(c.getName()) && c.isAnnotationPresent(NotNull.class));
    }

    private static List<Class<?>> dtosDeEntrada() {
        ClassPathScanningCandidateComponentProvider escaner =
                new ClassPathScanningCandidateComponentProvider(false);
        escaner.addIncludeFilter((lector, fabrica) -> true);

        List<Class<?>> dtos = new ArrayList<>();
        for (BeanDefinition definicion : escaner.findCandidateComponents(PAQUETE_DTOS)) {
            dtos.add(ClassUtils.resolveClassName(definicion.getBeanClassName(), null));
        }
        return dtos;
    }

    private static List<Path> ficherosDeControllers() {
        Path carpeta = raizDelModulo().resolve(CONTROLLERS);
        try (Stream<Path> ficheros = Files.list(carpeta)) {
            return ficheros.filter(f -> f.toString().endsWith(".java")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException("No puedo listar " + carpeta, e);
        }
    }

    /** body.get("userId") y dto.getUserId(): las dos formas de preguntarselo al cliente. */
    private static int vecesQueLeeElUserId(String codigo) {
        Matcher lectura = Pattern.compile("\\.get\\(\\s*\"userId\"\\s*\\)|\\.getUserId\\(\\s*\\)")
                .matcher(codigo);
        int veces = 0;
        while (lectura.find()) veces++;
        return veces;
    }

    /** Los comentarios de este proyecto CUENTAN la historia vieja; no son codigo. */
    private static String sinComentarios(String codigo) {
        return codigo.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("//[^\n]*", " ");
    }

    private static String leer(Path fichero) {
        try {
            return Files.readString(fichero, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("No puedo leer " + fichero, e);
        }
    }

    private static Path raizDelModulo() {
        Path actual = Paths.get("").toAbsolutePath();
        for (Path candidato : List.of(actual,
                actual.resolve("fasting-service"),
                actual.resolve("backend/fasting-service"))) {
            if (Files.isDirectory(candidato.resolve("src/main/java/com/ohchurus/fasting"))) {
                return candidato;
            }
        }
        throw new IllegalStateException("No encuentro la raiz de fasting-service desde " + actual);
    }
}

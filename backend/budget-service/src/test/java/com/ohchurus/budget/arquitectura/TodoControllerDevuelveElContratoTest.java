package com.ohchurus.budget.arquitectura;

import com.ohchurus.budget.dto.output.ResultDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ============================================================================
 * TODO CONTROLLER DEVUELVE EL CONTRATO
 * ============================================================================
 *
 * La API entera habla ResultDTO con HTTP 200 y el frontend SOLO sabe leer eso:
 * mira "correct" y, si es false, ensena "message". Un metodo que devuelva otra
 * cosa —un Map suelto, la entidad, un 500 con el cuerpo vacio— no rompe ninguna
 * prueba del servicio y sin embargo deja al usuario delante de una pantalla en
 * blanco o de un "Request failed with status code 500".
 *
 * Ya paso: la descarga del Excel devolvia internalServerError().build(), un 500
 * sin un solo byte de explicacion. ContratoDeErroresTest defiende el contrato
 * cuando algo falla; esta prueba lo defiende antes, en la firma del metodo, y
 * cubre TODOS los controllers en vez de los que alguien se acuerde de anadir.
 */
@DisplayName("Todo controller devuelve ResponseEntity<ResultDTO>")
class TodoControllerDevuelveElContratoTest {

    private static final String PAQUETE = "com.ohchurus.budget";

    /** Metodos que devuelven otra cosa, con el motivo al lado. */
    private static final Map<String, String> EXENTOS = new LinkedHashMap<>();
    static {
        EXENTOS.put("ExportController#exportExcel",
                "la descarga devuelve los bytes del .xlsx, no JSON; por eso es Object: "
                        + "el camino feliz son bytes y el de error, un ResultDTO en JSON");
    }

    private static final String COMO_ARREGLARLO =
            "\n\n  QUE HACER: devuelve ResponseEntity<ResultDTO>. El frontend solo sabe leer eso"
                    + "\n  (mira \"correct\" y ensena \"message\"); cualquier otra forma le llega como una"
                    + "\n  pantalla en blanco. Si de verdad no es JSON —una descarga— anade el metodo"
                    + "\n  a EXENTOS en este fichero con el motivo escrito al lado.\n";

    // ========================================================================

    @Test
    @DisplayName("ningun metodo de controller se sale del contrato")
    void ningunMetodoSeSaleDelContrato() {
        List<String> culpables = new ArrayList<>();

        for (Method metodo : metodosExpuestos()) {
            String nombre = metodo.getDeclaringClass().getSimpleName() + "#" + metodo.getName();
            if (EXENTOS.containsKey(nombre)) continue;
            if (!devuelveElContrato(metodo)) {
                culpables.add(nombre + " devuelve " + metodo.getGenericReturnType().getTypeName());
            }
        }

        assertThat(culpables)
                .as("estos endpoints hablan un idioma que el frontend no entiende" + COMO_ARREGLARLO)
                .isEmpty();
    }

    @Test
    @DisplayName("las exenciones siguen existiendo y siguen siendo excepciones")
    void lasExencionesNoSePudren() {
        /* Una exencion sobre un metodo que ya no existe —o que ya devuelve el
           contrato— es un hueco abierto esperando a que alguien reutilice el
           nombre. */
        List<String> nombresReales = new ArrayList<>();
        for (Method metodo : metodosExpuestos()) {
            nombresReales.add(metodo.getDeclaringClass().getSimpleName() + "#" + metodo.getName());
        }

        List<String> obsoletas = new ArrayList<>();
        for (String exento : EXENTOS.keySet()) {
            if (!nombresReales.contains(exento)) {
                obsoletas.add(exento + " ya no existe");
            }
        }

        assertThat(obsoletas)
                .as("borra estas lineas de EXENTOS en este fichero: ya no protegen nada")
                .isEmpty();
    }

    // ========================================================================
    // Herramientas

    /** ResponseEntity<ResultDTO>, exactamente. */
    private static boolean devuelveElContrato(Method metodo) {
        if (!ResponseEntity.class.equals(metodo.getReturnType())) return false;
        Type devuelto = metodo.getGenericReturnType();
        if (!(devuelto instanceof ParameterizedType parametrizado)) return false;
        Type[] argumentos = parametrizado.getActualTypeArguments();
        return argumentos.length == 1 && ResultDTO.class.equals(argumentos[0]);
    }

    private static List<Method> metodosExpuestos() {
        ClassPathScanningCandidateComponentProvider escaner =
                new ClassPathScanningCandidateComponentProvider(false);
        escaner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        escaner.addIncludeFilter(new AnnotationTypeFilter(Controller.class));

        List<Method> metodos = new ArrayList<>();
        for (BeanDefinition definicion : escaner.findCandidateComponents(PAQUETE)) {
            Class<?> controller = ClassUtils.resolveClassName(definicion.getBeanClassName(), null);
            for (Method metodo : controller.getDeclaredMethods()) {
                if (AnnotatedElementUtils.findMergedAnnotation(metodo, RequestMapping.class) != null) {
                    metodos.add(metodo);
                }
            }
        }
        return metodos;
    }
}

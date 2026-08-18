package com.ohchurus.budget.util;

import com.ohchurus.budget.entity.Movement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ============================================================================
 * LEER UN EXTRACTO Y SABER QUE YA TENIAS
 * ============================================================================
 *
 * Las tres piezas puras del importador. Se prueban juntas porque su valor solo
 * se ve junto: un lector perfecto que despues duplica movimientos no sirve de
 * nada, y un cotejador perfecto sobre columnas mal partidas tampoco.
 *
 * Y esto es lo que decide si la app se usa o se abandona. Nadie deja una app
 * de finanzas porque los informes sean feos; todo el mundo la deja por teclear
 * sesenta movimientos al mes.
 */
@DisplayName("Importacion: leer el CSV y saber que ya tenias")
class ImportarUnExtractoTest {

    // ========================================================================

    @Nested
    @DisplayName("Leer el CSV")
    class LeerElCsv {

        @Test
        @DisplayName("una coma dentro de comillas NO parte el campo")
        void comaDentroDeComillas() {
            /* El fallo clasico del partir por comas: "Pago, cuota 3" se
               convierte en dos campos y desplaza todo lo de la derecha, asi
               que el importe acaba en la columna de la descripcion y la fecha
               en la del importe. El fichero de ejemplo siempre funciona; el
               primero de verdad, no. */
            List<List<String>> filas = LectorCsv.leer("2026-08-01,\"Pago, cuota 3\",150000\n", ',');

            assertThat(filas).hasSize(1);
            assertThat(filas.get(0)).containsExactly("2026-08-01", "Pago, cuota 3", "150000");
        }

        @Test
        @DisplayName("dos comillas seguidas dentro de un campo son una comilla literal")
        void comillaEscapada() {
            List<List<String>> filas = LectorCsv.leer("a,\"dice \"\"hola\"\" fuerte\",b\n", ',');
            assertThat(filas.get(0).get(1)).isEqualTo("dice \"hola\" fuerte");
        }

        @Test
        @DisplayName("un salto de linea dentro de comillas no parte la fila")
        void saltoDeLineaDentroDeComillas() {
            /* Por esto no se puede leer linea a linea: hay que recorrer
               caracter a caracter. Un extracto con una descripcion multilinea
               generaria filas basura a partir de ahi. */
            List<List<String>> filas = LectorCsv.leer("a,\"primera\nsegunda\",b\n", ',');
            assertThat(filas).hasSize(1);
            assertThat(filas.get(0).get(1)).isEqualTo("primera\nsegunda");
        }

        @Test
        @DisplayName("la ultima fila cuenta aunque no acabe en salto de linea")
        void ultimaFilaSinSalto() {
            /* Perderla en silencio significa perder el ultimo movimiento del
               extracto, y nadie lo nota hasta que el saldo no cuadra. */
            assertThat(LectorCsv.leer("a,b\nc,d", ',')).hasSize(2);
        }

        @Test
        @DisplayName("las lineas en blanco del final no son movimientos vacios")
        void lineasEnBlanco() {
            assertThat(LectorCsv.leer("a,b\n\n\n", ',')).hasSize(1);
        }

        @Test
        @DisplayName("detecta el punto y coma, que medio banco colombiano usa")
        void detectaPuntoYComa() {
            assertThat(LectorCsv.separadorDe("fecha;concepto;valor\n2026-08-01;Pago;1000"))
                    .isEqualTo(';');
        }

        @Test
        @DisplayName("y no se deja enganar por comas que van DENTRO del texto")
        void noSeDejaEnganarPorLaComaDelTexto() {
            /* Contar a secas se equivoca: la coma de "Pago, cuota 3" ganaria al
               punto y coma que separa de verdad, y el fichero entero se leeria
               como una sola columna. */
            assertThat(LectorCsv.separadorDe("2026-08-01;\"Pago, cuota 3, con recargo\";150000"))
                    .isEqualTo(';');
        }
    }

    @Nested
    @DisplayName("Cuanto se parecen dos descripciones")
    class CuantoSeParecen {

        @Test
        @DisplayName("mayusculas y tildes no hacen distintas dos cosas iguales")
        void normalizaTildesYMayusculas() {
            /* En Colombia esto no es un detalle: los extractos vienen en
               MAYUSCULAS y SIN TILDES, y lo que uno escribe a mano lleva las
               dos cosas. Sin normalizar, el importador duplica siempre. */
            assertThat(Parecido.cuanto("Cafetería", "CAFETERIA")).isEqualTo(1.0);
            assertThat(Parecido.cuanto("Pago Nequi", "PAGO NEQUI")).isEqualTo(1.0);
        }

        @Test
        @DisplayName("un dedazo sigue pareciendose")
        void toleraUnDedazo() {
            assertThat(Parecido.bastante("Exito", "Extio")).isTrue();
        }

        @Test
        @DisplayName("el banco anadiendo palabras sigue siendo lo mismo")
        void elBancoAnadePalabras() {
            /*
             * EL caso comun del importador, y el que la distancia de edicion
             * sola no resuelve: tu escribes "Arriendo" y el banco escribe
             * "ARRIENDO AGOSTO". Catorce caracteres contra ocho dan 0,5 y los
             * declararia distintos.
             *
             * Lo descubrio la prueba de extremo a extremo: el pendiente del
             * arriendo no casaba con su propia fila del extracto y se habria
             * importado como un gasto NUEVO, dejando el pendiente colgando y
             * el arriendo contado dos veces.
             */
            assertThat(Parecido.cuanto("Arriendo", "ARRIENDO AGOSTO"))
                    .as("la distancia de edicion sola no llega")
                    .isLessThan(0.6);
            assertThat(Parecido.bastante("Arriendo", "ARRIENDO AGOSTO"))
                    .as("pero es obviamente lo mismo, y el cotejo ademas exige importe "
                            + "identico y fecha cercana")
                    .isTrue();
        }

        @Test
        @DisplayName("pero una palabra de tres letras no se come media lista")
        void elMinimoDeCuatroLetras() {
            /* Sin el minimo, cualquier descripcion que contenga "pag" casaria
               con "pago", y con importe repetido eso son duplicados en cadena. */
            assertThat(Parecido.bastante("pag", "pago de arriendo mensual")).isFalse();
        }

        @Test
        @DisplayName("dos cosas distintas no se parecen")
        void distintasNoSeParecen() {
            assertThat(Parecido.bastante("Arriendo", "Netflix")).isFalse();
        }

        @Test
        @DisplayName("dos descripciones vacias se consideran iguales")
        void dosVaciasSonIguales() {
            /* El caso real: un extracto sin columna de descripcion. Si dos
               vacios no se parecieran, el cotejo por importe y fecha no casaria
               nunca nada y todo entraria duplicado. */
            assertThat(Parecido.cuanto(null, "")).isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("Saber que ya tenias")
    class SaberQueYaTenias {

        private Movement movimiento(Long id, String fecha, String importe, String descripcion) {
            return Movement.builder()
                    .id(id).userId(1L).categoryId(1L)
                    .date(LocalDate.parse(fecha)).amount(new BigDecimal(importe))
                    .description(descripcion).confirmed(true).active(true).build();
        }

        private CotejadorDeImportacion.Fila fila(String fecha, String importe, String descripcion) {
            return new CotejadorDeImportacion.Fila(1, LocalDate.parse(fecha),
                    new BigDecimal(importe), descripcion, null);
        }

        @Test
        @DisplayName("el identificador del banco manda sobre todo lo demas")
        void elIdentificadorManda() {
            Movement existente = movimiento(1L, "2026-08-01", "50000", "cualquier cosa");
            existente.setExternalId("REF-999");

            var cotejos = CotejadorDeImportacion.cotejar(
                    List.of(new CotejadorDeImportacion.Fila(1, LocalDate.parse("2026-01-01"),
                            new BigDecimal("999999"), "nada que ver", "REF-999")),
                    List.of(existente));

            assertThat(cotejos.get(0).veredicto())
                    .as("con el identificador del banco no hay que adivinar: es la misma operacion "
                            + "aunque la fecha y el importe se hayan reescrito")
                    .isEqualTo(CotejadorDeImportacion.Veredicto.DUPLICADO);
        }

        @Test
        @DisplayName("mismo importe, fecha cercana y descripcion parecida es un duplicado")
        void duplicadoPorParecido() {
            var cotejos = CotejadorDeImportacion.cotejar(
                    List.of(fila("2026-08-04", "45000", "COMPRA EXITO CALLE 80")),
                    List.of(movimiento(1L, "2026-08-01", "45000", "Compra Exito calle 80")));

            assertThat(cotejos.get(0).veredicto()).isEqualTo(CotejadorDeImportacion.Veredicto.DUPLICADO);
        }

        @Test
        @DisplayName("un importe distinto NO es duplicado por mucho que se parezca el texto")
        void elImporteEsObligatorio() {
            /* El importe es lo unico que ni el banco ni el usuario reescriben.
               Sin exigirlo exacto, dos cafes de dias distintos se confundirian
               entre si y uno de los dos no entraria nunca. */
            var cotejos = CotejadorDeImportacion.cotejar(
                    List.of(fila("2026-08-01", "46000", "Cafeteria")),
                    List.of(movimiento(1L, "2026-08-01", "45000", "Cafeteria")));

            assertThat(cotejos.get(0).veredicto()).isEqualTo(CotejadorDeImportacion.Veredicto.NUEVO);
        }

        @Test
        @DisplayName("fuera de la ventana de cinco dias tampoco es duplicado")
        void fueraDeLaVentana() {
            /* Con una ventana mas ancha, el arriendo de este mes casaria con el
               del mes pasado y uno de los dos desapareceria. */
            var cotejos = CotejadorDeImportacion.cotejar(
                    List.of(fila("2026-08-20", "1500000", "Arriendo")),
                    List.of(movimiento(1L, "2026-07-20", "1500000", "Arriendo")));

            assertThat(cotejos.get(0).veredicto()).isEqualTo(CotejadorDeImportacion.Veredicto.NUEVO);
        }

        @Test
        @DisplayName("si casa con un pendiente de una recurrencia, lo CONFIRMA en vez de duplicarlo")
        void confirmaElPendiente() {
            /* Es la tercera respuesta, y la que hace bonito el importador:
               importarlo como nuevo dejaria el pendiente colgando para siempre
               y el arriendo contado dos veces. */
            Movement pendiente = movimiento(1L, "2026-08-05", "1500000", "Arriendo");
            pendiente.setConfirmed(false);
            pendiente.setScheduledMovementId(7L);

            var cotejos = CotejadorDeImportacion.cotejar(
                    List.of(fila("2026-08-06", "1500000", "PAGO ARRIENDO")),
                    List.of(pendiente));

            assertThat(cotejos.get(0).veredicto())
                    .isEqualTo(CotejadorDeImportacion.Veredicto.CONFIRMA_PENDIENTE);
            assertThat(cotejos.get(0).movimientoId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("dos cobros identicos el mismo dia no casan los dos con el mismo movimiento")
        void dosCobrosIdenticos() {
            /* Dos cafes de 4.500 el mismo dia: uno YA lo tenias y el otro no.
               Sin llevar cuenta de lo ya casado, los dos saldrian duplicados y
               el segundo no entraria nunca. */
            var cotejos = CotejadorDeImportacion.cotejar(
                    List.of(fila("2026-08-01", "4500", "Cafe"), fila("2026-08-01", "4500", "Cafe")),
                    List.of(movimiento(1L, "2026-08-01", "4500", "Cafe")));

            assertThat(cotejos.get(0).veredicto()).isEqualTo(CotejadorDeImportacion.Veredicto.DUPLICADO);
            assertThat(cotejos.get(1).veredicto())
                    .as("el segundo cafe es un gasto de verdad que todavia no estaba anotado")
                    .isEqualTo(CotejadorDeImportacion.Veredicto.NUEVO);
        }

        @Test
        @DisplayName("entre varios candidatos gana el mas cercano en el tiempo")
        void ganaElMasCercano() {
            var cotejos = CotejadorDeImportacion.cotejar(
                    List.of(fila("2026-08-05", "1500000", "Arriendo")),
                    List.of(movimiento(1L, "2026-08-01", "1500000", "Arriendo"),
                            movimiento(2L, "2026-08-04", "1500000", "Arriendo")));

            assertThat(cotejos.get(0).movimientoId()).isEqualTo(2L);
        }

        @Test
        @DisplayName("un movimiento borrado no cuenta como duplicado")
        void elBorradoNoCuenta() {
            Movement borrado = movimiento(1L, "2026-08-01", "45000", "Cafeteria");
            borrado.setActive(false);

            var cotejos = CotejadorDeImportacion.cotejar(
                    List.of(fila("2026-08-01", "45000", "Cafeteria")), List.of(borrado));

            assertThat(cotejos.get(0).veredicto())
                    .as("si contara, borrar un movimiento haria imposible volver a importarlo")
                    .isEqualTo(CotejadorDeImportacion.Veredicto.NUEVO);
        }

        @Test
        @DisplayName("sin nada previo, todo es nuevo y nada explota")
        void primeraImportacion() {
            var cotejos = CotejadorDeImportacion.cotejar(
                    List.of(fila("2026-08-01", "45000", "Cafeteria")), List.of());
            assertThat(cotejos.get(0).veredicto()).isEqualTo(CotejadorDeImportacion.Veredicto.NUEVO);
        }
    }
}

package com.ohchurus.budget.util;

import com.ohchurus.budget.enums.SplitMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ============================================================================
 * EL PESO PERDIDO
 * ============================================================================
 *
 * Casi todas estas pruebas comprueban lo mismo con distintos numeros: que las
 * partes sumen EXACTAMENTE el total. Parece una obsesion desproporcionada
 * hasta que se piensa en que pasa si no.
 *
 * 100 entre 3 son 33,33 tres veces, que suman 99,99. Ese centavo no se pierde:
 * se queda flotando y reaparece semanas despues como un descuadre de origen
 * desconocido en la pantalla de balances, que es de los sintomas que hacen que
 * alguien deje de usar una app de plata. Es la misma familia de fallo que ya
 * dejo el panel diciendo dos cifras distintas para la misma plata.
 *
 * La regla: el sobrante se le da AL PRIMERO, siempre. Que se lo lleve alguien
 * concreto y siempre el mismo importa mas que quien sea: hace el resultado
 * reproducible y la suma exacta.
 */
@DisplayName("Calculadora de reparto: las partes suman el total, siempre")
class CalculadoraDeRepartoTest {

    private static final Long ANA = 1L;
    private static final Long BRUNO = 2L;
    private static final Long CARLA = 3L;

    private static CalculadoraDeReparto.ParteDeclarada parte(Long quien, String valor) {
        return new CalculadoraDeReparto.ParteDeclarada(
                quien, valor == null ? null : new BigDecimal(valor));
    }

    private static BigDecimal suma(List<CalculadoraDeReparto.ParteCalculada> partes) {
        return partes.stream().map(CalculadoraDeReparto.ParteCalculada::importe)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal de(List<CalculadoraDeReparto.ParteCalculada> partes, Long quien) {
        return partes.stream().filter(p -> p.userId().equals(quien))
                .map(CalculadoraDeReparto.ParteCalculada::importe)
                .findFirst().orElse(BigDecimal.ZERO);
    }

    // ========================================================================

    @Nested
    @DisplayName("A partes iguales")
    class PartesIguales {

        @Test
        @DisplayName("120.000 entre tres son 40.000 cada uno")
        void repartoLimpio() {
            var r = CalculadoraDeReparto.repartir(new BigDecimal("120000"), SplitMode.EQUAL,
                    List.of(parte(ANA, null), parte(BRUNO, null), parte(CARLA, null)));

            assertThat(de(r, ANA)).isEqualByComparingTo("40000");
            assertThat(de(r, BRUNO)).isEqualByComparingTo("40000");
            assertThat(de(r, CARLA)).isEqualByComparingTo("40000");
        }

        @Test
        @DisplayName("100 entre tres suman 100, no 99,99")
        void elCentavoQueFalta() {
            var r = CalculadoraDeReparto.repartir(new BigDecimal("100"), SplitMode.EQUAL,
                    List.of(parte(ANA, null), parte(BRUNO, null), parte(CARLA, null)));

            assertThat(suma(r))
                    .as("33,33 tres veces son 99,99: falta un centavo que reaparecera "
                            + "semanas despues como un descuadre sin explicacion")
                    .isEqualByComparingTo("100");
            assertThat(de(r, ANA))
                    .as("el sobrante se le da al primero, siempre, para que sea reproducible")
                    .isEqualByComparingTo("33.34");
        }

        @Test
        @DisplayName("un solo participante se lo lleva todo")
        void unoSolo() {
            var r = CalculadoraDeReparto.repartir(new BigDecimal("77777"), SplitMode.EQUAL,
                    List.of(parte(ANA, null)));
            assertThat(de(r, ANA)).isEqualByComparingTo("77777");
        }
    }

    @Nested
    @DisplayName("Por participaciones")
    class Participaciones {

        @Test
        @DisplayName("uno paga por dos porque vinieron los ninos")
        void pagoPorDos() {
            var r = CalculadoraDeReparto.repartir(new BigDecimal("90000"), SplitMode.SHARES,
                    List.of(parte(ANA, "2"), parte(BRUNO, "1")));

            assertThat(de(r, ANA)).isEqualByComparingTo("60000");
            assertThat(de(r, BRUNO)).isEqualByComparingTo("30000");
            assertThat(suma(r)).isEqualByComparingTo("90000");
        }

        @Test
        @DisplayName("participaciones que no dividen redondo siguen sumando el total")
        void participacionesConResto() {
            var r = CalculadoraDeReparto.repartir(new BigDecimal("100"), SplitMode.SHARES,
                    List.of(parte(ANA, "1"), parte(BRUNO, "1"), parte(CARLA, "1")));
            assertThat(suma(r)).isEqualByComparingTo("100");
        }

        @Test
        @DisplayName("si todas las participaciones son cero, no se divide por cero y la plata no se pierde")
        void participacionesEnCero() {
            /*
             * No deberia llegar aqui —se valida antes—, pero dividir por cero
             * en un calculo de plata no puede depender de que la validacion de
             * arriba siga existiendo manana.
             *
             * Escribi primero que el resultado debia ser 0 y la prueba me
             * corrigio: con todo a cero el reparto sale a cero y despues el
             * ajuste del peso perdido le da el total al primero. Es lo
             * correcto y no un efecto colateral — la plata no puede
             * evaporarse. Un gasto de 100.000 que "no le toca a nadie" seria
             * 100.000 que salieron del banco y no aparecen en ningun
             * presupuesto, que es peor que atribuirselos a alguien.
             */
            var r = CalculadoraDeReparto.repartir(new BigDecimal("100"), SplitMode.SHARES,
                    List.of(parte(ANA, "0"), parte(BRUNO, "0")));
            assertThat(suma(r))
                    .as("la plata no puede evaporarse ni con datos absurdos")
                    .isEqualByComparingTo("100");
            assertThat(de(r, ANA)).isEqualByComparingTo("100");
        }
    }

    @Nested
    @DisplayName("Por porcentaje")
    class Porcentaje {

        @Test
        @DisplayName("70/30, que es como se reparte el arriendo cuando uno gana mas")
        void setentaTreinta() {
            var r = CalculadoraDeReparto.repartir(new BigDecimal("1500000"), SplitMode.PERCENT,
                    List.of(parte(ANA, "70"), parte(BRUNO, "30")));

            assertThat(de(r, ANA)).isEqualByComparingTo("1050000");
            assertThat(de(r, BRUNO)).isEqualByComparingTo("450000");
            assertThat(suma(r)).isEqualByComparingTo("1500000");
        }

        @Test
        @DisplayName("33/33/34 sobre una cifra fea sigue cuadrando al centavo")
        void porcentajesFeos() {
            var r = CalculadoraDeReparto.repartir(new BigDecimal("87431.77"), SplitMode.PERCENT,
                    List.of(parte(ANA, "33"), parte(BRUNO, "33"), parte(CARLA, "34")));
            assertThat(suma(r)).isEqualByComparingTo("87431.77");
        }
    }

    @Nested
    @DisplayName("Por importe")
    class PorImporte {

        @Test
        @DisplayName("los importes escritos se respetan tal cual")
        void importesExactos() {
            var r = CalculadoraDeReparto.repartir(new BigDecimal("120000"), SplitMode.AMOUNT,
                    List.of(parte(ANA, "45000"), parte(BRUNO, "75000")));

            assertThat(de(r, ANA)).isEqualByComparingTo("45000");
            assertThat(de(r, BRUNO)).isEqualByComparingTo("75000");
        }

        @Test
        @DisplayName("si los importes no llegan al total, NO se estiran")
        void loQueFaltaEsDelQuePago() {
            /* Es la unica excepcion a "las partes suman el total", y es
               deliberada: si alguien dice "de 120.000, 40.000 son tuyos" y
               nada mas, lo que quiere decir es que los otros 80.000 son suyos.
               Estirar la cifra que escribio seria inventarse su intencion. */
            var r = CalculadoraDeReparto.repartir(new BigDecimal("120000"), SplitMode.AMOUNT,
                    List.of(parte(BRUNO, "40000")));

            assertThat(de(r, BRUNO))
                    .as("la parte de Bruno no puede crecer sola hasta 120.000")
                    .isEqualByComparingTo("40000");
        }
    }

    @Nested
    @DisplayName("Bordes")
    class Bordes {

        @Test
        @DisplayName("sin participantes devuelve lista vacia en vez de romper")
        void sinParticipantes() {
            assertThat(CalculadoraDeReparto.repartir(new BigDecimal("100"), SplitMode.EQUAL, List.of()))
                    .isEmpty();
        }

        @Test
        @DisplayName("sin total ni modo tampoco explota")
        void sinNada() {
            assertThat(CalculadoraDeReparto.repartir(null, SplitMode.EQUAL, List.of(parte(ANA, null))))
                    .isEmpty();
            assertThat(CalculadoraDeReparto.repartir(new BigDecimal("100"), null, List.of(parte(ANA, null))))
                    .isEmpty();
        }

        @Test
        @DisplayName("un gasto de cero reparte ceros")
        void gastoDeCero() {
            var r = CalculadoraDeReparto.repartir(BigDecimal.ZERO, SplitMode.EQUAL,
                    List.of(parte(ANA, null), parte(BRUNO, null)));
            assertThat(suma(r)).isEqualByComparingTo("0");
        }
    }
}

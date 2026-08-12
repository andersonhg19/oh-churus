package com.ohchurus.budget.util;

import com.ohchurus.budget.enums.SplitMode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Convierte "como se reparte" en "cuanto le toca a cada quien".
 *
 * Es codigo puro: entra un total, un modo y lo que escribio cada persona, y
 * salen importes. No sabe de base de datos, ni de usuarios, ni de permisos.
 * Por eso las decenas de casos de redondeo se pueden probar en un parpadeo.
 *
 * EL PROBLEMA DE VERDAD AQUI ES EL PESO PERDIDO
 * ---------------------------------------------
 * 100 entre 3 son 33,33 tres veces, que suman 99,99. Ese centavo que falta
 * parece una tonteria y no lo es: si las partes no suman EXACTAMENTE el total,
 * la diferencia se queda flotando y aparece mas tarde como un descuadre que
 * nadie sabe de donde salio — la misma clase de fallo que este proyecto ya
 * arrastro en el panel.
 *
 * La regla: se reparte con redondeo normal y **la diferencia se le da al
 * primero**. No al que pago, no prorrateada: al primero, siempre, de forma
 * determinista. Que se lo lleve alguien concreto y siempre el mismo es mas
 * importante que quien sea, porque hace que el resultado sea reproducible y
 * que la suma cuadre al centavo. En Colombia, ademas, el centavo no existe en
 * la practica: los importes van en pesos.
 */
public final class CalculadoraDeReparto {

    private CalculadoraDeReparto() {}

    /** Lo que una persona escribio para su parte. */
    public record ParteDeclarada(Long userId, BigDecimal valor) {}

    /** Lo que le acaba tocando. */
    public record ParteCalculada(Long userId, BigDecimal valor, BigDecimal importe) {}

    /**
     * @param total   el importe completo del gasto
     * @param modo    como se reparte
     * @param partes  quien participa y con que valor (participaciones, % o importe)
     */
    public static List<ParteCalculada> repartir(BigDecimal total, SplitMode modo,
                                                List<ParteDeclarada> partes) {
        if (total == null || modo == null || partes == null || partes.isEmpty()) {
            return List.of();
        }

        BigDecimal restante = total.setScale(2, RoundingMode.HALF_UP);
        Map<Long, BigDecimal> calculado = new LinkedHashMap<>();

        switch (modo) {
            case EQUAL -> repartirEnPartesIguales(restante, partes, calculado);
            case SHARES -> repartirPorPeso(restante, partes, calculado, sumaDeValores(partes));
            case PERCENT -> repartirPorPeso(restante, partes, calculado, new BigDecimal("100"));
            case AMOUNT -> partes.forEach(p ->
                    calculado.put(p.userId(), valor(p).setScale(2, RoundingMode.HALF_UP)));
        }

        /*
         * El ajuste final. En EQUAL/SHARES/PERCENT viene del redondeo; en
         * AMOUNT viene de que la persona escribio importes que no suman el
         * total, y ahi la diferencia NO se toca: si alguien dice "de 120.000,
         * 40.000 son tuyos" y nada mas, lo que quiere decir es que los otros
         * 80.000 son suyos, no que haya que estirar la cifra que escribio.
         */
        if (modo != SplitMode.AMOUNT) {
            ajustarElPesoPerdido(restante, calculado);
        }

        List<ParteCalculada> resultado = new ArrayList<>();
        partes.forEach(p -> resultado.add(new ParteCalculada(
                p.userId(), p.valor(), calculado.getOrDefault(p.userId(), BigDecimal.ZERO))));
        return resultado;
    }

    private static void repartirEnPartesIguales(BigDecimal total, List<ParteDeclarada> partes,
                                                Map<Long, BigDecimal> destino) {
        BigDecimal cuantos = BigDecimal.valueOf(partes.size());
        BigDecimal cada = total.divide(cuantos, 2, RoundingMode.HALF_UP);
        partes.forEach(p -> destino.put(p.userId(), cada));
    }

    private static void repartirPorPeso(BigDecimal total, List<ParteDeclarada> partes,
                                        Map<Long, BigDecimal> destino, BigDecimal pesoTotal) {
        if (pesoTotal.compareTo(BigDecimal.ZERO) == 0) {
            /* Todo el mundo a cero antes que dividir por cero. Que las partes
               sumen algo se valida antes de llegar aqui; esto es la red. */
            partes.forEach(p -> destino.put(p.userId(), BigDecimal.ZERO));
            return;
        }
        partes.forEach(p -> destino.put(p.userId(),
                total.multiply(valor(p)).divide(pesoTotal, 2, RoundingMode.HALF_UP)));
    }

    /** La diferencia por redondeo se le suma al primero. Ver la nota de arriba. */
    private static void ajustarElPesoPerdido(BigDecimal total, Map<Long, BigDecimal> calculado) {
        BigDecimal suma = calculado.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal diferencia = total.subtract(suma);
        if (diferencia.compareTo(BigDecimal.ZERO) == 0 || calculado.isEmpty()) return;

        Long primero = calculado.keySet().iterator().next();
        calculado.put(primero, calculado.get(primero).add(diferencia));
    }

    private static BigDecimal sumaDeValores(List<ParteDeclarada> partes) {
        return partes.stream().map(CalculadoraDeReparto::valor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal valor(ParteDeclarada p) {
        return p.valor() == null ? BigDecimal.ZERO : p.valor();
    }
}

package com.ohchurus.budget.util;

import com.ohchurus.budget.entity.Movement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * ============================================================================
 * ESTA FILA DEL EXTRACTO, ¿YA LA TENGO?
 * ============================================================================
 *
 * Es la pregunta de la que depende que el importador sirva o estorbe. Si se
 * equivoca por un lado, la app se llena de gastos duplicados y las cifras
 * dejan de valer; si se equivoca por el otro, hay movimientos del banco que
 * no entran nunca y el saldo no cuadra.
 *
 * LA REGLA, al estilo de Actual y no al de Firefly III:
 *
 *   1. Si el extracto trae un IDENTIFICADOR del banco y ya existe un
 *      movimiento con ese identificador, es el mismo. Punto. Es la unica
 *      senal que el banco garantiza unica.
 *   2. Si no hay identificador: mismo IMPORTE (obligatorio, exacto), fecha
 *      dentro de una ventana de mas/menos 5 dias, y descripcion parecida.
 *
 * POR QUE EL IMPORTE ES OBLIGATORIO Y EXACTO. Es lo unico que ni el banco ni
 * el usuario escriben distinto. La fecha baila —el banco fecha la compra el
 * dia que la liquida— y la descripcion baila siempre. Sin exigir el importe,
 * dos cafes de dias distintos se confundirian entre si.
 *
 * POR QUE CINCO DIAS. Cubre el fin de semana largo mas la demora tipica de
 * liquidacion de una tarjeta. Con una ventana mas ancha empiezan a casar el
 * arriendo de este mes con el del anterior; con una mas estrecha, se cuelan
 * duplicados de una compra fechada el viernes y liquidada el martes.
 *
 * Y HAY UNA TERCERA RESPUESTA, que es la que hace bonito esto: la fila puede
 * casar con un PENDIENTE que genero una recurrencia. Entonces no es un
 * movimiento nuevo ni un duplicado: es la confirmacion de que el arriendo que
 * la app esperaba efectivamente se pago. Importarlo como nuevo dejaria el
 * pendiente colgando para siempre y el gasto contado dos veces.
 *
 * Codigo puro: entran filas y movimientos, sale una clasificacion.
 */
public final class CotejadorDeImportacion {

    private CotejadorDeImportacion() {}

    /** Cuantos dias de margen se admiten entre la fecha del banco y la tuya. */
    public static final int VENTANA_DIAS = 5;

    public enum Veredicto { NUEVO, DUPLICADO, CONFIRMA_PENDIENTE }

    /** Una fila del extracto, ya interpretada. */
    public record Fila(int numero, LocalDate fecha, BigDecimal importe,
                       String descripcion, String identificadorDelBanco) {}

    /** Que hacer con ella. */
    public record Cotejo(Fila fila, Veredicto veredicto, Long movimientoId, String motivo) {}

    /**
     * @param filas       lo que trae el extracto
     * @param existentes  los movimientos que ya hay (confirmados y pendientes)
     */
    public static List<Cotejo> cotejar(List<Fila> filas, List<Movement> existentes) {
        List<Cotejo> resultado = new ArrayList<>();
        if (filas == null) return resultado;

        /* Lo ya casado en ESTA pasada. Un extracto puede traer dos cobros
           identicos el mismo dia —dos cafes de 4.500— y sin esto los dos
           casarian con el mismo movimiento existente: el primero saldria
           duplicado y el segundo tambien, cuando en realidad uno de los dos es
           nuevo. */
        List<Long> yaCasados = new ArrayList<>();

        for (Fila fila : filas) {
            Movement porIdentificador = buscarPorIdentificador(fila, existentes, yaCasados);
            if (porIdentificador != null) {
                yaCasados.add(porIdentificador.getId());
                resultado.add(new Cotejo(fila, Veredicto.DUPLICADO, porIdentificador.getId(),
                        "El banco le dio el mismo identificador"));
                continue;
            }

            Movement parecido = buscarPorImporteFechaYDescripcion(fila, existentes, yaCasados);
            if (parecido != null) {
                yaCasados.add(parecido.getId());
                boolean esPendienteDeUnProgramado =
                        Boolean.FALSE.equals(parecido.getConfirmed())
                                && parecido.getScheduledMovementId() != null;
                resultado.add(new Cotejo(fila,
                        esPendienteDeUnProgramado ? Veredicto.CONFIRMA_PENDIENTE : Veredicto.DUPLICADO,
                        parecido.getId(),
                        esPendienteDeUnProgramado
                                ? "Casa con un pendiente que estabas esperando"
                                : "Mismo importe y fecha cercana"));
                continue;
            }

            resultado.add(new Cotejo(fila, Veredicto.NUEVO, null, "No estaba"));
        }
        return resultado;
    }

    private static Movement buscarPorIdentificador(Fila fila, List<Movement> existentes,
                                                   List<Long> yaCasados) {
        String id = fila.identificadorDelBanco();
        if (id == null || id.isBlank() || existentes == null) return null;

        return existentes.stream()
                .filter(m -> !yaCasados.contains(m.getId()))
                .filter(m -> id.equals(m.getExternalId()))
                .findFirst()
                .orElse(null);
    }

    private static Movement buscarPorImporteFechaYDescripcion(Fila fila, List<Movement> existentes,
                                                              List<Long> yaCasados) {
        if (existentes == null || fila.importe() == null || fila.fecha() == null) return null;

        Movement mejor = null;
        long mejorDistanciaEnDias = Long.MAX_VALUE;

        for (Movement m : existentes) {
            if (yaCasados.contains(m.getId())) continue;
            if (Boolean.FALSE.equals(m.getActive())) continue;
            if (m.getAmount() == null || m.getDate() == null) continue;

            /* El importe, exacto. Es la unica senal que nadie reescribe. */
            if (m.getAmount().compareTo(fila.importe().abs()) != 0) continue;

            long dias = Math.abs(java.time.temporal.ChronoUnit.DAYS.between(m.getDate(), fila.fecha()));
            if (dias > VENTANA_DIAS) continue;

            if (!Parecido.bastante(m.getDescription(), fila.descripcion())) continue;

            /* Entre varios candidatos gana el mas cercano en el tiempo: con el
               arriendo de dos meses seguidos, casar con el que toca importa. */
            if (dias < mejorDistanciaEnDias) {
                mejorDistanciaEnDias = dias;
                mejor = m;
            }
        }
        return mejor;
    }
}

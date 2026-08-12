package com.ohchurus.budget.service.impl;

import com.ohchurus.budget.entity.Movement;
import com.ohchurus.budget.entity.MovementSplit;
import com.ohchurus.budget.repository.MovementSplitRepository;
import com.ohchurus.budget.util.Computables;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Cuanto de este gasto es TUYO, y quien le debe que a quien.
 *
 * ES LA PIEZA QUE HACE QUE EL PRESUPUESTO DEJE DE MENTIR
 * ------------------------------------------------------
 * Sin esto, poner la cuenta de un restaurante de 120.000 entre tres se come el
 * mes entero de "Restaurantes" con plata que te van a devolver. Con esto, en tu
 * categoria entran 40.000 —lo que de verdad gastaste— y los otros 80.000 se
 * convierten en lo que son: un derecho de cobro.
 *
 * Y EL SALDO DE LA CUENTA NO CAMBIA
 * ---------------------------------
 * Siguen siendo 120.000 los que salieron del banco. Esa es la mitad
 * imprescindible de la regla: si el reparto tocara tambien el saldo, la app
 * dejaria de cuadrar con el extracto y perderia lo unico que la hace
 * comprobable. Presupuesto y saldo responden preguntas distintas y por eso
 * miran cosas distintas.
 */
@Component
public class RepartoDeGastos {

    private final MovementSplitRepository partes;

    public RepartoDeGastos(MovementSplitRepository partes) {
        this.partes = partes;
    }

    // ========================================================================
    // Cuanto me toca

    /**
     * Lo que este movimiento cuenta como gasto (o ingreso) MIO.
     *
     * Sin reparto, el importe entero. Con reparto, solo mi parte — y cero si
     * no participo, porque haber pagado no es haber gastado.
     */
    public BigDecimal miParte(Movement movimiento, Long yo) {
        if (movimiento == null) return BigDecimal.ZERO;
        if (movimiento.getSplitMode() == null) return Computables.importe(movimiento);

        return partes.findByMovementIdAndActiveTrue(movimiento.getId()).stream()
                .filter(p -> p.getUserId().equals(yo))
                .map(MovementSplit::getComputedAmount)
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }

    /**
     * Lo mismo para un lote, en UNA consulta.
     *
     * El panel pinta decenas de movimientos; preguntar uno por uno seria una
     * consulta por fila en cada refresco. Devuelve un mapa de id de movimiento
     * a la parte que me toca.
     */
    public Map<Long, BigDecimal> misPartes(Collection<Movement> movimientos, Long yo) {
        Map<Long, BigDecimal> resultado = new HashMap<>();
        if (movimientos == null || movimientos.isEmpty()) return resultado;

        List<Long> conReparto = movimientos.stream()
                .filter(m -> m.getSplitMode() != null)
                .map(Movement::getId)
                .collect(Collectors.toList());

        Map<Long, BigDecimal> mias = conReparto.isEmpty()
                ? Map.of()
                : partes.findDeVarios(conReparto).stream()
                        .filter(p -> p.getUserId().equals(yo))
                        .collect(Collectors.toMap(MovementSplit::getMovementId,
                                MovementSplit::getComputedAmount, (a, b) -> a));

        for (Movement m : movimientos) {
            resultado.put(m.getId(), m.getSplitMode() == null
                    ? Computables.importe(m)
                    : mias.getOrDefault(m.getId(), BigDecimal.ZERO));
        }
        return resultado;
    }

    // ========================================================================
    // Quien le debe que a quien

    /**
     * El balance NETO con cada persona, al estilo Cospend.
     *
     * Positivo = esa persona te debe. Negativo = le debes tu.
     *
     * POR QUE NETO Y NO UNA LISTA DE DEUDAS
     * -------------------------------------
     * Porque "A le debe 40.000 a B por el mercado, B le debe 25.000 a A por la
     * gasolina y A le debe 12.000 a B por el taxi" no lo resuelve nadie de
     * cabeza, y ademas invita a hacer tres pagos donde sobra uno. La cifra
     * util es una sola por persona: "te debo 27.000". Todo lo demas es el
     * detalle, que sigue estando en los movimientos.
     *
     * COMO SE CALCULA
     * ---------------
     * Por cada gasto repartido que YO pague, lo que pusieron los demas es
     * plata que me deben. Por cada gasto repartido que pago OTRO, mi parte es
     * plata que le debo. Las liquidaciones ya pagadas se restan de ese neto.
     */
    public Map<Long, BigDecimal> balances(Long yo, Collection<Movement> movimientos) {
        Map<Long, BigDecimal> neto = new LinkedHashMap<>();
        if (movimientos == null || movimientos.isEmpty()) return neto;

        List<Movement> repartidos = movimientos.stream()
                .filter(m -> !Boolean.FALSE.equals(m.getActive()))
                .filter(m -> m.getSplitMode() != null)
                .collect(Collectors.toList());

        if (!repartidos.isEmpty()) {
            Map<Long, List<MovementSplit>> porMovimiento = partes
                    .findDeVarios(repartidos.stream().map(Movement::getId).collect(Collectors.toList()))
                    .stream()
                    .collect(Collectors.groupingBy(MovementSplit::getMovementId));

            for (Movement m : repartidos) {
                Long quienPago = m.getUserId();
                for (MovementSplit parte : porMovimiento.getOrDefault(m.getId(), List.of())) {
                    Long deQuienEsLaParte = parte.getUserId();
                    /* La parte del que pago no genera deuda consigo mismo, y
                       una deuda entre dos terceros no es asunto mio: solo
                       cuentan las lineas donde aparezco yo. */
                    if (quienPago.equals(deQuienEsLaParte)) continue;

                    if (quienPago.equals(yo)) {
                        sumar(neto, deQuienEsLaParte, parte.getComputedAmount());
                    } else if (deQuienEsLaParte.equals(yo)) {
                        sumar(neto, quienPago, parte.getComputedAmount().negate());
                    }
                }
            }
        }

        /*
         * Las liquidaciones. Si ya le pague 80.000, esa deuda esta saldada y
         * tiene que desaparecer del neto; si no se restaran, la app me seguiria
         * pidiendo que pague algo que ya pague — que es exactamente el motivo
         * por el que la gente deja de fiarse de estas cuentas.
         */
        movimientos.stream()
                .filter(m -> !Boolean.FALSE.equals(m.getActive()))
                .filter(m -> Boolean.TRUE.equals(m.getIsSettlement()))
                .filter(m -> m.getSettledWithUserId() != null)
                .forEach(m -> {
                    BigDecimal importe = Computables.importe(m);
                    if (yo.equals(m.getUserId())) {
                        /* Yo pague: mi deuda con esa persona baja, o su deuda
                           conmigo sube si ya estabamos a cero. */
                        sumar(neto, m.getSettledWithUserId(), importe);
                    } else if (yo.equals(m.getSettledWithUserId())) {
                        sumar(neto, m.getUserId(), importe.negate());
                    }
                });

        /* Los que quedan exactamente en cero se van: una lista con "Bruno: 0"
           es ruido, y una lista de gente con la que estas en paz entrena a no
           mirarla. */
        neto.entrySet().removeIf(e -> e.getValue().compareTo(BigDecimal.ZERO) == 0);
        return neto;
    }

    private void sumar(Map<Long, BigDecimal> neto, Long persona, BigDecimal cuanto) {
        neto.merge(persona, cuanto, BigDecimal::add);
    }
}

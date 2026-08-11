package com.ohchurus.budget.util;

import com.ohchurus.budget.entity.Movement;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.function.Predicate;

/**
 * ¿Este movimiento SUMA?
 *
 * Una sola respuesta para toda la aplicacion. Antes cada metodo decidia por su
 * cuenta y no coincidian ni dentro del mismo metodo: en DashboardServiceImpl,
 * `totalExpense` contaba los sub-movimientos y `budgetTotal` los excluia. Con
 * un gasto padre de 500.000 y dos hijos de 200.000, "Gastos" decia 900.000 y
 * "Presupuesto" 500.000 en la MISMA pantalla. De ahi salia la sensacion de que
 * las cifras no cuadran.
 *
 * LA REGLA, en dos frases:
 *
 *   · El hijo es DETALLE del padre, nunca un gasto aparte. Si desglosas la
 *     compra del mercado en tres lineas, gastaste el total de la compra, no
 *     el total mas las tres lineas.
 *   · La transferencia mueve plata entre bolsillos: no es ingreso ni gasto
 *     para nadie. Si contara, "disponibilizar" del bote comun al bolsillo
 *     propio inventaria un gasto y un ingreso que no existieron.
 *
 * Si algun dia hay que cambiar la regla, se cambia AQUI y cambia en las cinco
 * pantallas a la vez. Ese es el objetivo.
 */
public final class Computables {

    private Computables() {}

    public static final Predicate<Movement> SUMA = Computables::suma;

    public static boolean suma(Movement m) {
        if (m == null) return false;
        if (Boolean.FALSE.equals(m.getActive())) return false;
        if (Boolean.TRUE.equals(m.getIsTransfer())) return false;
        return m.getParentMovementId() == null;
    }

    /** Importe seguro: un null nunca debe romper una suma de dinero. */
    public static BigDecimal importe(Movement m) {
        return (m == null || m.getAmount() == null) ? BigDecimal.ZERO : m.getAmount();
    }

    /** Suma los que computan de una coleccion. */
    public static BigDecimal total(Collection<Movement> movimientos) {
        if (movimientos == null) return BigDecimal.ZERO;
        return movimientos.stream()
                .filter(Computables::suma)
                .map(Computables::importe)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}

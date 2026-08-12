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
        /* La apertura dice cuanta plata HABIA, no cuanta entro. Si contara,
           el dia que registras la cuenta con 2.000.000 la app te felicitaria
           por un ingreso de dos millones que no ocurrio. */
        if (Boolean.TRUE.equals(m.getIsOpening())) return false;
        return m.getParentMovementId() == null;
    }

    /**
     * ¿Este movimiento MUEVE EL SALDO de su cuenta?
     *
     * Es una pregunta DISTINTA de suma(), y confundirlas es exactamente el
     * tipo de error que dejo el panel descuadrado. Las dos difieren en los dos
     * casos que mas importan:
     *
     *   · La TRANSFERENCIA no suma (no es ingreso ni gasto de nadie) pero SI
     *     mueve saldo: sacar 400.000 del bote comun y meterlos en el bolsillo
     *     deja el bote con 400.000 menos. Un saldo que ignorase las
     *     transferencias no cuadraria jamas con el banco.
     *   · La APERTURA no suma (no es un ingreso) pero SI mueve saldo: es
     *     justamente de donde sale el saldo inicial.
     *
     * Coinciden en lo demas: el hijo es detalle del padre y contarlo ademas
     * del padre gastaria la plata dos veces, y lo desactivado no existe.
     *
     * Se exige cuenta: un movimiento sin accountId no puede mover el saldo de
     * ninguna cuenta. Hoy no deberia haber ninguno —la V4 los repartio todos y
     * el servicio pone la cuenta por defecto—, pero la suma no es el sitio
     * donde enterarse de que si lo hay.
     */
    public static boolean afectaSaldo(Movement m) {
        if (m == null) return false;
        if (Boolean.FALSE.equals(m.getActive())) return false;
        if (m.getAccountId() == null) return false;
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

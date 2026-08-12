package com.ohchurus.budget.enums;

/**
 * Como se reparte un gasto entre varias personas.
 *
 * Los cuatro modos existen porque los cuatro se usan de verdad, y cada uno
 * responde a una frase que la gente dice:
 *
 *   EQUAL   — "a medias". El caso del 80 % de las veces.
 *   SHARES  — "yo pago por dos porque vinieron los ninos". Participaciones.
 *   PERCENT — "yo el 70 %, tu el 30 %", que es como se reparte el arriendo
 *             cuando uno gana mas.
 *   AMOUNT  — "de estos 120.000, 45.000 son tuyos". Importes exactos, para
 *             cuando el reparto no sigue ninguna regla.
 *
 * Por dentro los cuatro acaban en lo mismo: una lista de personas con un
 * importe cada una que suma el total. El modo solo dice COMO se calculo, y se
 * guarda para poder recalcular si cambia el importe del gasto.
 */
public enum SplitMode {
    EQUAL,
    SHARES,
    PERCENT,
    AMOUNT
}

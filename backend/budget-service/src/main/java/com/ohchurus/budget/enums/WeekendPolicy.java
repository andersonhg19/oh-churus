package com.ohchurus.budget.enums;

/**
 * Que hacer cuando una ocurrencia cae sabado o domingo.
 *
 * El valor por defecto es KEEP, y no es pereza:
 *
 *  · Mover la fecha es INVENTAR un dato que el usuario no pidio. El pendiente
 *    es una propuesta que la persona confirma; adelantarlo o atrasarlo por su
 *    cuenta le cambia el mes al que pertenece el gasto y, con el, la cifra de
 *    dos periodos.
 *  · Cambiar el valor por defecto a cualquier otra cosa moveria de golpe la
 *    fecha de TODOS los programados que ya existen, que se crearon cuando esto
 *    no se podia elegir.
 *
 * Quien cobra la nomina "el tercer viernes" no necesita politica ninguna: un
 * viernes nunca es fin de semana. Quien paga el arriendo el 1 y quiere que el
 * banco lo tome el viernes anterior elige PREVIOUS_BUSINESS_DAY a mano.
 *
 * La politica mueve la FECHA del movimiento, nunca su clave de idempotencia:
 * la clave sigue siendo la de la ocurrencia canonica, asi que cambiar de
 * politica no duplica nada ni resucita lo borrado.
 */
public enum WeekendPolicy {

    /** Se queda donde cae, aunque sea sabado o domingo. */
    KEEP,

    /** Se adelanta al viernes anterior (asi se paga la nomina en Colombia). */
    PREVIOUS_BUSINESS_DAY,

    /** Se atrasa al lunes siguiente (asi procesa el banco un debito automatico). */
    NEXT_BUSINESS_DAY
}

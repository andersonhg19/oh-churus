package com.ohchurus.budget.enums;

/**
 * Que clase de salida (o entrada) de plata es un programado. El porque
 * completo esta en la migracion V10; aqui va lo que cambia en el codigo.
 *
 * Solo PROVISION se comporta distinto al generar pendientes: no genera
 * ninguno, porque separar plata para el mercado no es comprar el mercado. Las
 * otras tres generan su pendiente como siempre y se distinguen solo en el plan
 * de la quincena.
 *
 * Nulo se lee como PAYMENT: es lo que eran todos los programados de antes.
 */
public enum ScheduledKind {

    /** Un pago con fecha: arriendo, seguridad social, una cuota. */
    PAYMENT,

    /** Un descuento de nomina: resta del ingreso, no se paga aparte. */
    DEDUCTION,

    /** Ahorro: sale de la cuenta pero no es consumo. */
    SAVING,

    /** Plata que se separa para gastarla en el periodo; no genera pendiente. */
    PROVISION;

    public static ScheduledKind de(ScheduledKind k) {
        return k != null ? k : PAYMENT;
    }
}

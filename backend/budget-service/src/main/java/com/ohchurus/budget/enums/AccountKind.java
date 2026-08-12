package com.ohchurus.budget.enums;

/**
 * Las dos clases de cuenta que existen, y la unica diferencia entre ellas es
 * QUE SIGNIFICA un saldo positivo:
 *
 *   · OWN       — efectivo, cuenta de ahorros, nomina. Saldo positivo = plata
 *                 que tienes.
 *   · LIABILITY — tarjeta de credito, prestamo. Saldo positivo = plata que
 *                 DEBES. Es la misma aritmetica con el signo leido al reves,
 *                 y por eso no hace falta un modelo aparte: lo unico que
 *                 cambia es como se presenta.
 *
 * No hay mas clases a proposito. Firefly III distingue seis tipos de cuenta y
 * la mitad existen solo para sostener su partida doble literal; aqui los
 * ingresos y los gastos ya son categorias.
 */
public enum AccountKind {
    OWN,
    LIABILITY
}

package com.ohchurus.budget.dto.input;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * "Pague la tarjeta": cuanto (mayor que cero y hasta lo que se debe a esa
 * fecha; sin monto, todo lo que se debe) y con que fecha (hoy si no se dice).
 * No es un gasto nuevo: las compras ya contaron el mes en que se hicieron.
 */
@Getter
@Setter
@NoArgsConstructor
public class CardPayDTO {
    /** Fecha del pago. */
    private LocalDate upTo;
    private BigDecimal amount;
}

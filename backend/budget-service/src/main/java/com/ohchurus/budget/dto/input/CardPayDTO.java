package com.ohchurus.budget.dto.input;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * "Pague la tarjeta": quedan pagadas las compras y cuotas hechas con ella
 * hasta esta fecha (hoy si no se dice). No es un gasto nuevo: las compras ya
 * contaron como gasto el mes en que se hicieron.
 */
@Getter
@Setter
@NoArgsConstructor
public class CardPayDTO {
    private LocalDate upTo;
}

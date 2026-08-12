package com.ohchurus.budget.dto.output;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Una ocurrencia que TOCABA pero que no se ha creado.
 *
 * Cuando un programado acumula mas ocurrencias atrasadas de la cuenta, no se
 * materializan en silencio: se devuelven asi, para que la persona las mire y
 * decida. Un diario olvidado tres meses son noventa movimientos; crearlos solos
 * es inventarle a alguien un gasto que quiza nunca hizo.
 *
 * Lo que hace falta para materializarla despues es la pareja
 * (scheduledMovementId, periodStart): el resto son datos para pintarla.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProposedOccurrenceDTO {

    private Long scheduledMovementId;
    private String name;
    private Long categoryId;
    private String categoryName;
    private String categoryType;
    private BigDecimal amount;

    /** La fecha que tendria el movimiento, ya con la politica de fin de semana. */
    private LocalDate date;

    /** La clave de la ocurrencia. Es lo que hay que devolver para crearla. */
    private LocalDate periodStart;

    /** Si su fecha ya paso. Las de mas adelante en el periodo tambien se listan. */
    private Boolean overdue;
}

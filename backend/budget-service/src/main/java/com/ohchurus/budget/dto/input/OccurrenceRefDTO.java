package com.ohchurus.budget.dto.input;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Senala UNA ocurrencia concreta de un programado.
 *
 * No lleva userId ni puede llevarlo: quien pide es el token. Y no lleva importe
 * ni fecha del movimiento, aunque seria comodo: los pone el servidor
 * recalculando el calendario del programado. Si viajaran en el cuerpo,
 * "materializar una propuesta" seria una via para crear el movimiento que se
 * quisiera, con la fecha que se quisiera.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OccurrenceRefDTO {

    @NotNull(message = "es obligatorio")
    private Long scheduledMovementId;

    @NotNull(message = "es obligatorio")
    private LocalDate periodStart;
}

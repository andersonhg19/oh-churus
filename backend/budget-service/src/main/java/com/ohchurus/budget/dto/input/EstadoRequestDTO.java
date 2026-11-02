package com.ohchurus.budget.dto.input;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Pregunta por el estado de un periodo. No lleva la identidad de quien
 * pregunta (sale del token): personUserId es DE QUIEN se quiere ver el
 * estado, y el servicio exige que sea alguien del hogar.
 */
@Getter
@Setter
@NoArgsConstructor
public class EstadoRequestDTO {
    private Integer budgetStartDay;
    private LocalDate referenceDate;
    /** HOGAR (por defecto) o PERSONA. */
    private String scope;
    private Long personUserId;
    /** Solo para el historial: cuantos periodos hacia atras (1 a 12). */
    private Integer periods;
}

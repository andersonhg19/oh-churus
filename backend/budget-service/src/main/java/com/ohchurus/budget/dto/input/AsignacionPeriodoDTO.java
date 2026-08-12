package com.ohchurus.budget.dto.input;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Cuerpo de /v1/budget-allocation/list y /summary: el periodo que se consulta.
 *
 * Sustituye al Map, que aceptaba "budgetStartDay":"lunes" y reventaba con un
 * NumberFormatException fuera del contrato. Los nombres JSON no cambian.
 */
@Getter
@Setter
@NoArgsConstructor
public class AsignacionPeriodoDTO {

    /* El frontend lo sigue enviando, pero la identidad sale del token.
       Se acepta y se ignora. */
    private Long userId;

    @Min(value = 1, message = "debe estar entre 1 y 31")
    @Max(value = 31, message = "debe estar entre 1 y 31")
    private Integer budgetStartDay = 1;

    private LocalDate referenceDate;

    /* Un budgetStartDay:null explicito llegaba como null y rompia el calculo
       del periodo; el Map lo trataba como ausente y ponia 1. Se conserva. */
    public Integer getBudgetStartDay() {
        return budgetStartDay != null ? budgetStartDay : 1;
    }
}

package com.ohchurus.fasting.dto.input;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Cuerpo de /v1/fasting/history/by-period y /history/summary.
 *
 * El historial de ayuno se corta por el mismo periodo que el presupuesto, de
 * ahi el budgetStartDay. Los nombres JSON no cambian.
 */
@Getter
@Setter
@NoArgsConstructor
public class AyunoPeriodoDTO {

    @NotNull(message = "es obligatorio")
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

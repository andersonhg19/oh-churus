package com.ohchurus.budget.dto.input;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/** Cuerpo de /v1/plan/*. Todo opcional: sin nada, es "hoy" con corte el 1. */
@Getter
@Setter
@NoArgsConstructor
public class PlanRequestDTO {
    @Min(value = 1, message = "debe estar entre 1 y 31")
    @Max(value = 31, message = "debe estar entre 1 y 31")
    private Integer budgetStartDay;
    private LocalDate referenceDate;
    private LocalDate from;
    private LocalDate to;
    @Min(value = 1, message = "debe estar entre 1 y 12")
    @Max(value = 12, message = "debe estar entre 1 y 12")
    private Integer periods;
}

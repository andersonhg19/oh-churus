package com.ohchurus.budget.dto.input;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Cuerpo de /v1/budget-allocation/save.
 *
 * Antes era un Map y el controller hacia new BigDecimal(body.get("amount")
 * .toString()): sin amount reventaba con un NullPointerException, y con
 * "amount":"abc" con un NumberFormatException. Las dos veces salia un 500 con
 * el error de Spring, que el frontend no sabe leer. Los nombres JSON no
 * cambian.
 */
@Getter
@Setter
@NoArgsConstructor
public class AsignacionGuardarDTO {

    /* El frontend lo sigue enviando, pero la identidad sale del token.
       Se acepta y se ignora. */
    private Long userId;

    @NotNull(message = "es obligatorio")
    private Long categoryId;

    @NotNull(message = "es obligatorio")
    private BigDecimal amount;

    @Size(max = 255, message = "no puede superar los 255 caracteres")
    private String notes;

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

package com.ohchurus.budget.dto.input;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Mover plata de un sobre a otro dentro del mismo periodo.
 *
 * Es la operacion que hace util la regla asimetrica de los sobres: cuando te
 * pasaste en Restaurantes, la respuesta no es sentirse mal, es decidir de que
 * otro sobre sale esa plata.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MoverEntreSobresDTO {

    @NotNull(message = "es obligatorio")
    private Long fromCategoryId;

    @NotNull(message = "es obligatorio")
    private Long toCategoryId;

    @NotNull(message = "es obligatorio")
    private BigDecimal amount;

    private Integer budgetStartDay;

    /** Cualquier dia del periodo. Si no viene, hoy. */
    private LocalDate referenceDate;
}

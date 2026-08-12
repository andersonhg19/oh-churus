package com.ohchurus.budget.dto.input;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Cuerpo de /v1/movements/transfer (disponibilizar).
 *
 * Antes era un Map: sin fromCategoryId reventaba con un NullPointerException y
 * el usuario veia "Request failed with status code 500". Que el importe sea
 * mayor que 0 lo sigue decidiendo el servicio, que ya lo comprobaba; aqui solo
 * se exige que los campos vengan y sean del tipo que dicen ser. Los nombres
 * JSON no cambian.
 */
@Getter
@Setter
@NoArgsConstructor
public class TransferenciaDTO {

    /* El frontend lo sigue enviando, pero la identidad sale del token.
       Se acepta y se ignora. */
    private Long userId;

    @NotNull(message = "es obligatorio")
    private Long fromCategoryId;

    @NotNull(message = "es obligatorio")
    private Long toCategoryId;

    @NotNull(message = "es obligatorio")
    private BigDecimal amount;

    @Size(max = 255, message = "no puede superar los 255 caracteres")
    private String description;
}

package com.ohchurus.budget.dto.input;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Crear o renombrar una tarjeta de credito. El dueno lo pone el token. */
@Getter
@Setter
@NoArgsConstructor
public class CardSaveDTO {

    /** Nulo = nueva. */
    private Long id;

    @NotBlank(message = "es obligatorio")
    @Size(max = 60, message = "maximo 60 caracteres")
    private String name;

    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "debe ser un color #RRGGBB")
    private String color;

    @Min(value = 1, message = "debe estar entre 1 y 31")
    @Max(value = 31, message = "debe estar entre 1 y 31")
    private Integer paymentDay;
}

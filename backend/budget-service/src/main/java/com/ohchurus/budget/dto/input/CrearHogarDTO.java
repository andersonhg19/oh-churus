package com.ohchurus.budget.dto.input;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Cuerpo de /v1/household/create.
 *
 * Antes era un Map: un nombre ausente llegaba como null hasta el INSERT y
 * salia un 500 sin explicacion, y uno de 300 caracteres reventaba contra la
 * columna (length 100). Los nombres JSON no cambian, el frontend sigue igual.
 */
@Getter
@Setter
@NoArgsConstructor
public class CrearHogarDTO {

    @NotBlank(message = "es obligatorio")
    @Size(max = 100, message = "no puede superar los 100 caracteres")
    private String name;

    /* El frontend lo sigue enviando, pero el dueno del hogar es quien crea:
       sale del token, no del cuerpo. Se acepta y se ignora. */
    private Long userId;
}

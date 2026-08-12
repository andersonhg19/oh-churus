package com.ohchurus.fasting.dto.input;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Cuerpo de los endpoints de ayuno que solo necesitan saber de quien se habla:
 * plan/get, session/cancel, session/active, achievements y water/today.
 *
 * Antes era un Map y el controller hacia Long.valueOf(body.get("userId")
 * .toString()): un cuerpo vacio reventaba con un NullPointerException y el
 * usuario veia "Request failed with status code 500". El nombre JSON no cambia.
 */
@Getter
@Setter
@NoArgsConstructor
public class AyunoUsuarioDTO {

    @NotNull(message = "es obligatorio")
    private Long userId;
}

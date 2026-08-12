package com.ohchurus.fasting.dto.input;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Cuerpo de /v1/fasting/water/set-goal. Que la meta este entre 1 y 20 lo sigue
 * decidiendo el servicio, que ya lo comprobaba. El nombre JSON no cambia.
 */
@Getter
@Setter
@NoArgsConstructor
public class AguaMetaDTO {

    @NotNull(message = "es obligatorio")
    private Long userId;

    @NotNull(message = "es obligatorio")
    private Integer goalGlasses;
}

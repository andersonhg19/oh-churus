package com.ohchurus.fasting.dto.input;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Cuerpo de /v1/fasting/session/start. startTime es opcional: si no viene, el
 * ayuno empieza ahora. Los nombres JSON no cambian.
 */
@Getter
@Setter
@NoArgsConstructor
public class AyunoInicioDTO {

    @NotNull(message = "es obligatorio")
    private Long userId;

    private LocalDateTime startTime;
}

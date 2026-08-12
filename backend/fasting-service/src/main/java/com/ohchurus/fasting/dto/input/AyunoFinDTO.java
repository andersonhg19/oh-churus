package com.ohchurus.fasting.dto.input;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Cuerpo de /v1/fasting/session/stop. endTime es opcional: si no viene, el
 * ayuno termina ahora. Los nombres JSON no cambian.
 */
@Getter
@Setter
@NoArgsConstructor
public class AyunoFinDTO {

    @NotNull(message = "es obligatorio")
    private Long userId;

    private LocalDateTime endTime;
}

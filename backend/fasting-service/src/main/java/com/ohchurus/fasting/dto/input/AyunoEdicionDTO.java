package com.ohchurus.fasting.dto.input;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Cuerpo de /v1/fasting/session/edit: corregir a mano las horas de una sesion.
 * Los nombres JSON no cambian.
 */
@Getter
@Setter
@NoArgsConstructor
public class AyunoEdicionDTO {

    @NotNull(message = "es obligatorio")
    private Long sessionId;

    private LocalDateTime startTime;

    private LocalDateTime endTime;
}

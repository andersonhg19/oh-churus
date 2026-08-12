package com.ohchurus.fasting.dto.input;

import com.ohchurus.fasting.enums.PlanType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Cuerpo de /v1/fasting/plan/save.
 *
 * Antes era un Map con PlanType.valueOf(...): un planType desconocido lanzaba
 * IllegalArgumentException y salia un 500 sin decir cual era el campo malo.
 * Que las horas sumen 24 lo sigue decidiendo el servicio. Los nombres JSON no
 * cambian.
 */
@Getter
@Setter
@NoArgsConstructor
public class AyunoPlanDTO {

    @NotNull(message = "es obligatorio")
    private Long userId;

    @NotNull(message = "es obligatorio")
    private PlanType planType;

    private Integer fastingHours;

    private Integer eatingHours;

    /* La columna admite 5 caracteres ("20:00"): mas largo reventaba al grabar. */
    @Size(max = 5, message = "no puede superar los 5 caracteres (formato HH:mm)")
    private String suggestedStartTime;

    private Boolean remindersEnabled;
}

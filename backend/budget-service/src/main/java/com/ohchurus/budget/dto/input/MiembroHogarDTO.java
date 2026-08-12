package com.ohchurus.budget.dto.input;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Cuerpo de /v1/household/add-member y /remove-member.
 *
 * OJO: aqui el userId NO es quien llama, es el MIEMBRO al que se invita o se
 * saca. Por eso este si viene del cuerpo y debe seguir viniendo. Quien llama
 * sale del token, y el servicio comprueba que sea el OWNER del hogar.
 *
 * Antes era un Map y se leia con Long.valueOf(body.get(...).toString()): sin
 * householdId reventaba con un NullPointerException y salia un 500 vacio.
 */
@Getter
@Setter
@NoArgsConstructor
public class MiembroHogarDTO {

    @NotNull(message = "es obligatorio")
    private Long householdId;

    @NotNull(message = "es obligatorio")
    private Long userId;
}

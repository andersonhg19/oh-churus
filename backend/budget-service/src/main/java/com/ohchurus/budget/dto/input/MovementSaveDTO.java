package com.ohchurus.budget.dto.input;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MovementSaveDTO {

    private Long id;

    /* Sigue existiendo por compatibilidad con el frontend, que aun lo envia,
       pero YA NO SE EXIGE ni se usa: la identidad la pone el token. Dejar el
       @NotNull rechazaba peticiones legitimas que no lo mandan; quitar el
       campo entero romperia nada (Spring ignora lo desconocido) pero deja sin
       sitio a los DTOs de guardado, que si necesitan escribirlo al crear. */
    private Long userId;

    @NotNull(message = "es obligatorio")
    private Long categoryId;

    @NotNull(message = "es obligatorio")
    private LocalDate date;

    @NotNull(message = "es obligatorio")
    @DecimalMin(value = "0.01", message = "debe ser mayor que 0")
    private BigDecimal amount;

    @Size(max = 255, message = "no puede superar los 255 caracteres")
    private String description;

    private Long scheduledMovementId;

    private Long parentMovementId;

    private Boolean confirmed = true;
}

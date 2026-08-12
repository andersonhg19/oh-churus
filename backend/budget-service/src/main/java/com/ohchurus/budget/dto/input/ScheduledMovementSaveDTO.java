package com.ohchurus.budget.dto.input;

import com.ohchurus.budget.enums.Frequency;
import jakarta.validation.constraints.*;
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
public class ScheduledMovementSaveDTO {

    private Long id;

    /* Sigue existiendo por compatibilidad con el frontend, que aun lo envia,
       pero YA NO SE EXIGE ni se usa: la identidad la pone el token. Dejar el
       @NotNull rechazaba peticiones legitimas que no lo mandan; quitar el
       campo entero romperia nada (Spring ignora lo desconocido) pero deja sin
       sitio a los DTOs de guardado, que si necesitan escribirlo al crear. */
    private Long userId;

    @NotNull(message = "es obligatorio")
    private Long categoryId;

    @NotBlank(message = "es obligatorio")
    @Size(max = 100, message = "no puede superar los 100 caracteres")
    private String name;

    @DecimalMin(value = "0.01", message = "debe ser mayor que 0")
    private BigDecimal amount;

    @NotNull(message = "es obligatorio")
    private Frequency frequency;

    private Integer durationMonths;

    @NotNull(message = "es obligatorio")
    private LocalDate startDate;

    @Min(value = 1, message = "debe estar entre 1 y 31")
    @Max(value = 31, message = "debe estar entre 1 y 31")
    private Integer dayOfMonth;
}

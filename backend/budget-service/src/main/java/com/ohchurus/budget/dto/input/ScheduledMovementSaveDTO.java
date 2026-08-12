package com.ohchurus.budget.dto.input;

import com.ohchurus.budget.enums.Frequency;
import com.ohchurus.budget.enums.WeekendPolicy;
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

    /* "El tercer viernes": weekOfMonth = 3, dayOfWeek = 5. El 5 en la semana
       del mes significa "el ultimo". Van los dos o ninguno. */
    @Min(value = 1, message = "debe estar entre 1 y 5 (5 es la ultima)")
    @Max(value = 5, message = "debe estar entre 1 y 5 (5 es la ultima)")
    private Integer weekOfMonth;

    @Min(value = 1, message = "debe estar entre 1 (lunes) y 7 (domingo)")
    @Max(value = 7, message = "debe estar entre 1 (lunes) y 7 (domingo)")
    private Integer dayOfWeek;

    /* Nulo se guarda como KEEP: no mover una fecha es lo unico que no inventa
       nada. El porque completo esta en el enum. */
    private WeekendPolicy weekendPolicy;
}

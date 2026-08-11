package com.ohchurus.budget.dto.input;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.time.LocalDate;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class PeriodRequestDTO {
    /* Sigue existiendo por compatibilidad con el frontend, que aun lo envia,
       pero YA NO SE EXIGE ni se usa: la identidad la pone el token. Dejar el
       @NotNull rechazaba peticiones legitimas que no lo mandan; quitar el
       campo entero romperia nada (Spring ignora lo desconocido) pero deja sin
       sitio a los DTOs de guardado, que si necesitan escribirlo al crear. */
    private Long userId;
    @NotNull(message = "startDate is required")
    private LocalDate startDate;
    @NotNull(message = "endDate is required")
    private LocalDate endDate;
}

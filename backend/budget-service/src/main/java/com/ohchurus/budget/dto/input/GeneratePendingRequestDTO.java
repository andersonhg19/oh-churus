package com.ohchurus.budget.dto.input;

import jakarta.validation.constraints.*;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class GeneratePendingRequestDTO {
    /* Sigue existiendo por compatibilidad con el frontend, que aun lo envia,
       pero YA NO SE EXIGE ni se usa: la identidad la pone el token. Dejar el
       @NotNull rechazaba peticiones legitimas que no lo mandan; quitar el
       campo entero romperia nada (Spring ignora lo desconocido) pero deja sin
       sitio a los DTOs de guardado, que si necesitan escribirlo al crear. */
    private Long userId;
    @NotNull(message = "budgetStartDay is required")
    @Min(value = 1, message = "budgetStartDay must be between 1 and 31")
    @Max(value = 31, message = "budgetStartDay must be between 1 and 31")
    private Integer budgetStartDay;
}

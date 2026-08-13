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

    /**
     * En que cuenta ocurrio. Opcional a proposito: si no viene, el servicio la
     * pone en la cuenta por defecto. Exigirlo dejaria de golpe sin poder
     * guardar a cualquier cliente que aun no conozca las cuentas.
     */
    private Long accountId;

    /**
     * Como se reparte este gasto, si es que se reparte. Nulo = no se reparte.
     *
     * El `amount` de arriba SIGUE SIENDO EL TOTAL: los 120.000 que salieron
     * del banco. El reparto no cambia lo que salio, cambia cuanto de eso
     * cuenta como gasto tuyo.
     */
    private com.ohchurus.budget.enums.SplitMode splitMode;

    /** Quien participa y con que valor. Ver SplitInputDTO. */
    private java.util.List<SplitInputDTO> splits;

    private Boolean confirmed = true;
}

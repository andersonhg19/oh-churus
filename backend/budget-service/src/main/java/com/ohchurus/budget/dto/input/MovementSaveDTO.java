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

    /* Nulo = "no lo dije": al crear se toma como pagado, al editar se deja
       como estaba. Con true por defecto, corregir el valor de un pendiente
       desde un cliente que no manda el campo lo daba por pagado. */
    private Boolean confirmed;

    /* De que categoria de ahorro salio la plata. Nulo = no lo dije (al editar
       se deja como estaba); 0 = ya no sale del ahorro. */
    private Long fundedFromCategoryId;

    /* Con que tarjeta se pago. Nulo = no lo dije (al editar se deja como
       estaba); 0 = ya no fue con tarjeta. */
    private Long cardId;

    /* A cuantas cuotas, si fue con tarjeta. Nulo o 1 = una sola. Solo al
       crear: cambiar las cuotas de una compra ya anotada es borrarla y
       anotarla de nuevo. */
    @jakarta.validation.constraints.Min(1)
    @jakarta.validation.constraints.Max(72)
    private Integer installments;
}

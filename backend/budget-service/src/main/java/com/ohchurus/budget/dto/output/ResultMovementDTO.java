package com.ohchurus.budget.dto.output;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class ResultMovementDTO {

    private Long id;
    private Long userId;
    private Long categoryId;
    private LocalDate date;
    private BigDecimal amount;
    private String description;
    private Long scheduledMovementId;
    private Long parentMovementId;
    private Boolean confirmed;
    private Boolean active;

    private String categoryName;
    private String categoryType;
    private String categoryIcon;
    private String categoryColor;

    /** Cuanto se libero de este pendiente del plan. */
    private java.math.BigDecimal releasedAmount;
    /** De que ahorro salio la plata, si salio de uno. */
    private Long fundedFromCategoryId;
    /** Con que tarjeta se pago, y que cuota es. */
    private Long cardId;
    private Boolean cardPaid;
    private Integer installmentNumber;
    private Integer installmentTotal;
    private Long purchaseId;

    /* Ola 5: si la categoria es del hogar (la ven todos) o privada, y quien lo
       paga con su apodo. Sin esto el panel no podia separar "lo que pago yo" de
       "lo que paga el otro" ni lo comun de lo personal: ensenaba todo junto. */
    private Boolean shared;
    private String payerName;
}

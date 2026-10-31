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
    private Boolean isTransfer;
    private Long transferPairId;
    private Boolean confirmed;
    private Boolean active;

    private String categoryName;
    private String categoryType;
    private String categoryIcon;
    private String categoryColor;

    private Long accountId;
    private String accountName;
    private Boolean isOpening;

    private String splitMode;
    private Boolean isSettlement;

    /**
     * Lo que este movimiento cuenta como gasto MIO.
     *
     * Sin reparto es igual que `amount`. Con reparto es solo mi parte, y esa
     * es la diferencia entre un presupuesto que dice la verdad y uno que se
     * come el mes de "Restaurantes" con plata que me van a devolver.
     *
     * Van los dos campos a proposito: la lista tiene que poder ensenar
     * "120.000 (te tocan 40.000)". Con uno solo, la pantalla tendria que
     * elegir cual, y acabaria eligiendo distinto en cada sitio.
     */
    private java.math.BigDecimal myShare;
    /* Ola 5: si la categoria es del hogar (la ven todos) o privada, y quien lo
       paga con su apodo. Sin esto el panel no podia separar "lo que pago yo" de
       "lo que paga el otro" ni lo comun de lo personal: ensenaba todo junto. */
    private Boolean shared;
    private String payerName;
}

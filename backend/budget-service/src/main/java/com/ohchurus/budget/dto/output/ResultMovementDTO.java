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
}

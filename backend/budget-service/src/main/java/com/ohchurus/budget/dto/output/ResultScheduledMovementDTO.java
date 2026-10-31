package com.ohchurus.budget.dto.output;

import com.ohchurus.budget.enums.Frequency;
import com.ohchurus.budget.enums.WeekendPolicy;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class ResultScheduledMovementDTO {

    private Long id;
    private Long userId;
    private Long categoryId;
    private String name;
    private BigDecimal amount;
    private Frequency frequency;
    private Integer durationMonths;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer dayOfMonth;
    private Integer weekOfMonth;
    private Integer dayOfWeek;
    private WeekendPolicy weekendPolicy;
    private Boolean active;

    private Long payerUserId;
    private com.ohchurus.budget.enums.ScheduledKind kind;
    private String notes;
    // Enrichment fields
    /* Quien lo paga ya resuelto (el creador si payerUserId es nulo) y su
       apodo en el hogar. */
    private Long effectivePayerUserId;
    private String payerName;
    /* Cuotas: cuantas quedan desde hoy, cuando es la proxima, cuanto falta
       en total. Nulos si el programado no tiene fin. */
    private Integer remainingOccurrences;
    private LocalDate nextDate;
    private BigDecimal remainingTotal;
    private String categoryIcon;
    private String categoryColor;
    private Long categoryHouseholdId;
    private String categoryName;
    private String categoryType;
}

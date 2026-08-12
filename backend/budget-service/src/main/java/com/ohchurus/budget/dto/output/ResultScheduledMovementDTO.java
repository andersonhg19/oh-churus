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

    // Enrichment fields
    private String categoryName;
    private String categoryType;
}

package com.ohchurus.budget.dto.input;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class DashboardRequestDTO {

    @NotNull(message = "userId is required")
    private Long userId;

    @Min(value = 1, message = "budgetStartDay must be between 1 and 31")
    @Max(value = 31, message = "budgetStartDay must be between 1 and 31")
    private Integer budgetStartDay = 1;

    private LocalDate referenceDate;
}

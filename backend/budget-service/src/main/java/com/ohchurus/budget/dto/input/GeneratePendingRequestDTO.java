package com.ohchurus.budget.dto.input;

import jakarta.validation.constraints.*;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class GeneratePendingRequestDTO {
    @NotNull(message = "userId is required")
    private Long userId;
    @NotNull(message = "budgetStartDay is required")
    @Min(value = 1, message = "budgetStartDay must be between 1 and 31")
    @Max(value = 31, message = "budgetStartDay must be between 1 and 31")
    private Integer budgetStartDay;
}

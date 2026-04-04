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

    @NotNull(message = "userId is required")
    private Long userId;

    @NotNull(message = "categoryId is required")
    private Long categoryId;

    @NotNull(message = "date is required")
    private LocalDate date;

    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.01", message = "amount must be greater than 0")
    private BigDecimal amount;

    @Size(max = 255, message = "description must not exceed 255 characters")
    private String description;

    private Long scheduledMovementId;

    private Long parentMovementId;

    private Boolean confirmed = true;
}

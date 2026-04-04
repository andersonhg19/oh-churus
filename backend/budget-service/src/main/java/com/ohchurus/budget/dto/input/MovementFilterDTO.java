package com.ohchurus.budget.dto.input;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MovementFilterDTO {

    private Long userId;
    private Long categoryId;
    private LocalDate startDate;
    private LocalDate endDate;
    private Boolean confirmed;
    @Min(0)
    private int page = 0;
    @Min(1) @Max(100)
    private int size = 10;
}

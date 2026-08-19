package com.ohchurus.budget.dto.input;

import com.ohchurus.budget.enums.CategoryType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CategoryFilterDTO {

    private Long userId;
    private String name;
    private CategoryType type;
    @Min(0)
    private int page = 0;
    /** Mismo tope que MovementFilterDTO: ver el porque alli. */
    @Min(1) @Max(500)
    private int size = 10;
}

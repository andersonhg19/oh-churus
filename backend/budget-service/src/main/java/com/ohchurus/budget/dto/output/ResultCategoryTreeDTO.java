package com.ohchurus.budget.dto.output;

import com.ohchurus.budget.enums.CategoryType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class ResultCategoryTreeDTO {

    private Long id;
    private String name;
    private String description;
    private String icon;
    private String color;
    private CategoryType type;
    private Long householdId;
    private Boolean shared = false;
    private List<ResultCategoryTreeDTO> children = new ArrayList<>();
}

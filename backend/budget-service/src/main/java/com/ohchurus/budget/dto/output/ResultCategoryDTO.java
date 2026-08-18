package com.ohchurus.budget.dto.output;

import com.ohchurus.budget.enums.CategoryType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ResultCategoryDTO {

    private Long id;
    private Long userId;
    private String name;
    private String description;
    private Long parentId;
    private String icon;
    private String color;
    private CategoryType type;
    private Boolean active;
    private Long householdId;

    /** "Es dinero que me van a devolver": no descuenta su sobregiro del total. */
    private Boolean reimbursable;
    private Boolean shared = false;
}

package com.ohchurus.budget.mapper;

import com.ohchurus.budget.dto.output.ResultCategoryDTO;
import com.ohchurus.budget.dto.output.ResultCategoryTreeDTO;
import com.ohchurus.budget.entity.Category;

public interface CategoryMapper {

    ResultCategoryDTO toResultDTO(Category category);

    ResultCategoryTreeDTO toTreeDTO(Category category);
}

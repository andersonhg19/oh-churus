package com.ohchurus.budget.mapper.impl;

import com.ohchurus.budget.dto.output.ResultCategoryDTO;
import com.ohchurus.budget.dto.output.ResultCategoryTreeDTO;
import com.ohchurus.budget.entity.Category;
import com.ohchurus.budget.mapper.CategoryMapper;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

@Service
public class CategoryMapperImpl implements CategoryMapper {

    private final ModelMapper modelMapper;

    public CategoryMapperImpl(ModelMapper modelMapper) {
        this.modelMapper = modelMapper;
    }

    @Override
    public ResultCategoryDTO toResultDTO(Category category) {
        return (category != null) ? modelMapper.map(category, ResultCategoryDTO.class) : null;
    }

    @Override
    public ResultCategoryTreeDTO toTreeDTO(Category category) {
        if (category == null) return null;
        ResultCategoryTreeDTO dto = new ResultCategoryTreeDTO();
        dto.setId(category.getId());
        dto.setName(category.getName());
        dto.setDescription(category.getDescription());
        dto.setIcon(category.getIcon());
        dto.setColor(category.getColor());
        dto.setType(category.getType());
        dto.setHouseholdId(category.getHouseholdId());
        dto.setShared(category.getHouseholdId() != null);
        return dto;
    }
}

package com.ohchurus.budget.service;

import com.ohchurus.budget.dto.input.CategoryFilterDTO;
import com.ohchurus.budget.dto.input.CategorySaveDTO;
import com.ohchurus.budget.dto.output.ResultDTO;

public interface CategoryService {

    ResultDTO saveAndUpdate(CategorySaveDTO dto);

    ResultDTO getById(Long id);

    ResultDTO getAll(CategoryFilterDTO filter);

    ResultDTO getTree(Long userId);

    ResultDTO delete(Long id);

    ResultDTO typeList();
}

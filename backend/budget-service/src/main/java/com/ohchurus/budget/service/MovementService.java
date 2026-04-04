package com.ohchurus.budget.service;

import com.ohchurus.budget.dto.input.MovementFilterDTO;
import com.ohchurus.budget.dto.input.MovementSaveDTO;
import com.ohchurus.budget.dto.output.ResultDTO;

import java.time.LocalDate;

public interface MovementService {

    ResultDTO saveAndUpdate(MovementSaveDTO dto);

    ResultDTO getById(Long id);

    ResultDTO getAll(MovementFilterDTO filter);

    ResultDTO delete(Long id);

    ResultDTO confirm(Long id);

    ResultDTO confirmWithAmount(Long id, java.math.BigDecimal newAmount);

    ResultDTO getByPeriod(Long userId, LocalDate startDate, LocalDate endDate);

    ResultDTO getChildren(Long parentId);
}

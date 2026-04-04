package com.ohchurus.budget.service;

import com.ohchurus.budget.dto.input.ScheduledMovementFilterDTO;
import com.ohchurus.budget.dto.input.ScheduledMovementSaveDTO;
import com.ohchurus.budget.dto.output.ResultDTO;

public interface ScheduledMovementService {

    ResultDTO saveAndUpdate(ScheduledMovementSaveDTO dto);

    ResultDTO getById(Long id);

    ResultDTO getAll(ScheduledMovementFilterDTO filter);

    ResultDTO delete(Long id);

    ResultDTO generatePending(Long userId, int budgetStartDay);

    ResultDTO frequencyList();
}

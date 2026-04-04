package com.ohchurus.budget.mapper;

import com.ohchurus.budget.dto.output.ResultScheduledMovementDTO;
import com.ohchurus.budget.entity.ScheduledMovement;

public interface ScheduledMovementMapper {

    ResultScheduledMovementDTO toResultDTO(ScheduledMovement scheduledMovement);
}

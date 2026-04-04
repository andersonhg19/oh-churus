package com.ohchurus.budget.mapper;

import com.ohchurus.budget.dto.output.ResultMovementDTO;
import com.ohchurus.budget.entity.Movement;

public interface MovementMapper {

    ResultMovementDTO toResultDTO(Movement movement);
}

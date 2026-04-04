package com.ohchurus.budget.mapper.impl;

import com.ohchurus.budget.dto.output.ResultScheduledMovementDTO;
import com.ohchurus.budget.entity.ScheduledMovement;
import com.ohchurus.budget.mapper.ScheduledMovementMapper;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

@Service
public class ScheduledMovementMapperImpl implements ScheduledMovementMapper {

    private final ModelMapper modelMapper;

    public ScheduledMovementMapperImpl(ModelMapper modelMapper) {
        this.modelMapper = modelMapper;
    }

    @Override
    public ResultScheduledMovementDTO toResultDTO(ScheduledMovement scheduledMovement) {
        return (scheduledMovement != null) ? modelMapper.map(scheduledMovement, ResultScheduledMovementDTO.class) : null;
    }
}

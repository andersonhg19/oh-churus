package com.ohchurus.budget.mapper.impl;

import com.ohchurus.budget.dto.output.ResultMovementDTO;
import com.ohchurus.budget.entity.Movement;
import com.ohchurus.budget.mapper.MovementMapper;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

@Service
public class MovementMapperImpl implements MovementMapper {

    private final ModelMapper modelMapper;

    public MovementMapperImpl(ModelMapper modelMapper) {
        this.modelMapper = modelMapper;
    }

    @Override
    public ResultMovementDTO toResultDTO(Movement movement) {
        return (movement != null) ? modelMapper.map(movement, ResultMovementDTO.class) : null;
    }
}

package com.ohchurus.auth.mapper.impl;

import com.ohchurus.auth.dto.output.ResultUserDTO;
import com.ohchurus.auth.entity.User;
import com.ohchurus.auth.mapper.UserMapper;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

@Service
public class UserMapperImpl implements UserMapper {

    private final ModelMapper modelMapper;

    public UserMapperImpl(ModelMapper modelMapper) {
        this.modelMapper = modelMapper;
    }

    @Override
    public ResultUserDTO toResultDTO(User user) {
        return (user != null) ? modelMapper.map(user, ResultUserDTO.class) : null;
    }

    @Override
    public User toEntity(ResultUserDTO dto) {
        return (dto != null) ? modelMapper.map(dto, User.class) : null;
    }
}

package com.ohchurus.auth.mapper;

import com.ohchurus.auth.dto.output.ResultUserDTO;
import com.ohchurus.auth.entity.User;

public interface UserMapper {

    ResultUserDTO toResultDTO(User user);

    User toEntity(ResultUserDTO dto);
}

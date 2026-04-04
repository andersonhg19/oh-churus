package com.ohchurus.auth.service;

import com.ohchurus.auth.dto.input.UserFilterDTO;
import com.ohchurus.auth.dto.input.UserSaveDTO;
import com.ohchurus.auth.dto.output.ResultDTO;

public interface UserService {

    ResultDTO saveAndUpdate(UserSaveDTO dto);

    ResultDTO getById(Long id);

    ResultDTO getAll(UserFilterDTO filter);

    ResultDTO delete(Long id);
}

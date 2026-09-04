package com.ohchurus.auth.service;

import com.ohchurus.auth.dto.input.RoleSaveDTO;
import com.ohchurus.auth.dto.output.ResultDTO;

public interface RoleService {

    ResultDTO getAll();

    ResultDTO saveAndUpdate(RoleSaveDTO dto);

    ResultDTO desactivar(Long id);
}

package com.ohchurus.auth.service;

import com.ohchurus.auth.dto.input.AsignarRolesDTO;
import com.ohchurus.auth.dto.input.UserFilterDTO;
import com.ohchurus.auth.dto.input.UserSaveDTO;
import com.ohchurus.auth.dto.output.ResultDTO;

public interface AdminUserService {

    ResultDTO getAll(UserFilterDTO filter);

    ResultDTO getById(Long id);

    ResultDTO crear(UserSaveDTO dto);

    ResultDTO asignarRoles(Long userId, AsignarRolesDTO dto);

    ResultDTO cambiarActivacion(Long userId, boolean activar);
}

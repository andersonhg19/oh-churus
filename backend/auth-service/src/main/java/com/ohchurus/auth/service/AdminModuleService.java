package com.ohchurus.auth.service;

import com.ohchurus.auth.dto.input.PermisoSaveDTO;
import com.ohchurus.auth.dto.output.ResultDTO;

public interface AdminModuleService {

    /** Los modulos con sus paginas, incluidos los desactivados. */
    ResultDTO getModulos();

    /** Los permisos que tiene un rol, pagina a pagina. */
    ResultDTO getPermisosDeRol(String roleName);

    /** Fija lo que un rol puede hacer en una pagina. */
    ResultDTO guardarPermiso(PermisoSaveDTO dto);
}

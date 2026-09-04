package com.ohchurus.auth.service;

import com.ohchurus.auth.dto.input.AppConfigSaveDTO;
import com.ohchurus.auth.dto.output.ResultDTO;

public interface PreferenciasService {

    /** Moneda, decimales y formato de quien pregunta, ya resueltos. */
    ResultDTO misPreferencias();

    /** Los ajustes de la instalacion. Solo administracion. */
    ResultDTO getConfiguracion();

    ResultDTO guardarConfiguracion(AppConfigSaveDTO dto);
}

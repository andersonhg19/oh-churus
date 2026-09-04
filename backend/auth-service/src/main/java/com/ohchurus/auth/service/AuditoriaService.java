package com.ohchurus.auth.service;

import com.ohchurus.auth.dto.input.AuditoriaFilterDTO;
import com.ohchurus.auth.dto.output.ResultDTO;

public interface AuditoriaService {

    ResultDTO consultar(AuditoriaFilterDTO filtro);
}

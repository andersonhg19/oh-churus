package com.ohchurus.auth.service;

import com.ohchurus.auth.dto.output.ResultDTO;

public interface MenuService {

    /** El menu de quien hace la peticion, segun los roles que trae su token. */
    ResultDTO miMenu();
}

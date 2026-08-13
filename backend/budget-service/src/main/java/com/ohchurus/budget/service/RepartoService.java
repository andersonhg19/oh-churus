package com.ohchurus.budget.service;

import com.ohchurus.budget.dto.input.SettleDTO;
import com.ohchurus.budget.dto.output.ResultDTO;

public interface RepartoService {

    /** El balance neto con cada persona: quien te debe y a quien le debes. */
    ResultDTO balances();

    /** Anotar que una deuda se pago. */
    ResultDTO settle(SettleDTO dto);
}

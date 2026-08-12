package com.ohchurus.budget.service;

import com.ohchurus.budget.dto.input.AccountSaveDTO;
import com.ohchurus.budget.dto.input.ReconcileDTO;
import com.ohchurus.budget.dto.output.ResultDTO;

public interface AccountService {

    ResultDTO saveAndUpdate(AccountSaveDTO dto);

    ResultDTO getById(Long id);

    ResultDTO getAll();

    ResultDTO delete(Long id);

    ResultDTO reconcile(ReconcileDTO dto);

    ResultDTO kindList();
}

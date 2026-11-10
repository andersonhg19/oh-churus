package com.ohchurus.budget.service;

import com.ohchurus.budget.dto.output.ResultDTO;

import java.time.LocalDate;

public interface DashboardService {

    ResultDTO getPending(Long userId, Integer budgetStartDay);

    ResultDTO getPending(Long userId, Integer budgetStartDay, LocalDate referenceDate);
}

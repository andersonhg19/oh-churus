package com.ohchurus.budget.service;

import com.ohchurus.budget.dto.output.ResultDTO;

import java.time.LocalDate;

public interface DashboardService {

    ResultDTO getSummary(Long userId, Integer budgetStartDay);

    ResultDTO getSummary(Long userId, Integer budgetStartDay, LocalDate referenceDate);

    ResultDTO getByCategory(Long userId, Integer budgetStartDay);

    ResultDTO getByCategory(Long userId, Integer budgetStartDay, LocalDate referenceDate);

    ResultDTO getTrend(Long userId, Integer budgetStartDay);

    ResultDTO getPending(Long userId, Integer budgetStartDay);

    ResultDTO getPending(Long userId, Integer budgetStartDay, LocalDate referenceDate);

    ResultDTO getSplitSummary(Long userId, Integer budgetStartDay, LocalDate referenceDate);
}

package com.ohchurus.budget.controller;

import com.ohchurus.budget.dto.input.DashboardRequestDTO;
import com.ohchurus.budget.dto.output.ResultDTO;
import com.ohchurus.budget.service.DashboardService;
import com.ohchurus.budget.service.impl.BudgetAllocationServiceImpl;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;
    private final BudgetAllocationServiceImpl budgetAllocationService;

    public DashboardController(DashboardService dashboardService,
                                BudgetAllocationServiceImpl budgetAllocationService) {
        this.dashboardService = dashboardService;
        this.budgetAllocationService = budgetAllocationService;
    }

    @PostMapping(value = "/summary", produces = "application/json")
    public ResponseEntity<ResultDTO> summary(@Valid @RequestBody DashboardRequestDTO request) {
        return ResponseEntity.ok(dashboardService.getSummary(
                request.getUserId(), request.getBudgetStartDay(), request.getReferenceDate()));
    }

    @PostMapping(value = "/by-category", produces = "application/json")
    public ResponseEntity<ResultDTO> byCategory(@Valid @RequestBody DashboardRequestDTO request) {
        return ResponseEntity.ok(dashboardService.getByCategory(
                request.getUserId(), request.getBudgetStartDay(), request.getReferenceDate()));
    }

    @PostMapping(value = "/trend", produces = "application/json")
    public ResponseEntity<ResultDTO> trend(@Valid @RequestBody DashboardRequestDTO request) {
        return ResponseEntity.ok(dashboardService.getTrend(
                request.getUserId(), request.getBudgetStartDay()));
    }

    @PostMapping(value = "/pending", produces = "application/json")
    public ResponseEntity<ResultDTO> pending(@Valid @RequestBody DashboardRequestDTO request) {
        return ResponseEntity.ok(dashboardService.getPending(
                request.getUserId(), request.getBudgetStartDay(), request.getReferenceDate()));
    }

    @PostMapping(value = "/split-summary", produces = "application/json")
    public ResponseEntity<ResultDTO> splitSummary(@Valid @RequestBody DashboardRequestDTO request) {
        return ResponseEntity.ok(dashboardService.getSplitSummary(
                request.getUserId(), request.getBudgetStartDay(), request.getReferenceDate()));
    }

    @PostMapping(value = "/consolidated", produces = "application/json")
    public ResponseEntity<ResultDTO> consolidated(@Valid @RequestBody DashboardRequestDTO request) {
        return ResponseEntity.ok(budgetAllocationService.consolidated(
                request.getUserId(), request.getBudgetStartDay(), request.getReferenceDate()));
    }
}

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

    /*
     * De aqui en adelante, el userId sale del token.
     *
     * Antes llegaba en el cuerpo de la peticion, o sea que lo decidia el
     * cliente: bastaba con cambiar un numero para pedir el panel, los
     * movimientos o el Excel de otra persona. Se ignora lo que venga en el
     * cuerpo —el frontend puede seguir mandandolo— y manda el token.
     */

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
                com.ohchurus.budget.util.SecurityUtils.getAuthenticatedUserId(), request.getBudgetStartDay(), request.getReferenceDate()));
    }

    @PostMapping(value = "/by-category", produces = "application/json")
    public ResponseEntity<ResultDTO> byCategory(@Valid @RequestBody DashboardRequestDTO request) {
        return ResponseEntity.ok(dashboardService.getByCategory(
                com.ohchurus.budget.util.SecurityUtils.getAuthenticatedUserId(), request.getBudgetStartDay(), request.getReferenceDate()));
    }

    @PostMapping(value = "/trend", produces = "application/json")
    public ResponseEntity<ResultDTO> trend(@Valid @RequestBody DashboardRequestDTO request) {
        return ResponseEntity.ok(dashboardService.getTrend(
                com.ohchurus.budget.util.SecurityUtils.getAuthenticatedUserId(), request.getBudgetStartDay()));
    }

    @PostMapping(value = "/pending", produces = "application/json")
    public ResponseEntity<ResultDTO> pending(@Valid @RequestBody DashboardRequestDTO request) {
        return ResponseEntity.ok(dashboardService.getPending(
                com.ohchurus.budget.util.SecurityUtils.getAuthenticatedUserId(), request.getBudgetStartDay(), request.getReferenceDate()));
    }

    @PostMapping(value = "/split-summary", produces = "application/json")
    public ResponseEntity<ResultDTO> splitSummary(@Valid @RequestBody DashboardRequestDTO request) {
        return ResponseEntity.ok(dashboardService.getSplitSummary(
                com.ohchurus.budget.util.SecurityUtils.getAuthenticatedUserId(), request.getBudgetStartDay(), request.getReferenceDate()));
    }

    @PostMapping(value = "/consolidated", produces = "application/json")
    public ResponseEntity<ResultDTO> consolidated(@Valid @RequestBody DashboardRequestDTO request) {
        return ResponseEntity.ok(budgetAllocationService.consolidated(
                com.ohchurus.budget.util.SecurityUtils.getAuthenticatedUserId(), request.getBudgetStartDay(), request.getReferenceDate()));
    }
}

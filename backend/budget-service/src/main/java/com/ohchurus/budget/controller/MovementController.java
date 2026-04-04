package com.ohchurus.budget.controller;

import com.ohchurus.budget.dto.input.MovementFilterDTO;
import com.ohchurus.budget.dto.input.MovementSaveDTO;
import com.ohchurus.budget.dto.input.PeriodRequestDTO;
import com.ohchurus.budget.dto.output.ResultDTO;
import com.ohchurus.budget.service.MovementService;
import com.ohchurus.budget.service.impl.BudgetAllocationServiceImpl;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/v1/movements")
public class MovementController {

    private final MovementService movementService;
    private final BudgetAllocationServiceImpl budgetAllocationService;

    public MovementController(MovementService movementService,
                               BudgetAllocationServiceImpl budgetAllocationService) {
        this.movementService = movementService;
        this.budgetAllocationService = budgetAllocationService;
    }

    @PostMapping(value = "/save", produces = "application/json")
    public ResponseEntity<ResultDTO> save(@Valid @RequestBody MovementSaveDTO dto) {
        return ResponseEntity.ok(movementService.saveAndUpdate(dto));
    }

    @PostMapping(value = "/get/{id}", produces = "application/json")
    public ResponseEntity<ResultDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(movementService.getById(id));
    }

    @PostMapping(value = "/all", produces = "application/json")
    public ResponseEntity<ResultDTO> getAll(@Valid @RequestBody MovementFilterDTO filter) {
        return ResponseEntity.ok(movementService.getAll(filter));
    }

    @PostMapping(value = "/delete/{id}", produces = "application/json")
    public ResponseEntity<ResultDTO> delete(@PathVariable Long id) {
        return ResponseEntity.ok(movementService.delete(id));
    }

    @PostMapping(value = "/confirm/{id}", produces = "application/json")
    public ResponseEntity<ResultDTO> confirm(@PathVariable Long id,
            @RequestBody(required = false) java.util.Map<String, Object> body) {
        if (body != null && body.containsKey("amount")) {
            java.math.BigDecimal amount = new java.math.BigDecimal(body.get("amount").toString());
            return ResponseEntity.ok(movementService.confirmWithAmount(id, amount));
        }
        return ResponseEntity.ok(movementService.confirm(id));
    }

    @PostMapping(value = "/by-period", produces = "application/json")
    public ResponseEntity<ResultDTO> getByPeriod(@Valid @RequestBody PeriodRequestDTO dto) {
        return ResponseEntity.ok(movementService.getByPeriod(dto.getUserId(), dto.getStartDate(), dto.getEndDate()));
    }

    @PostMapping(value = "/children/{parentId}", produces = "application/json")
    public ResponseEntity<ResultDTO> getChildren(@PathVariable Long parentId) {
        return ResponseEntity.ok(movementService.getChildren(parentId));
    }

    @PostMapping(value = "/transfer", produces = "application/json")
    public ResponseEntity<ResultDTO> transfer(@RequestBody Map<String, Object> body) {
        Long userId = Long.valueOf(body.get("userId").toString());
        Long fromCategoryId = Long.valueOf(body.get("fromCategoryId").toString());
        Long toCategoryId = Long.valueOf(body.get("toCategoryId").toString());
        BigDecimal amount = new BigDecimal(body.get("amount").toString());
        String description = body.containsKey("description") ? (String) body.get("description") : null;
        return ResponseEntity.ok(budgetAllocationService.transfer(userId, fromCategoryId, toCategoryId, amount, description));
    }
}

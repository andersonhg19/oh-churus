package com.ohchurus.budget.controller;

import com.ohchurus.budget.dto.output.ResultDTO;
import com.ohchurus.budget.service.impl.BudgetAllocationServiceImpl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/v1/budget-allocation")
public class BudgetAllocationController {

    private final BudgetAllocationServiceImpl service;

    public BudgetAllocationController(BudgetAllocationServiceImpl service) {
        this.service = service;
    }

    @PostMapping(value = "/save", produces = "application/json")
    public ResponseEntity<ResultDTO> save(@RequestBody Map<String, Object> body) {
        Long userId = Long.valueOf(body.get("userId").toString());
        Long categoryId = Long.valueOf(body.get("categoryId").toString());
        BigDecimal amount = new BigDecimal(body.get("amount").toString());
        String notes = body.containsKey("notes") ? (String) body.get("notes") : null;
        Integer budgetStartDay = body.containsKey("budgetStartDay")
                ? Integer.valueOf(body.get("budgetStartDay").toString()) : 1;
        LocalDate ref = body.containsKey("referenceDate") && body.get("referenceDate") != null
                ? LocalDate.parse(body.get("referenceDate").toString()) : null;
        return ResponseEntity.ok(service.save(userId, categoryId, amount, notes, budgetStartDay, ref));
    }

    @PostMapping(value = "/list", produces = "application/json")
    public ResponseEntity<ResultDTO> list(@RequestBody Map<String, Object> body) {
        Long userId = Long.valueOf(body.get("userId").toString());
        Integer budgetStartDay = body.containsKey("budgetStartDay")
                ? Integer.valueOf(body.get("budgetStartDay").toString()) : 1;
        LocalDate ref = body.containsKey("referenceDate") && body.get("referenceDate") != null
                ? LocalDate.parse(body.get("referenceDate").toString()) : null;
        return ResponseEntity.ok(service.list(userId, budgetStartDay, ref));
    }

    @PostMapping(value = "/summary", produces = "application/json")
    public ResponseEntity<ResultDTO> summary(@RequestBody Map<String, Object> body) {
        Long userId = Long.valueOf(body.get("userId").toString());
        Integer budgetStartDay = body.containsKey("budgetStartDay")
                ? Integer.valueOf(body.get("budgetStartDay").toString()) : 1;
        LocalDate ref = body.containsKey("referenceDate") && body.get("referenceDate") != null
                ? LocalDate.parse(body.get("referenceDate").toString()) : null;
        return ResponseEntity.ok(service.summary(userId, budgetStartDay, ref));
    }

    @PostMapping(value = "/delete/{id}", produces = "application/json")
    public ResponseEntity<ResultDTO> delete(@PathVariable Long id) {
        return ResponseEntity.ok(service.delete(id));
    }
}

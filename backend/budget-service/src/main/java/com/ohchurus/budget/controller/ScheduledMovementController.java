package com.ohchurus.budget.controller;

import com.ohchurus.budget.dto.input.GeneratePendingRequestDTO;
import com.ohchurus.budget.dto.input.ScheduledMovementFilterDTO;
import com.ohchurus.budget.dto.input.ScheduledMovementSaveDTO;
import com.ohchurus.budget.dto.output.ResultDTO;
import com.ohchurus.budget.service.ScheduledMovementService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/scheduled")
public class ScheduledMovementController {

    private final ScheduledMovementService scheduledMovementService;

    public ScheduledMovementController(ScheduledMovementService scheduledMovementService) {
        this.scheduledMovementService = scheduledMovementService;
    }

    @PostMapping(value = "/save", produces = "application/json")
    public ResponseEntity<ResultDTO> save(@Valid @RequestBody ScheduledMovementSaveDTO dto) {
        return ResponseEntity.ok(scheduledMovementService.saveAndUpdate(dto));
    }

    @PostMapping(value = "/get/{id}", produces = "application/json")
    public ResponseEntity<ResultDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(scheduledMovementService.getById(id));
    }

    @PostMapping(value = "/all", produces = "application/json")
    public ResponseEntity<ResultDTO> getAll(@Valid @RequestBody ScheduledMovementFilterDTO filter) {
        return ResponseEntity.ok(scheduledMovementService.getAll(filter));
    }

    @PostMapping(value = "/delete/{id}", produces = "application/json")
    public ResponseEntity<ResultDTO> delete(@PathVariable Long id) {
        return ResponseEntity.ok(scheduledMovementService.delete(id));
    }

    @PostMapping(value = "/generate-pending", produces = "application/json")
    public ResponseEntity<ResultDTO> generatePending(@Valid @RequestBody GeneratePendingRequestDTO dto) {
        return ResponseEntity.ok(scheduledMovementService.generatePending(dto.getUserId(), dto.getBudgetStartDay()));
    }

    @PostMapping(value = "/frequency-list", produces = "application/json")
    public ResponseEntity<ResultDTO> frequencyList() {
        return ResponseEntity.ok(scheduledMovementService.frequencyList());
    }
}

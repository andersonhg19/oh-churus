package com.ohchurus.budget.controller;

import com.ohchurus.budget.dto.input.GeneratePendingRequestDTO;
import com.ohchurus.budget.dto.input.MaterializeOccurrencesDTO;
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

    /*
     * De aqui en adelante, el userId sale del token.
     *
     * Antes llegaba en el cuerpo de la peticion, o sea que lo decidia el
     * cliente: bastaba con cambiar un numero para pedir el panel, los
     * movimientos o el Excel de otra persona. Se ignora lo que venga en el
     * cuerpo —el frontend puede seguir mandandolo— y manda el token.
     */

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
        filter.setUserId(com.ohchurus.budget.util.SecurityUtils.getAuthenticatedUserId());
        return ResponseEntity.ok(scheduledMovementService.getAll(filter));
    }

    @PostMapping(value = "/delete/{id}", produces = "application/json")
    public ResponseEntity<ResultDTO> delete(@PathVariable Long id) {
        return ResponseEntity.ok(scheduledMovementService.delete(id));
    }

    @PostMapping(value = "/generate-pending", produces = "application/json")
    public ResponseEntity<ResultDTO> generatePending(@Valid @RequestBody GeneratePendingRequestDTO dto) {
        return ResponseEntity.ok(scheduledMovementService.generatePending(com.ohchurus.budget.util.SecurityUtils.getAuthenticatedUserId(), dto.getBudgetStartDay()));
    }

    /*
     * La otra mitad del tope de materializacion: generate-pending propone las
     * ocurrencias atrasadas y esto crea las que la persona acepte. El cuerpo
     * solo senala CUALES —(scheduledMovementId, periodStart)—; el importe y la
     * fecha los recalcula el servidor desde el programado.
     */
    @PostMapping(value = "/materialize", produces = "application/json")
    public ResponseEntity<ResultDTO> materialize(@Valid @RequestBody MaterializeOccurrencesDTO dto) {
        return ResponseEntity.ok(scheduledMovementService.materialize(dto));
    }

    @PostMapping(value = "/frequency-list", produces = "application/json")
    public ResponseEntity<ResultDTO> frequencyList() {
        return ResponseEntity.ok(scheduledMovementService.frequencyList());
    }
}

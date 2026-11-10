package com.ohchurus.budget.controller;

import com.ohchurus.budget.dto.input.PlanRequestDTO;
import com.ohchurus.budget.dto.output.ResultDTO;
import com.ohchurus.budget.service.impl.DeudasService;
import com.ohchurus.budget.util.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Lo que se debe: tarjetas y cuotas. De quien es lo dice el token; de la
 * pareja solo se ve lo de categorias comunes.
 */
@RestController
@RequestMapping("/v1/debts")
public class DebtController {

    private final DeudasService deudas;

    public DebtController(DeudasService deudas) {
        this.deudas = deudas;
    }

    @PostMapping(value = "/all", produces = "application/json")
    public ResponseEntity<ResultDTO> all(@Valid @RequestBody(required = false) PlanRequestDTO body) {
        return ResponseEntity.ok(deudas.deudas(SecurityUtils.getAuthenticatedUserId(),
                body != null ? body.getReferenceDate() : null));
    }

    /** Pagar hoy la ultima cuota que falta de una deuda en cuotas. */
    @PostMapping(value = "/advance/{scheduledMovementId}", produces = "application/json")
    public ResponseEntity<ResultDTO> advance(@PathVariable Long scheduledMovementId) {
        return ResponseEntity.ok(deudas.adelantarCuota(SecurityUtils.getAuthenticatedUserId(), scheduledMovementId));
    }
}

package com.ohchurus.budget.controller;

import com.ohchurus.budget.dto.input.PlanRequestDTO;
import com.ohchurus.budget.dto.output.ResultDTO;
import com.ohchurus.budget.service.impl.PlanFinancieroService;
import com.ohchurus.budget.util.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * El plan financiero: quincena, calendario, quien paga que y proyeccion.
 * Todo de solo lectura y calculado; ver PlanFinancieroService.
 *
 * Como en el resto de la API, de quien es el plan lo dice el token: el cuerpo
 * solo trae fechas y el dia de corte.
 */
@RestController
@RequestMapping("/v1/plan")
public class PlanController {

    private final PlanFinancieroService plan;

    public PlanController(PlanFinancieroService plan) {
        this.plan = plan;
    }

    @PostMapping(value = "/quincena", produces = "application/json")
    public ResponseEntity<ResultDTO> quincena(@Valid @RequestBody PlanRequestDTO body) {
        return ResponseEntity.ok(plan.quincena(SecurityUtils.getAuthenticatedUserId(), body.getReferenceDate()));
    }

    @PostMapping(value = "/calendar", produces = "application/json")
    public ResponseEntity<ResultDTO> calendar(@Valid @RequestBody PlanRequestDTO body) {
        return ResponseEntity.ok(plan.calendario(SecurityUtils.getAuthenticatedUserId(), body.getFrom(), body.getTo()));
    }

    @PostMapping(value = "/responsibilities", produces = "application/json")
    public ResponseEntity<ResultDTO> responsibilities(@Valid @RequestBody PlanRequestDTO body) {
        return ResponseEntity.ok(plan.responsabilidades(SecurityUtils.getAuthenticatedUserId(),
                body.getBudgetStartDay(), body.getReferenceDate()));
    }

    @PostMapping(value = "/projection", produces = "application/json")
    public ResponseEntity<ResultDTO> projection(@Valid @RequestBody PlanRequestDTO body) {
        return ResponseEntity.ok(plan.proyeccion(SecurityUtils.getAuthenticatedUserId(),
                body.getBudgetStartDay(), body.getPeriods() != null ? body.getPeriods() : 3));
    }
}

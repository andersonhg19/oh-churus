package com.ohchurus.budget.controller;

import com.ohchurus.budget.dto.input.EstadoRequestDTO;
import com.ohchurus.budget.dto.output.ResultDTO;
import com.ohchurus.budget.service.impl.EstadoDelPeriodoService;
import com.ohchurus.budget.util.SecurityUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Las cifras del presupuesto conjunto, con una sola definicion cada una:
 * ingresos, presupuestado, real, comprometido, disponible, ahorro, bolsas,
 * responsabilidades, fondos y cumplimiento. Todas las pantallas leen de aqui.
 */
@RestController
@RequestMapping("/v1/estado")
public class EstadoController {

    private final EstadoDelPeriodoService estado;

    public EstadoController(EstadoDelPeriodoService estado) {
        this.estado = estado;
    }

    @PostMapping(value = "/periodo", produces = "application/json")
    public ResponseEntity<ResultDTO> periodo(@RequestBody EstadoRequestDTO body) {
        return ResponseEntity.ok(estado.estado(SecurityUtils.getAuthenticatedUserId(),
                body.getBudgetStartDay(), body.getReferenceDate(), body.getScope(), body.getPersonUserId()));
    }

    /** Presupuestado contra real, periodo por periodo: el cierre de cada mes. */
    @PostMapping(value = "/historial", produces = "application/json")
    public ResponseEntity<ResultDTO> historial(@RequestBody EstadoRequestDTO body) {
        return ResponseEntity.ok(estado.historial(SecurityUtils.getAuthenticatedUserId(),
                body.getBudgetStartDay(), body.getReferenceDate(),
                body.getPeriods() != null ? body.getPeriods() : 6, body.getScope(), body.getPersonUserId()));
    }
}

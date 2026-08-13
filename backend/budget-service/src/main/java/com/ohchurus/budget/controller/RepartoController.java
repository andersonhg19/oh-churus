package com.ohchurus.budget.controller;

import com.ohchurus.budget.dto.input.SettleDTO;
import com.ohchurus.budget.dto.output.ResultDTO;
import com.ohchurus.budget.service.RepartoService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * El reparto en si se guarda con el movimiento (ver MovementSaveDTO): repartir
 * es parte de anotar el gasto, no un segundo paso — un flujo de dos pasos
 * garantiza que a la mitad de los gastos se les olvide el segundo.
 *
 * Aqui vive lo que no cuelga de un movimiento concreto: el balance con cada
 * persona y la liquidacion.
 */
@RestController
@RequestMapping("/v1/splits")
public class RepartoController {

    private final RepartoService repartoService;

    public RepartoController(RepartoService repartoService) {
        this.repartoService = repartoService;
    }

    /** No recibe nada: los balances son los de quien pide, y eso lo dice el token. */
    @PostMapping(value = "/balances", produces = "application/json")
    public ResponseEntity<ResultDTO> balances(@RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(repartoService.balances());
    }

    @PostMapping(value = "/settle", produces = "application/json")
    public ResponseEntity<ResultDTO> settle(@Valid @RequestBody SettleDTO dto) {
        return ResponseEntity.ok(repartoService.settle(dto));
    }
}

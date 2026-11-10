package com.ohchurus.budget.controller;

import com.ohchurus.budget.dto.input.CardPayDTO;
import com.ohchurus.budget.dto.input.CardSaveDTO;
import com.ohchurus.budget.dto.output.ResultDTO;
import com.ohchurus.budget.service.impl.DeudasService;
import com.ohchurus.budget.util.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Las tarjetas de credito de quien pide. El dueno lo pone el token: no se
 * puede crear, pagar ni borrar la tarjeta de otra persona.
 */
@RestController
@RequestMapping("/v1/cards")
public class CardController {

    private final DeudasService deudas;

    public CardController(DeudasService deudas) {
        this.deudas = deudas;
    }

    @PostMapping(value = "/save", produces = "application/json")
    public ResponseEntity<ResultDTO> save(@Valid @RequestBody CardSaveDTO body) {
        return ResponseEntity.ok(deudas.guardarTarjeta(SecurityUtils.getAuthenticatedUserId(),
                body.getId(), body.getName(), body.getColor(), body.getPaymentDay(), body.getCutoffDay()));
    }

    /** Mis tarjetas, con lo que debo en cada una. */
    @PostMapping(value = "/mine", produces = "application/json")
    public ResponseEntity<ResultDTO> mine() {
        return ResponseEntity.ok(deudas.misTarjetas(SecurityUtils.getAuthenticatedUserId()));
    }

    @PostMapping(value = "/pay/{id}", produces = "application/json")
    public ResponseEntity<ResultDTO> pay(@PathVariable Long id, @RequestBody(required = false) CardPayDTO body) {
        return ResponseEntity.ok(deudas.pagarTarjeta(SecurityUtils.getAuthenticatedUserId(), id,
                body != null ? body.getUpTo() : null, body != null ? body.getAmount() : null));
    }

    @PostMapping(value = "/delete/{id}", produces = "application/json")
    public ResponseEntity<ResultDTO> delete(@PathVariable Long id) {
        return ResponseEntity.ok(deudas.borrarTarjeta(SecurityUtils.getAuthenticatedUserId(), id));
    }
}

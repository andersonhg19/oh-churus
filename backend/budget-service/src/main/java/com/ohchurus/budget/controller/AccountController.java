package com.ohchurus.budget.controller;

import com.ohchurus.budget.dto.input.AccountSaveDTO;
import com.ohchurus.budget.dto.input.ReconcileDTO;
import com.ohchurus.budget.dto.output.ResultDTO;
import com.ohchurus.budget.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/v1/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping(value = "/save", produces = "application/json")
    public ResponseEntity<ResultDTO> save(@Valid @RequestBody AccountSaveDTO dto) {
        return ResponseEntity.ok(accountService.saveAndUpdate(dto));
    }

    @PostMapping(value = "/get/{id}", produces = "application/json")
    public ResponseEntity<ResultDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(accountService.getById(id));
    }

    /**
     * No recibe nada: las cuentas que se devuelven son las de quien pide, y eso
     * lo dice el token. El cuerpo se acepta y se ignora porque el resto de la
     * API lo lleva y el cliente lo manda por costumbre.
     */
    @PostMapping(value = "/all", produces = "application/json")
    public ResponseEntity<ResultDTO> getAll(@RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(accountService.getAll());
    }

    @PostMapping(value = "/delete/{id}", produces = "application/json")
    public ResponseEntity<ResultDTO> delete(@PathVariable Long id) {
        return ResponseEntity.ok(accountService.delete(id));
    }

    @PostMapping(value = "/reconcile", produces = "application/json")
    public ResponseEntity<ResultDTO> reconcile(@Valid @RequestBody ReconcileDTO dto) {
        return ResponseEntity.ok(accountService.reconcile(dto));
    }

    @PostMapping(value = "/kind-list", produces = "application/json")
    public ResponseEntity<ResultDTO> kindList() {
        return ResponseEntity.ok(accountService.kindList());
    }
}

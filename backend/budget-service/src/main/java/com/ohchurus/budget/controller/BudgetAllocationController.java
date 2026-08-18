package com.ohchurus.budget.controller;

import com.ohchurus.budget.dto.input.AsignacionGuardarDTO;
import com.ohchurus.budget.dto.input.MoverEntreSobresDTO;
import com.ohchurus.budget.dto.input.AsignacionPeriodoDTO;
import com.ohchurus.budget.dto.output.ResultDTO;
import com.ohchurus.budget.service.impl.BudgetAllocationServiceImpl;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/budget-allocation")
public class BudgetAllocationController {

    /*
     * De aqui en adelante, el userId sale del token.
     *
     * Antes llegaba en el cuerpo de la peticion, o sea que lo decidia el
     * cliente: bastaba con cambiar un numero para pedir el panel, los
     * movimientos o el Excel de otra persona. Se ignora lo que venga en el
     * cuerpo —el frontend puede seguir mandandolo— y manda el token.
     */

    private final BudgetAllocationServiceImpl service;

    public BudgetAllocationController(BudgetAllocationServiceImpl service) {
        this.service = service;
    }

    @PostMapping(value = "/save", produces = "application/json")
    public ResponseEntity<ResultDTO> save(@Valid @RequestBody AsignacionGuardarDTO body) {
        Long userId = com.ohchurus.budget.util.SecurityUtils.getAuthenticatedUserId();
        return ResponseEntity.ok(service.save(userId, body.getCategoryId(), body.getAmount(),
                body.getNotes(), body.getBudgetStartDay(), body.getReferenceDate()));
    }

    @PostMapping(value = "/list", produces = "application/json")
    public ResponseEntity<ResultDTO> list(@Valid @RequestBody AsignacionPeriodoDTO body) {
        Long userId = com.ohchurus.budget.util.SecurityUtils.getAuthenticatedUserId();
        return ResponseEntity.ok(service.list(userId, body.getBudgetStartDay(), body.getReferenceDate()));
    }

    @PostMapping(value = "/summary", produces = "application/json")
    public ResponseEntity<ResultDTO> summary(@Valid @RequestBody AsignacionPeriodoDTO body) {
        Long userId = com.ohchurus.budget.util.SecurityUtils.getAuthenticatedUserId();
        return ResponseEntity.ok(service.summary(userId, body.getBudgetStartDay(), body.getReferenceDate()));
    }

    /**
     * Los sobres del periodo con su arrastre.
     *
     * No recibe ningun id: los sobres son los de quien pide, y eso lo dice el
     * token. Del cuerpo solo se miran el dia de corte y la fecha de referencia.
     */
    @PostMapping(value = "/envelopes", produces = "application/json")
    public ResponseEntity<ResultDTO> envelopes(@Valid @RequestBody AsignacionPeriodoDTO body) {
        return ResponseEntity.ok(service.envelopes(body.getBudgetStartDay(), body.getReferenceDate()));
    }

    @PostMapping(value = "/move", produces = "application/json")
    public ResponseEntity<ResultDTO> move(@Valid @RequestBody MoverEntreSobresDTO body) {
        return ResponseEntity.ok(service.move(body.getFromCategoryId(), body.getToCategoryId(),
                body.getAmount(), body.getBudgetStartDay(), body.getReferenceDate()));
    }

    @PostMapping(value = "/delete/{id}", produces = "application/json")
    public ResponseEntity<ResultDTO> delete(@PathVariable Long id) {
        return ResponseEntity.ok(service.delete(id));
    }
}

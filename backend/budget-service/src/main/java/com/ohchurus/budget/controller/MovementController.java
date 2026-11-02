package com.ohchurus.budget.controller;

import com.ohchurus.budget.dto.input.MovementFilterDTO;
import com.ohchurus.budget.dto.input.MovementSaveDTO;
import com.ohchurus.budget.dto.input.PeriodRequestDTO;
import com.ohchurus.budget.dto.input.TransferenciaDTO;
import com.ohchurus.budget.dto.output.ResultDTO;
import com.ohchurus.budget.service.MovementService;
import com.ohchurus.budget.service.impl.BudgetAllocationServiceImpl;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/movements")
public class MovementController {

    /*
     * De aqui en adelante, el userId sale del token.
     *
     * Antes llegaba en el cuerpo de la peticion, o sea que lo decidia el
     * cliente: bastaba con cambiar un numero para pedir el panel, los
     * movimientos o el Excel de otra persona. Se ignora lo que venga en el
     * cuerpo —el frontend puede seguir mandandolo— y manda el token.
     */

    private final MovementService movementService;
    private final BudgetAllocationServiceImpl budgetAllocationService;

    public MovementController(MovementService movementService,
                               BudgetAllocationServiceImpl budgetAllocationService) {
        this.movementService = movementService;
        this.budgetAllocationService = budgetAllocationService;
    }

    @PostMapping(value = "/save", produces = "application/json")
    public ResponseEntity<ResultDTO> save(@Valid @RequestBody MovementSaveDTO dto) {
        return ResponseEntity.ok(movementService.saveAndUpdate(dto));
    }

    @PostMapping(value = "/get/{id}", produces = "application/json")
    public ResponseEntity<ResultDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(movementService.getById(id));
    }

    @PostMapping(value = "/all", produces = "application/json")
    public ResponseEntity<ResultDTO> getAll(@Valid @RequestBody MovementFilterDTO filter) {
        filter.setUserId(com.ohchurus.budget.util.SecurityUtils.getAuthenticatedUserId());
        return ResponseEntity.ok(movementService.getAll(filter));
    }

    @PostMapping(value = "/delete/{id}", produces = "application/json")
    public ResponseEntity<ResultDTO> delete(@PathVariable Long id) {
        return ResponseEntity.ok(movementService.delete(id));
    }

    @PostMapping(value = "/confirm/{id}", produces = "application/json")
    public ResponseEntity<ResultDTO> confirm(@PathVariable Long id,
            @RequestBody(required = false) java.util.Map<String, Object> body) {
        if (body != null && body.containsKey("amount")) {
            java.math.BigDecimal amount = new java.math.BigDecimal(body.get("amount").toString());
            return ResponseEntity.ok(movementService.confirmWithAmount(id, amount));
        }
        return ResponseEntity.ok(movementService.confirm(id));
    }

    /**
     * Libera plata de un pendiente del plan para usarla en otra cosa, sin
     * cambiar el presupuesto. Cuerpo opcional {"amount": 2000000}; sin monto
     * se libera todo.
     */
    @PostMapping(value = "/release/{id}", produces = "application/json")
    public ResponseEntity<ResultDTO> release(@PathVariable Long id,
            @RequestBody(required = false) java.util.Map<String, Object> body) {
        java.math.BigDecimal monto = body != null && body.get("amount") != null
                ? new java.math.BigDecimal(body.get("amount").toString()) : null;
        return ResponseEntity.ok(movementService.liberar(id, monto));
    }

    /**
     * Confirma varios pendientes de una vez: el ahorro, el arriendo y las
     * cuotas de la tarjeta en un solo toque. Cuerpo:
     * {"items":[{"id":12},{"id":13,"amount":150000}]}. Es todo o nada: si uno
     * no se puede confirmar, no se confirma ninguno y se dice cual fallo.
     */
    @PostMapping(value = "/confirm-batch", produces = "application/json")
    public ResponseEntity<ResultDTO> confirmBatch(@RequestBody java.util.Map<String, Object> body) {
        Object items = body != null ? body.get("items") : null;
        if (!(items instanceof java.util.List<?> lista) || lista.isEmpty()) {
            return ResponseEntity.ok(new ResultDTO(false, "No hay pagos para confirmar", 400));
        }
        java.util.List<Long> ids = new java.util.ArrayList<>();
        java.util.List<java.math.BigDecimal> montos = new java.util.ArrayList<>();
        for (Object o : lista) {
            if (!(o instanceof java.util.Map<?, ?> item) || item.get("id") == null) {
                return ResponseEntity.ok(new ResultDTO(false, "Cada pago necesita su id", 400));
            }
            ids.add(Long.valueOf(item.get("id").toString()));
            montos.add(item.get("amount") != null ? new java.math.BigDecimal(item.get("amount").toString()) : null);
        }
        return ResponseEntity.ok(movementService.confirmarVarios(ids, montos));
    }

    /**
     * Cambia quien paga UN pendiente (esta vez lo paga el otro). El cambio
     * permanente se hace en el programado; esto es para la eventualidad.
     * payerUserId no es la identidad de quien llama: es el miembro del hogar
     * que pasa a ser responsable, y el servicio lo valida.
     */
    @PostMapping(value = "/reassign/{id}", produces = "application/json")
    public ResponseEntity<ResultDTO> reassign(@PathVariable Long id,
            @RequestBody java.util.Map<String, Object> body) {
        Object quien = body != null ? body.get("payerUserId") : null;
        if (quien == null) {
            return ResponseEntity.ok(new ResultDTO(false, "payerUserId es obligatorio", 400));
        }
        return ResponseEntity.ok(movementService.reasignarPagador(id, Long.valueOf(quien.toString())));
    }

    @PostMapping(value = "/by-period", produces = "application/json")
    public ResponseEntity<ResultDTO> getByPeriod(@Valid @RequestBody PeriodRequestDTO dto) {
        return ResponseEntity.ok(movementService.getByPeriod(com.ohchurus.budget.util.SecurityUtils.getAuthenticatedUserId(), dto.getStartDate(), dto.getEndDate()));
    }

    @PostMapping(value = "/children/{parentId}", produces = "application/json")
    public ResponseEntity<ResultDTO> getChildren(@PathVariable Long parentId) {
        return ResponseEntity.ok(movementService.getChildren(parentId));
    }

    @PostMapping(value = "/transfer", produces = "application/json")
    public ResponseEntity<ResultDTO> transfer(@Valid @RequestBody TransferenciaDTO body) {
        Long userId = com.ohchurus.budget.util.SecurityUtils.getAuthenticatedUserId();
        return ResponseEntity.ok(budgetAllocationService.transfer(userId, body.getFromCategoryId(),
                body.getToCategoryId(), body.getAmount(), body.getDescription()));
    }
}

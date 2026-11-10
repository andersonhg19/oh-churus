package com.ohchurus.budget.controller;

import com.ohchurus.budget.dto.input.DashboardRequestDTO;
import com.ohchurus.budget.dto.output.ResultDTO;
import com.ohchurus.budget.service.DashboardService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/dashboard")
public class DashboardController {

    /*
     * De aqui en adelante, el userId sale del token.
     *
     * Antes llegaba en el cuerpo de la peticion, o sea que lo decidia el
     * cliente: bastaba con cambiar un numero para pedir el panel, los
     * movimientos o el Excel de otra persona. Se ignora lo que venga en el
     * cuerpo —el frontend puede seguir mandandolo— y manda el token.
     */

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @PostMapping(value = "/pending", produces = "application/json")
    public ResponseEntity<ResultDTO> pending(@Valid @RequestBody DashboardRequestDTO request) {
        return ResponseEntity.ok(dashboardService.getPending(
                com.ohchurus.budget.util.SecurityUtils.getAuthenticatedUserId(), corte(request.getBudgetStartDay()), request.getReferenceDate()));
    }

    /* Sin dia de corte se usa el 1, como en el resto de la app: antes el
       panel respondia 500 al desempaquetar el nulo. */
    private static Integer corte(Integer dia) {
        return dia != null ? dia : 1;
    }
}

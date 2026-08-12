package com.ohchurus.budget.controller;

import com.ohchurus.budget.dto.input.CrearHogarDTO;
import com.ohchurus.budget.dto.input.HogarPorUsuarioDTO;
import com.ohchurus.budget.dto.input.InvitacionPorCorreoDTO;
import com.ohchurus.budget.dto.input.MiembroHogarDTO;
import com.ohchurus.budget.dto.output.ResultDTO;
import com.ohchurus.budget.service.impl.HouseholdServiceImpl;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/household")
public class HouseholdController {

    /*
     * De aqui en adelante, el userId sale del token.
     *
     * Antes llegaba en el cuerpo de la peticion, o sea que lo decidia el
     * cliente: bastaba con cambiar un numero para pedir el panel, los
     * movimientos o el Excel de otra persona. Se ignora lo que venga en el
     * cuerpo —el frontend puede seguir mandandolo— y manda el token.
     */

    private final HouseholdServiceImpl householdService;

    public HouseholdController(HouseholdServiceImpl householdService) {
        this.householdService = householdService;
    }

    @PostMapping(value = "/create", produces = "application/json")
    public ResponseEntity<ResultDTO> create(@Valid @RequestBody CrearHogarDTO body) {
        Long userId = com.ohchurus.budget.util.SecurityUtils.getAuthenticatedUserId();   // el dueno es quien lo crea
        return ResponseEntity.ok(householdService.create(body.getName(), userId));
    }

    /* En add-member y remove-member el userId del cuerpo es el MIEMBRO objetivo
       —a quien se invita o se saca—, no quien llama. Ese si viene del cliente.
       Quien llama sale del token y el servicio exige que sea el OWNER. */
    @PostMapping(value = "/add-member", produces = "application/json")
    public ResponseEntity<ResultDTO> addMember(@Valid @RequestBody MiembroHogarDTO body) {
        return ResponseEntity.ok(householdService.addMember(body.getHouseholdId(), body.getUserId()));
    }

    /* Invitar por correo. El id del invitado lo resuelve el servidor: la
       pantalla pedia el id de fila de la base de datos y nadie conoce el suyo.
       add-member sigue existiendo para lo que ya lo usa. */
    @PostMapping(value = "/invite", produces = "application/json")
    public ResponseEntity<ResultDTO> invite(@Valid @RequestBody InvitacionPorCorreoDTO body) {
        return ResponseEntity.ok(householdService.invitarPorCorreo(body.getHouseholdId(), body.getEmail()));
    }

    @PostMapping(value = "/remove-member", produces = "application/json")
    public ResponseEntity<ResultDTO> removeMember(@Valid @RequestBody MiembroHogarDTO body) {
        return ResponseEntity.ok(householdService.removeMember(body.getHouseholdId(), body.getUserId()));
    }

    @PostMapping(value = "/by-user", produces = "application/json")
    public ResponseEntity<ResultDTO> getByUser(@Valid @RequestBody HogarPorUsuarioDTO body) {
        Long userId = com.ohchurus.budget.util.SecurityUtils.getAuthenticatedUserId();
        return ResponseEntity.ok(householdService.getByUser(userId));
    }
}

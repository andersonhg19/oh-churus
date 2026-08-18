package com.ohchurus.budget.controller;

import com.ohchurus.budget.dto.input.ImportarDTO;
import com.ohchurus.budget.dto.output.ResultDTO;
import com.ohchurus.budget.service.impl.ImportacionServiceImpl;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Importar el extracto del banco.
 *
 * DOS PASOS, y el primero no escribe nada: `preview` ensena que va a pasar con
 * las sesenta filas, `confirm` hace solo lo que el usuario acepto. Un
 * importador que escribe primero y deja arreglar el desastre despues es peor
 * que no tener importador.
 */
@RestController
@RequestMapping("/v1/import")
public class ImportacionController {

    private final ImportacionServiceImpl importacionService;

    public ImportacionController(ImportacionServiceImpl importacionService) {
        this.importacionService = importacionService;
    }

    @PostMapping(value = "/preview", produces = "application/json")
    public ResponseEntity<ResultDTO> preview(@Valid @RequestBody ImportarDTO dto) {
        return ResponseEntity.ok(importacionService.preview(dto));
    }

    @PostMapping(value = "/confirm", produces = "application/json")
    public ResponseEntity<ResultDTO> confirm(@Valid @RequestBody ImportarDTO dto) {
        return ResponseEntity.ok(importacionService.confirm(dto));
    }

    /** Los perfiles de banco guardados. Los de quien pide, que lo dice el token. */
    @PostMapping(value = "/profiles", produces = "application/json")
    public ResponseEntity<ResultDTO> profiles(@RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(importacionService.profiles());
    }
}

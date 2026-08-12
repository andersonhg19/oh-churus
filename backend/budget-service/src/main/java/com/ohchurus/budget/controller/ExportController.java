package com.ohchurus.budget.controller;

import com.ohchurus.budget.dto.input.DashboardRequestDTO;
import com.ohchurus.budget.dto.output.ResultDTO;
import com.ohchurus.budget.service.impl.ExcelExportService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/v1/export")
public class ExportController {

    /*
     * De aqui en adelante, el userId sale del token.
     *
     * Antes llegaba en el cuerpo de la peticion, o sea que lo decidia el
     * cliente: bastaba con cambiar un numero para pedir el panel, los
     * movimientos o el Excel de otra persona. Se ignora lo que venga en el
     * cuerpo —el frontend puede seguir mandandolo— y manda el token.
     */

    private final ExcelExportService excelExportService;

    public ExportController(ExcelExportService excelExportService) {
        this.excelExportService = excelExportService;
    }

    @PostMapping(value = "/excel", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<Object> exportExcel(@Valid @RequestBody DashboardRequestDTO request) {
        Long userId = com.ohchurus.budget.util.SecurityUtils.getAuthenticatedUserId();
        try {
            byte[] excelBytes = excelExportService.exportPeriod(
                    userId, request.getBudgetStartDay(), request.getReferenceDate());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            headers.setContentDispositionFormData("attachment", "Presupuesto.xlsx");
            headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");

            return ResponseEntity.ok().headers(headers).body(excelBytes);
        } catch (Exception e) {
            /* Antes: internalServerError().build(), un 500 con el cuerpo VACIO.
               Quien pulsaba "Descargar Excel" no recibia ni un byte ni una
               explicacion. Ahora sale el mismo ResultDTO que el resto de la
               API, en JSON, para que se pueda contar que ha pasado. */
            log.error("Error exportando el Excel del usuario {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ResultDTO(false,
                            "No se pudo generar el Excel del periodo. Intentalo de nuevo.", 500));
        }
    }
}

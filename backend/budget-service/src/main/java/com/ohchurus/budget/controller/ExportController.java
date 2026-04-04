package com.ohchurus.budget.controller;

import com.ohchurus.budget.dto.input.DashboardRequestDTO;
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

    private final ExcelExportService excelExportService;

    public ExportController(ExcelExportService excelExportService) {
        this.excelExportService = excelExportService;
    }

    @PostMapping(value = "/excel", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<byte[]> exportExcel(@Valid @RequestBody DashboardRequestDTO request) {
        try {
            byte[] excelBytes = excelExportService.exportPeriod(
                    request.getUserId(), request.getBudgetStartDay(), request.getReferenceDate());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            headers.setContentDispositionFormData("attachment", "Presupuesto.xlsx");
            headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");

            return ResponseEntity.ok().headers(headers).body(excelBytes);
        } catch (Exception e) {
            log.error("Error exporting Excel for user {}: {}", request.getUserId(), e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}

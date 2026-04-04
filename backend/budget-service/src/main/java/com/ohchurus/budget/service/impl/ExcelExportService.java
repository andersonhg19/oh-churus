package com.ohchurus.budget.service.impl;

import com.ohchurus.budget.entity.Category;
import com.ohchurus.budget.entity.Movement;
import com.ohchurus.budget.enums.CategoryType;
import com.ohchurus.budget.repository.CategoryRepository;
import com.ohchurus.budget.repository.HouseholdMemberRepository;
import com.ohchurus.budget.repository.MovementRepository;
import com.ohchurus.budget.util.PeriodUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class ExcelExportService {

    private final MovementRepository movementRepository;
    private final CategoryRepository categoryRepository;
    private final HouseholdMemberRepository householdMemberRepository;

    public ExcelExportService(MovementRepository movementRepository, CategoryRepository categoryRepository,
                               HouseholdMemberRepository householdMemberRepository) {
        this.movementRepository = movementRepository;
        this.categoryRepository = categoryRepository;
        this.householdMemberRepository = householdMemberRepository;
    }

    public byte[] exportPeriod(Long userId, int budgetStartDay, LocalDate referenceDate) throws IOException {
        LocalDate ref = referenceDate != null ? referenceDate : LocalDate.now();
        LocalDate periodStart = PeriodUtils.getStartOfPeriod(budgetStartDay, ref);
        LocalDate periodEnd = PeriodUtils.getEndOfPeriod(budgetStartDay, periodStart);

        // Get all movements for the period
        List<Movement> confirmed = movementRepository
                .findByUserIdAndDateBetweenAndConfirmedTrueAndActiveTrue(userId, periodStart, periodEnd);
        List<Movement> pending = movementRepository
                .findByUserIdAndDateBetweenAndConfirmedFalseAndActiveTrue(userId, periodStart, periodEnd);

        Map<Long, Category> catCache = new ConcurrentHashMap<>();

        @SuppressWarnings("resource")
        XSSFWorkbook wb = new XSSFWorkbook(); // closed in finally block below

        // ===== COLORS =====
        byte[] BLACK = new byte[]{0x1A, 0x1A, 0x1A};
        byte[] HEADER_BG = new byte[]{0x33, 0x33, 0x33};
        byte[] HEADER2_BG = new byte[]{0x42, 0x42, 0x42};
        byte[] SUMMARY_BG = new byte[]{0x2D, 0x2D, 0x2D};
        byte[] WHITE = new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
        byte[] GREEN = new byte[]{0x66, (byte) 0xBB, 0x6A};
        byte[] RED = new byte[]{(byte) 0xEF, 0x53, 0x50};
        byte[] ORANGE = new byte[]{(byte) 0xFF, (byte) 0xA7, 0x26};
        byte[] PURPLE = new byte[]{(byte) 0xCE, (byte) 0x93, (byte) 0xD8};
        byte[] BLUE = new byte[]{0x4F, (byte) 0xC3, (byte) 0xF7};
        byte[] YELLOW = new byte[]{(byte) 0xFF, (byte) 0xCA, 0x28};

        // ===== STYLES =====
        XSSFFont whiteFont = wb.createFont();
        whiteFont.setColor(new XSSFColor(WHITE, null));
        whiteFont.setFontName("Aptos Narrow");
        whiteFont.setFontHeightInPoints((short) 11);

        XSSFFont headerFont = wb.createFont();
        headerFont.setColor(new XSSFColor(WHITE, null));
        headerFont.setFontName("Aptos Narrow");
        headerFont.setFontHeightInPoints((short) 12);
        headerFont.setBold(true);

        XSSFFont titleFont = wb.createFont();
        titleFont.setColor(new XSSFColor(WHITE, null));
        titleFont.setFontName("Aptos Narrow");
        titleFont.setFontHeightInPoints((short) 14);
        titleFont.setBold(true);

        // Data cell style (dark bg, white text)
        XSSFCellStyle dataStyle = wb.createCellStyle();
        dataStyle.setFillForegroundColor(new XSSFColor(BLACK, null));
        dataStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        dataStyle.setFont(whiteFont);

        // Money style
        XSSFCellStyle moneyStyle = wb.createCellStyle();
        moneyStyle.cloneStyleFrom(dataStyle);
        moneyStyle.setDataFormat(wb.createDataFormat().getFormat("$#,##0"));
        moneyStyle.setAlignment(HorizontalAlignment.RIGHT);

        // Header style
        XSSFCellStyle headerStyle = wb.createCellStyle();
        headerStyle.setFillForegroundColor(new XSSFColor(HEADER2_BG, null));
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        headerStyle.setFont(headerFont);
        headerStyle.setAlignment(HorizontalAlignment.CENTER);
        headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        headerStyle.setBorderBottom(BorderStyle.THIN);

        // Title style
        XSSFCellStyle titleStyle = wb.createCellStyle();
        titleStyle.setFillForegroundColor(new XSSFColor(HEADER_BG, null));
        titleStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        titleStyle.setFont(titleFont);
        titleStyle.setAlignment(HorizontalAlignment.CENTER);
        titleStyle.setVerticalAlignment(VerticalAlignment.CENTER);

        // Date style
        XSSFCellStyle dateStyle = wb.createCellStyle();
        dateStyle.cloneStyleFrom(dataStyle);
        dateStyle.setDataFormat(wb.createDataFormat().getFormat("DD/MM/YYYY"));
        dateStyle.setAlignment(HorizontalAlignment.CENTER);

        // Center data
        XSSFCellStyle centerStyle = wb.createCellStyle();
        centerStyle.cloneStyleFrom(dataStyle);
        centerStyle.setAlignment(HorizontalAlignment.CENTER);

        // Summary styles
        XSSFCellStyle[] summaryLabelStyles = new XSSFCellStyle[6];
        XSSFCellStyle[] summaryValueStyles = new XSSFCellStyle[6];
        byte[][] summaryColors = {GREEN, ORANGE, PURPLE, RED, BLUE, YELLOW};
        String[] summaryLabels = {"TOTAL INGRESOS", "PRESUPUESTO GASTOS", "PENDIENTE POR PAGAR", "TOTAL GASTOS", "BALANCE", "DISPONIBLE (TRAS PRESUP.)"};

        for (int i = 0; i < 6; i++) {
            XSSFFont f = wb.createFont();
            f.setColor(new XSSFColor(summaryColors[i], null));
            f.setFontName("Aptos Narrow");
            f.setFontHeightInPoints((short) (i == 4 ? 14 : 12));
            f.setBold(true);

            summaryLabelStyles[i] = wb.createCellStyle();
            summaryLabelStyles[i].setFillForegroundColor(new XSSFColor(SUMMARY_BG, null));
            summaryLabelStyles[i].setFillPattern(FillPatternType.SOLID_FOREGROUND);
            summaryLabelStyles[i].setFont(f);

            summaryValueStyles[i] = wb.createCellStyle();
            summaryValueStyles[i].setFillForegroundColor(new XSSFColor(SUMMARY_BG, null));
            summaryValueStyles[i].setFillPattern(FillPatternType.SOLID_FOREGROUND);
            summaryValueStyles[i].setFont(f);
            summaryValueStyles[i].setDataFormat(wb.createDataFormat().getFormat("$#,##0"));
            summaryValueStyles[i].setAlignment(HorizontalAlignment.RIGHT);
        }

        // ===== SHEET: Presupuesto =====
        XSSFSheet ws = wb.createSheet("Presupuesto");

        // Column widths
        ws.setColumnWidth(0, 6 * 256);   // A: ID
        ws.setColumnWidth(1, 14 * 256);  // B: TIPO
        ws.setColumnWidth(2, 18 * 256);  // C: CATEGORÍA
        ws.setColumnWidth(3, 40 * 256);  // D: DESCRIPCIÓN
        ws.setColumnWidth(4, 18 * 256);  // E: MONTO
        ws.setColumnWidth(5, 18 * 256);  // F: ESTADO
        ws.setColumnWidth(6, 15 * 256);  // G: FECHA
        ws.setColumnWidth(7, 4 * 256);   // H: spacer
        ws.setColumnWidth(8, 32 * 256);  // I: Summary labels
        ws.setColumnWidth(9, 22 * 256);  // J: Summary values

        // Row 1: Title
        XSSFRow row1 = ws.createRow(0);
        row1.setHeightInPoints(30);
        ws.addMergedRegion(new CellRangeAddress(0, 0, 0, 6));
        XSSFCell titleCell = row1.createCell(0);
        titleCell.setCellValue("PRESUPUESTO - " + periodStart + " a " + periodEnd);
        titleCell.setCellStyle(titleStyle);
        for (int c = 1; c <= 9; c++) {
            XSSFCell cell = row1.createCell(c);
            cell.setCellStyle(c <= 6 ? titleStyle : dataStyle);
        }

        // Row 2: Headers
        XSSFRow row2 = ws.createRow(1);
        row2.setHeightInPoints(25);
        String[] headers = {"ID", "TIPO", "CATEGORÍA", "DESCRIPCIÓN", "MONTO", "ESTADO", "FECHA"};
        for (int c = 0; c < headers.length; c++) {
            XSSFCell cell = row2.createCell(c);
            cell.setCellValue(headers[c]);
            cell.setCellStyle(headerStyle);
        }

        // Summary header
        ws.addMergedRegion(new CellRangeAddress(1, 1, 8, 9));
        XSSFCell sumHeader = row2.createCell(8);
        sumHeader.setCellValue("RESUMEN");
        sumHeader.setCellStyle(headerStyle);
        row2.createCell(9).setCellStyle(headerStyle);
        row2.createCell(7).setCellStyle(dataStyle);

        // Data rows - first confirmed, then pending
        int dataStartRow = 2;
        int rowIdx = dataStartRow;
        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        // All movements: confirmed first, then pending
        java.util.List<Movement> allMovements = new java.util.ArrayList<>();
        allMovements.addAll(confirmed);
        allMovements.addAll(pending);

        for (Movement m : allMovements) {
            XSSFRow row = ws.createRow(rowIdx);
            Category cat = catCache.computeIfAbsent(m.getCategoryId(),
                    id -> categoryRepository.findByIdAndActiveTrue(id).orElse(null));
            String tipo = cat != null && cat.getType() == CategoryType.INCOME ? "Ingreso" : "Egreso";
            String catName = cat != null ? cat.getName() : "";
            String estado = Boolean.TRUE.equals(m.getConfirmed()) ? "Ejecutado" : "Presupuestado";

            XSSFCell cId = row.createCell(0);
            cId.setCellValue(rowIdx - 1);
            cId.setCellStyle(centerStyle);

            XSSFCell cTipo = row.createCell(1);
            cTipo.setCellValue(tipo);
            cTipo.setCellStyle(centerStyle);

            XSSFCell cCat = row.createCell(2);
            cCat.setCellValue(catName);
            cCat.setCellStyle(centerStyle);

            XSSFCell cDesc = row.createCell(3);
            cDesc.setCellValue(m.getDescription() != null ? m.getDescription() : "");
            cDesc.setCellStyle(dataStyle);

            XSSFCell cMonto = row.createCell(4);
            cMonto.setCellValue(m.getAmount() != null ? m.getAmount().doubleValue() : 0);
            cMonto.setCellStyle(moneyStyle);

            XSSFCell cEstado = row.createCell(5);
            cEstado.setCellValue(estado);
            cEstado.setCellStyle(centerStyle);

            XSSFCell cFecha = row.createCell(6);
            cFecha.setCellValue(m.getDate() != null ? m.getDate().format(dateFmt) : "");
            cFecha.setCellStyle(centerStyle);

            // Spacer + summary bg
            row.createCell(7).setCellStyle(dataStyle);
            row.createCell(8).setCellStyle(dataStyle);
            row.createCell(9).setCellStyle(dataStyle);

            rowIdx++;
        }

        int dataEndRow = rowIdx; // exclusive

        // Fill remaining rows with dark bg
        for (int r = rowIdx; r < rowIdx + 5; r++) {
            XSSFRow row = ws.createRow(r);
            for (int c = 0; c <= 9; c++) {
                row.createCell(c).setCellStyle(dataStyle);
            }
        }

        // ===== SUMMARY FORMULAS (rows 3-8 in I:J) =====
        String rangeE = "E" + (dataStartRow + 1) + ":E" + dataEndRow;
        String rangeB = "B" + (dataStartRow + 1) + ":B" + dataEndRow;
        String rangeF = "F" + (dataStartRow + 1) + ":F" + dataEndRow;

        // Row 3: TOTAL INGRESOS
        XSSFRow sr0 = ws.getRow(2);
        sr0.createCell(8).setCellStyle(summaryLabelStyles[0]);
        sr0.getCell(8).setCellValue(summaryLabels[0]);
        sr0.createCell(9).setCellStyle(summaryValueStyles[0]);
        sr0.getCell(9).setCellFormula("SUMIFS(" + rangeE + "," + rangeB + ",\"Ingreso\"," + rangeF + ",\"Ejecutado\")");

        // Row 4: PRESUPUESTO GASTOS (todos egresos)
        XSSFRow sr1 = ws.getRow(3);
        sr1.getCell(8).setCellStyle(summaryLabelStyles[1]);
        sr1.getCell(8).setCellValue(summaryLabels[1]);
        sr1.getCell(9).setCellStyle(summaryValueStyles[1]);
        sr1.getCell(9).setCellFormula("SUMIFS(" + rangeE + "," + rangeB + ",\"Egreso\")");

        // Row 5: PENDIENTE POR PAGAR
        XSSFRow sr2 = ws.getRow(4);
        sr2.getCell(8).setCellStyle(summaryLabelStyles[2]);
        sr2.getCell(8).setCellValue(summaryLabels[2]);
        sr2.getCell(9).setCellStyle(summaryValueStyles[2]);
        sr2.getCell(9).setCellFormula("SUMIFS(" + rangeE + "," + rangeB + ",\"Egreso\"," + rangeF + ",\"Presupuestado\")");

        // Row 6: TOTAL GASTOS (ejecutados)
        XSSFRow sr3 = ws.getRow(5);
        sr3.getCell(8).setCellStyle(summaryLabelStyles[3]);
        sr3.getCell(8).setCellValue(summaryLabels[3]);
        sr3.getCell(9).setCellStyle(summaryValueStyles[3]);
        sr3.getCell(9).setCellFormula("SUMIFS(" + rangeE + "," + rangeB + ",\"Egreso\"," + rangeF + ",\"Ejecutado\")");

        // Row 7: separator - already styled

        // Row 8: BALANCE
        int balRow = dataStartRow > 6 ? 6 : dataEndRow;
        XSSFRow sr4 = ws.getRow(6) != null ? ws.getRow(6) : ws.createRow(6);
        sr4.createCell(8).setCellStyle(summaryLabelStyles[4]);
        sr4.getCell(8).setCellValue(summaryLabels[4]);
        sr4.createCell(9).setCellStyle(summaryValueStyles[4]);
        sr4.getCell(9).setCellFormula("J3-J6");

        // Row 9: DISPONIBLE
        XSSFRow sr5 = ws.getRow(7) != null ? ws.getRow(7) : ws.createRow(7);
        sr5.createCell(8).setCellStyle(summaryLabelStyles[5]);
        sr5.getCell(8).setCellValue(summaryLabels[5]);
        sr5.createCell(9).setCellStyle(summaryValueStyles[5]);
        sr5.getCell(9).setCellFormula("J7-J5");

        // Auto filter
        ws.setAutoFilter(new CellRangeAddress(1, 1, 0, 6));

        // Freeze panes
        ws.createFreezePane(0, 2);

        // ===== SHEET: Configuración =====
        XSSFSheet configSheet = wb.createSheet("Configuración");
        XSSFCellStyle configHeaderStyle = wb.createCellStyle();
        configHeaderStyle.cloneStyleFrom(headerStyle);
        XSSFCellStyle configDataStyle = wb.createCellStyle();
        configDataStyle.cloneStyleFrom(dataStyle);

        String[][] configData = {
                {"CAT. EGRESO", "CAT. INGRESO"},
        };
        XSSFRow configR0 = configSheet.createRow(0);
        configR0.createCell(0).setCellValue("CAT. EGRESO");
        configR0.getCell(0).setCellStyle(configHeaderStyle);
        configR0.createCell(2).setCellValue("CAT. INGRESO");
        configR0.getCell(2).setCellStyle(configHeaderStyle);

        // Get distinct categories for user
        // Include personal + household categories
        List<Long> householdIds = householdMemberRepository.findByUserIdAndActiveTrue(userId).stream()
                .map(m -> m.getHouseholdId()).collect(java.util.stream.Collectors.toList());
        List<Category> userCats;
        if (!householdIds.isEmpty()) {
            userCats = categoryRepository.findPersonalAndHousehold(userId, householdIds);
        } else {
            userCats = categoryRepository.findByUserIdAndActiveTrue(userId);
        }
        int egressIdx = 1, incomeIdx = 1;
        for (Category c : userCats) {
            if (c.getType() == CategoryType.EXPENSE) {
                XSSFRow r = configSheet.getRow(egressIdx) != null ? configSheet.getRow(egressIdx) : configSheet.createRow(egressIdx);
                r.createCell(0).setCellValue(c.getName());
                r.getCell(0).setCellStyle(configDataStyle);
                egressIdx++;
            } else {
                XSSFRow r = configSheet.getRow(incomeIdx) != null ? configSheet.getRow(incomeIdx) : configSheet.createRow(incomeIdx);
                r.createCell(2).setCellValue(c.getName());
                r.getCell(2).setCellStyle(configDataStyle);
                incomeIdx++;
            }
        }

        configSheet.setColumnWidth(0, 20 * 256);
        configSheet.setColumnWidth(2, 20 * 256);

        // Dark bg for config sheet
        for (int r = 0; r <= Math.max(egressIdx, incomeIdx) + 2; r++) {
            XSSFRow row = configSheet.getRow(r) != null ? configSheet.getRow(r) : configSheet.createRow(r);
            for (int c = 0; c <= 3; c++) {
                XSSFCell cell = row.getCell(c) != null ? row.getCell(c) : row.createCell(c);
                if (cell.getCellStyle() == null || cell.getCellStyle().getIndex() == 0) {
                    cell.setCellStyle(configDataStyle);
                }
            }
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            wb.write(out);
        } finally {
            wb.close();
        }

        return out.toByteArray();
    }
}

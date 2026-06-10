package com.ohchurus.budget.service;

import com.ohchurus.budget.entity.Category;
import com.ohchurus.budget.entity.HouseholdMember;
import com.ohchurus.budget.entity.Movement;
import com.ohchurus.budget.enums.CategoryType;
import com.ohchurus.budget.repository.CategoryRepository;
import com.ohchurus.budget.repository.HouseholdMemberRepository;
import com.ohchurus.budget.repository.MovementRepository;
import com.ohchurus.budget.service.impl.ExcelExportService;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExcelExportService")
class ExcelExportServiceTest {

    @Mock
    private MovementRepository movementRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private HouseholdMemberRepository householdMemberRepository;

    @InjectMocks
    private ExcelExportService service;

    private static final Long USER_ID = 1L;
    private static final LocalDate REF = LocalDate.of(2026, 3, 15);

    private Category expense() {
        return Category.builder().id(20L).userId(USER_ID).name("Arriendo")
                .type(CategoryType.EXPENSE).active(true).build();
    }

    private Category income() {
        return Category.builder().id(10L).userId(USER_ID).name("Salario")
                .type(CategoryType.INCOME).active(true).build();
    }

    private Workbook parse(byte[] bytes) throws Exception {
        return new XSSFWorkbook(new ByteArrayInputStream(bytes));
    }

    @Test
    @DisplayName("Should build workbook with both sheets and movement rows (personal user)")
    void shouldExportPersonal() throws Exception {
        Movement confirmedExpense = Movement.builder().id(1L).userId(USER_ID).categoryId(20L)
                .date(REF).amount(new BigDecimal("1500000")).description("Marzo")
                .confirmed(true).active(true).build();
        Movement confirmedIncome = Movement.builder().id(2L).userId(USER_ID).categoryId(10L)
                .date(REF).amount(new BigDecimal("3500000")).description("Sueldo")
                .confirmed(true).active(true).build();
        Movement pending = Movement.builder().id(3L).userId(USER_ID).categoryId(20L)
                .date(REF).amount(new BigDecimal("250000")).description("Servicios")
                .confirmed(false).active(true).build();

        when(movementRepository.findByUserIdAndDateBetweenAndConfirmedTrueAndActiveTrue(eq(USER_ID), any(), any()))
                .thenReturn(List.of(confirmedExpense, confirmedIncome));
        when(movementRepository.findByUserIdAndDateBetweenAndConfirmedFalseAndActiveTrue(eq(USER_ID), any(), any()))
                .thenReturn(List.of(pending));
        when(categoryRepository.findByIdAndActiveTrue(20L)).thenReturn(Optional.of(expense()));
        when(categoryRepository.findByIdAndActiveTrue(10L)).thenReturn(Optional.of(income()));
        when(householdMemberRepository.findByUserIdAndActiveTrue(USER_ID)).thenReturn(Collections.emptyList());
        when(categoryRepository.findByUserIdAndActiveTrue(USER_ID)).thenReturn(List.of(expense(), income()));

        byte[] bytes = service.exportPeriod(USER_ID, 1, REF);

        assertNotNull(bytes);
        assertTrue(bytes.length > 0);
        try (Workbook wb = parse(bytes)) {
            assertNotNull(wb.getSheet("Presupuesto"));
            assertNotNull(wb.getSheet("Configuración"));
            Sheet ws = wb.getSheet("Presupuesto");
            // title row
            assertTrue(ws.getRow(0).getCell(0).getStringCellValue().startsWith("PRESUPUESTO"));
            // header row
            assertEquals("ID", ws.getRow(1).getCell(0).getStringCellValue());
            // first data row: confirmed expense
            assertEquals("Arriendo", ws.getRow(2).getCell(2).getStringCellValue());
            assertEquals(1500000d, ws.getRow(2).getCell(4).getNumericCellValue());
        }
    }

    @Test
    @DisplayName("Should default reference date to today when null")
    void shouldDefaultReferenceDate() throws Exception {
        when(movementRepository.findByUserIdAndDateBetweenAndConfirmedTrueAndActiveTrue(eq(USER_ID), any(), any()))
                .thenReturn(Collections.emptyList());
        when(movementRepository.findByUserIdAndDateBetweenAndConfirmedFalseAndActiveTrue(eq(USER_ID), any(), any()))
                .thenReturn(Collections.emptyList());
        when(householdMemberRepository.findByUserIdAndActiveTrue(USER_ID)).thenReturn(Collections.emptyList());
        when(categoryRepository.findByUserIdAndActiveTrue(USER_ID)).thenReturn(Collections.emptyList());

        byte[] bytes = service.exportPeriod(USER_ID, 1, null);

        assertNotNull(bytes);
        assertTrue(bytes.length > 0);
    }

    @Test
    @DisplayName("Should handle null description / amount / date / missing category gracefully")
    void shouldHandleNullFields() throws Exception {
        Movement messy = Movement.builder().id(1L).userId(USER_ID).categoryId(99L)
                .date(null).amount(null).description(null)
                .confirmed(true).active(true).build();

        when(movementRepository.findByUserIdAndDateBetweenAndConfirmedTrueAndActiveTrue(eq(USER_ID), any(), any()))
                .thenReturn(List.of(messy));
        when(movementRepository.findByUserIdAndDateBetweenAndConfirmedFalseAndActiveTrue(eq(USER_ID), any(), any()))
                .thenReturn(Collections.emptyList());
        when(categoryRepository.findByIdAndActiveTrue(99L)).thenReturn(Optional.empty());
        when(householdMemberRepository.findByUserIdAndActiveTrue(USER_ID)).thenReturn(Collections.emptyList());
        when(categoryRepository.findByUserIdAndActiveTrue(USER_ID)).thenReturn(Collections.emptyList());

        byte[] bytes = service.exportPeriod(USER_ID, 1, REF);

        try (Workbook wb = parse(bytes)) {
            Sheet ws = wb.getSheet("Presupuesto");
            // empty category name and description, amount 0
            assertEquals("", ws.getRow(2).getCell(2).getStringCellValue());
            assertEquals("", ws.getRow(2).getCell(3).getStringCellValue());
            assertEquals(0d, ws.getRow(2).getCell(4).getNumericCellValue());
        }
    }

    @Test
    @DisplayName("Should use household categories when the user belongs to a household")
    void shouldUseHouseholdCategories() throws Exception {
        HouseholdMember membership = HouseholdMember.builder()
                .id(1L).householdId(100L).userId(USER_ID).role("OWNER").active(true).build();

        when(movementRepository.findByUserIdAndDateBetweenAndConfirmedTrueAndActiveTrue(eq(USER_ID), any(), any()))
                .thenReturn(Collections.emptyList());
        when(movementRepository.findByUserIdAndDateBetweenAndConfirmedFalseAndActiveTrue(eq(USER_ID), any(), any()))
                .thenReturn(Collections.emptyList());
        when(householdMemberRepository.findByUserIdAndActiveTrue(USER_ID)).thenReturn(List.of(membership));
        when(categoryRepository.findPersonalAndHousehold(eq(USER_ID), anyList()))
                .thenReturn(List.of(expense(), income()));

        byte[] bytes = service.exportPeriod(USER_ID, 1, REF);

        assertNotNull(bytes);
        try (Workbook wb = parse(bytes)) {
            Sheet config = wb.getSheet("Configuración");
            assertEquals("CAT. EGRESO", config.getRow(0).getCell(0).getStringCellValue());
            assertEquals("Arriendo", config.getRow(1).getCell(0).getStringCellValue());
            assertEquals("Salario", config.getRow(1).getCell(2).getStringCellValue());
        }
    }
}

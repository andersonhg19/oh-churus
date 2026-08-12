package com.ohchurus.budget.dto;

import com.ohchurus.budget.dto.input.*;
import com.ohchurus.budget.dto.output.*;
import com.ohchurus.budget.entity.*;
import com.ohchurus.budget.enums.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DTOTest {

    @Test
    @DisplayName("ResultDTO constructors and all getters/setters")
    void testResultDTO() {
        ResultDTO ok = new ResultDTO("data");
        assertTrue(ok.isCorrect());
        assertEquals("OK", ok.getMessage());
        assertEquals(0, ok.getErrorCode());
        assertEquals("data", ok.getObject());

        ResultDTO err = new ResultDTO(false, "fail", 100);
        assertFalse(err.isCorrect());
        assertEquals("fail", err.getMessage());
        assertEquals(100, err.getErrorCode());
        assertNull(err.getObject());

        // Test setters
        ResultDTO dto = new ResultDTO();
        dto.setCorrect(true);
        dto.setMessage("msg");
        dto.setErrorCode(5);
        dto.setObject("obj");
        assertTrue(dto.isCorrect());
        assertEquals("msg", dto.getMessage());
        assertEquals(5, dto.getErrorCode());
        assertEquals("obj", dto.getObject());
    }

    @Test
    @DisplayName("DashboardRequestDTO with referenceDate")
    void testDashboardRequest() {
        DashboardRequestDTO dto = new DashboardRequestDTO();
        dto.setUserId(1L);
        dto.setBudgetStartDay(15);
        dto.setReferenceDate(LocalDate.of(2026, 3, 15));
        assertEquals(1L, dto.getUserId());
        assertEquals(15, dto.getBudgetStartDay());
        assertEquals(LocalDate.of(2026, 3, 15), dto.getReferenceDate());

        // Default budgetStartDay is 1
        DashboardRequestDTO dto2 = new DashboardRequestDTO();
        assertEquals(1, dto2.getBudgetStartDay());
        assertNull(dto2.getReferenceDate());
    }

    @Test
    @DisplayName("PeriodRequestDTO all args and setters")
    void testPeriodRequest() {
        PeriodRequestDTO dto = new PeriodRequestDTO(1L, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));
        assertEquals(1L, dto.getUserId());
        assertEquals(LocalDate.of(2026, 3, 1), dto.getStartDate());
        assertEquals(LocalDate.of(2026, 3, 31), dto.getEndDate());

        PeriodRequestDTO dto2 = new PeriodRequestDTO();
        dto2.setUserId(2L);
        dto2.setStartDate(LocalDate.of(2026, 1, 1));
        dto2.setEndDate(LocalDate.of(2026, 12, 31));
        assertEquals(2L, dto2.getUserId());
    }

    @Test
    @DisplayName("GeneratePendingRequestDTO all args and setters")
    void testGeneratePending() {
        GeneratePendingRequestDTO dto = new GeneratePendingRequestDTO(1L, 15);
        assertEquals(1L, dto.getUserId());
        assertEquals(15, dto.getBudgetStartDay());

        GeneratePendingRequestDTO dto2 = new GeneratePendingRequestDTO();
        dto2.setUserId(2L);
        dto2.setBudgetStartDay(20);
        assertEquals(2L, dto2.getUserId());
    }

    @Test
    @DisplayName("CategorySaveDTO all fields via constructor and setters, including householdId")
    void testCategorySave() {
        CategorySaveDTO dto = new CategorySaveDTO(1L, 2L, "Cat", "Desc", 3L, "icon", "#FFF", CategoryType.INCOME, 100L);
        assertEquals(1L, dto.getId());
        assertEquals(2L, dto.getUserId());
        assertEquals("Cat", dto.getName());
        assertEquals("Desc", dto.getDescription());
        assertEquals(3L, dto.getParentId());
        assertEquals("icon", dto.getIcon());
        assertEquals("#FFF", dto.getColor());
        assertEquals(CategoryType.INCOME, dto.getType());
        assertEquals(100L, dto.getHouseholdId());

        CategorySaveDTO dto2 = new CategorySaveDTO();
        dto2.setId(10L);
        dto2.setUserId(20L);
        dto2.setName("Name");
        dto2.setDescription("D");
        dto2.setParentId(5L);
        dto2.setIcon("i");
        dto2.setColor("#000");
        dto2.setType(CategoryType.EXPENSE);
        dto2.setHouseholdId(200L);
        assertEquals(10L, dto2.getId());
        assertEquals(200L, dto2.getHouseholdId());
    }

    @Test
    @DisplayName("MovementSaveDTO all fields via setters and AllArgsConstructor, including parentMovementId")
    void testMovementSave() {
        LocalDate now = LocalDate.now();
        MovementSaveDTO dto = new MovementSaveDTO(1L, 2L, 3L, now, new BigDecimal("1000"), "desc", 4L, 5L, true);
        assertEquals(1L, dto.getId());
        assertEquals(2L, dto.getUserId());
        assertEquals(3L, dto.getCategoryId());
        assertEquals(now, dto.getDate());
        assertEquals(new BigDecimal("1000"), dto.getAmount());
        assertEquals("desc", dto.getDescription());
        assertEquals(4L, dto.getScheduledMovementId());
        assertEquals(5L, dto.getParentMovementId());
        assertTrue(dto.getConfirmed());

        MovementSaveDTO dto2 = new MovementSaveDTO();
        dto2.setId(10L);
        dto2.setUserId(20L);
        dto2.setCategoryId(30L);
        dto2.setDate(now);
        dto2.setAmount(new BigDecimal("500"));
        dto2.setDescription("test");
        dto2.setScheduledMovementId(40L);
        dto2.setParentMovementId(50L);
        dto2.setConfirmed(false);
        assertEquals(10L, dto2.getId());
        assertEquals(50L, dto2.getParentMovementId());
        assertFalse(dto2.getConfirmed());

        // Default confirmed is true
        MovementSaveDTO dto3 = new MovementSaveDTO();
        assertTrue(dto3.getConfirmed());
    }

    @Test
    @DisplayName("ScheduledMovementSaveDTO all fields via setters and AllArgsConstructor")
    void testScheduledSave() {
        LocalDate now = LocalDate.now();
        ScheduledMovementSaveDTO dto = new ScheduledMovementSaveDTO(1L, 2L, 3L, "Rent",
                new BigDecimal("500"), Frequency.MONTHLY, 12, now, 15);
        assertEquals(1L, dto.getId());
        assertEquals(2L, dto.getUserId());
        assertEquals(3L, dto.getCategoryId());
        assertEquals("Rent", dto.getName());
        assertEquals(new BigDecimal("500"), dto.getAmount());
        assertEquals(Frequency.MONTHLY, dto.getFrequency());
        assertEquals(12, dto.getDurationMonths());
        assertEquals(now, dto.getStartDate());
        assertEquals(15, dto.getDayOfMonth());

        ScheduledMovementSaveDTO dto2 = new ScheduledMovementSaveDTO();
        dto2.setId(10L);
        dto2.setUserId(20L);
        dto2.setCategoryId(30L);
        dto2.setName("Test");
        dto2.setAmount(new BigDecimal("100"));
        dto2.setFrequency(Frequency.WEEKLY);
        dto2.setDurationMonths(6);
        dto2.setStartDate(now);
        dto2.setDayOfMonth(1);
        assertEquals(10L, dto2.getId());
    }

    @Test
    @DisplayName("MovementFilterDTO all fields via AllArgsConstructor and setters")
    void testMovementFilter() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 12, 31);
        MovementFilterDTO dto = new MovementFilterDTO(1L, 2L, start, end, true, 1, 20);
        assertEquals(1L, dto.getUserId());
        assertEquals(2L, dto.getCategoryId());
        assertEquals(start, dto.getStartDate());
        assertEquals(end, dto.getEndDate());
        assertTrue(dto.getConfirmed());
        assertEquals(1, dto.getPage());
        assertEquals(20, dto.getSize());

        MovementFilterDTO dto2 = new MovementFilterDTO();
        dto2.setUserId(10L);
        dto2.setCategoryId(20L);
        dto2.setStartDate(start);
        dto2.setEndDate(end);
        dto2.setConfirmed(false);
        dto2.setPage(0);
        dto2.setSize(50);
        assertEquals(10L, dto2.getUserId());
    }

    @Test
    @DisplayName("ScheduledMovementFilterDTO all fields via AllArgsConstructor and setters")
    void testScheduledFilter() {
        ScheduledMovementFilterDTO dto = new ScheduledMovementFilterDTO(1L, 2L, Frequency.WEEKLY, 0, 10);
        assertEquals(1L, dto.getUserId());
        assertEquals(2L, dto.getCategoryId());
        assertEquals(Frequency.WEEKLY, dto.getFrequency());
        assertEquals(0, dto.getPage());
        assertEquals(10, dto.getSize());

        ScheduledMovementFilterDTO dto2 = new ScheduledMovementFilterDTO();
        dto2.setUserId(10L);
        dto2.setCategoryId(20L);
        dto2.setFrequency(Frequency.ANNUAL);
        dto2.setPage(2);
        dto2.setSize(25);
        assertEquals(10L, dto2.getUserId());
    }

    @Test
    @DisplayName("CategoryFilterDTO all fields")
    void testCategoryFilter() {
        CategoryFilterDTO dto = new CategoryFilterDTO(1L, "test", CategoryType.EXPENSE, 0, 10);
        assertEquals(1L, dto.getUserId());
        assertEquals("test", dto.getName());
        assertEquals(CategoryType.EXPENSE, dto.getType());
        assertEquals(0, dto.getPage());
        assertEquals(10, dto.getSize());
    }

    @Test
    @DisplayName("DashboardSummaryDTO builder, all-args, no-args, setters and getters with budgetTotal")
    void testDashboardSummary() {
        LocalDate start = LocalDate.of(2026, 3, 1);
        LocalDate end = LocalDate.of(2026, 3, 31);
        DashboardSummaryDTO dto = DashboardSummaryDTO.builder()
                .totalIncome(new BigDecimal("1000"))
                .totalExpense(new BigDecimal("500"))
                .balance(new BigDecimal("500"))
                .budgetTotal(new BigDecimal("800"))
                .pendingCount(3)
                .pendingAmount(new BigDecimal("200"))
                .periodStart(start)
                .periodEnd(end)
                .build();
        assertEquals(new BigDecimal("1000"), dto.getTotalIncome());
        assertEquals(new BigDecimal("500"), dto.getTotalExpense());
        assertEquals(new BigDecimal("500"), dto.getBalance());
        assertEquals(new BigDecimal("800"), dto.getBudgetTotal());
        assertEquals(3, dto.getPendingCount());
        assertEquals(new BigDecimal("200"), dto.getPendingAmount());
        assertEquals(start, dto.getPeriodStart());
        assertEquals(end, dto.getPeriodEnd());

        // Test setters
        DashboardSummaryDTO dto2 = new DashboardSummaryDTO();
        dto2.setTotalIncome(new BigDecimal("2000"));
        dto2.setTotalExpense(new BigDecimal("1000"));
        dto2.setBudgetTotal(new BigDecimal("1500"));
        dto2.setPendingCount(5);
        dto2.setPendingAmount(new BigDecimal("300"));
        dto2.setBalance(new BigDecimal("700"));
        dto2.setPeriodStart(start);
        dto2.setPeriodEnd(end);
        assertEquals(new BigDecimal("2000"), dto2.getTotalIncome());

        // Test all-args
        DashboardSummaryDTO dto3 = new DashboardSummaryDTO(
                new BigDecimal("3000"), new BigDecimal("1500"), new BigDecimal("1500"),
                new BigDecimal("2000"), 2, new BigDecimal("100"), start, end);
        assertEquals(new BigDecimal("3000"), dto3.getTotalIncome());
    }

    @Test
    @DisplayName("DashboardSummaryDTO.CategorySummary with categoryType field")
    void testCategorySummary() {
        DashboardSummaryDTO.CategorySummary cs = DashboardSummaryDTO.CategorySummary.builder()
                .categoryId(1L).categoryName("Food").categoryType("EXPENSE").icon("utensils").color("#FF0000")
                .total(new BigDecimal("500")).count(5).build();
        assertEquals(1L, cs.getCategoryId());
        assertEquals("Food", cs.getCategoryName());
        assertEquals("EXPENSE", cs.getCategoryType());
        assertEquals("utensils", cs.getIcon());
        assertEquals("#FF0000", cs.getColor());
        assertEquals(new BigDecimal("500"), cs.getTotal());
        assertEquals(5, cs.getCount());

        // NoArgs + setters
        DashboardSummaryDTO.CategorySummary cs2 = new DashboardSummaryDTO.CategorySummary();
        cs2.setCategoryId(2L);
        cs2.setCategoryName("Transport");
        cs2.setCategoryType("EXPENSE");
        cs2.setIcon("car");
        cs2.setColor("#00FF00");
        cs2.setTotal(new BigDecimal("300"));
        cs2.setCount(3);
        assertEquals(2L, cs2.getCategoryId());

        // AllArgs
        DashboardSummaryDTO.CategorySummary cs3 = new DashboardSummaryDTO.CategorySummary(
                3L, "Health", "INCOME", "heart", "#0000FF", new BigDecimal("200"), 2);
        assertEquals("Health", cs3.getCategoryName());
    }

    @Test
    @DisplayName("DashboardSummaryDTO.SplitSummary all fields")
    void testSplitSummary() {
        LocalDate start = LocalDate.of(2026, 3, 1);
        LocalDate end = LocalDate.of(2026, 3, 31);

        DashboardSummaryDTO.SplitSummary ss = DashboardSummaryDTO.SplitSummary.builder()
                .personalIncome(new BigDecimal("1000"))
                .personalExpense(new BigDecimal("500"))
                .personalBalance(new BigDecimal("500"))
                .sharedIncome(new BigDecimal("2000"))
                .sharedExpense(new BigDecimal("1500"))
                .sharedBalance(new BigDecimal("500"))
                .totalIncome(new BigDecimal("3000"))
                .totalExpense(new BigDecimal("2000"))
                .totalBalance(new BigDecimal("1000"))
                .personalPendingCount(2)
                .sharedPendingCount(3)
                .periodStart(start)
                .periodEnd(end)
                .build();

        assertEquals(new BigDecimal("1000"), ss.getPersonalIncome());
        assertEquals(new BigDecimal("500"), ss.getPersonalExpense());
        assertEquals(new BigDecimal("500"), ss.getPersonalBalance());
        assertEquals(new BigDecimal("2000"), ss.getSharedIncome());
        assertEquals(new BigDecimal("1500"), ss.getSharedExpense());
        assertEquals(new BigDecimal("500"), ss.getSharedBalance());
        assertEquals(new BigDecimal("3000"), ss.getTotalIncome());
        assertEquals(new BigDecimal("2000"), ss.getTotalExpense());
        assertEquals(new BigDecimal("1000"), ss.getTotalBalance());
        assertEquals(2, ss.getPersonalPendingCount());
        assertEquals(3, ss.getSharedPendingCount());
        assertEquals(start, ss.getPeriodStart());
        assertEquals(end, ss.getPeriodEnd());

        // NoArgs + setters
        DashboardSummaryDTO.SplitSummary ss2 = new DashboardSummaryDTO.SplitSummary();
        ss2.setPersonalIncome(BigDecimal.ZERO);
        ss2.setPersonalExpense(BigDecimal.ZERO);
        ss2.setPersonalBalance(BigDecimal.ZERO);
        ss2.setSharedIncome(BigDecimal.ZERO);
        ss2.setSharedExpense(BigDecimal.ZERO);
        ss2.setSharedBalance(BigDecimal.ZERO);
        ss2.setTotalIncome(BigDecimal.ZERO);
        ss2.setTotalExpense(BigDecimal.ZERO);
        ss2.setTotalBalance(BigDecimal.ZERO);
        ss2.setPersonalPendingCount(0);
        ss2.setSharedPendingCount(0);
        ss2.setPeriodStart(start);
        ss2.setPeriodEnd(end);
        assertEquals(BigDecimal.ZERO, ss2.getPersonalIncome());

        // AllArgs
        DashboardSummaryDTO.SplitSummary ss3 = new DashboardSummaryDTO.SplitSummary(
                new BigDecimal("100"), new BigDecimal("50"), new BigDecimal("50"),
                new BigDecimal("200"), new BigDecimal("100"), new BigDecimal("100"),
                new BigDecimal("300"), new BigDecimal("150"), new BigDecimal("150"),
                1, 2, start, end);
        assertEquals(new BigDecimal("100"), ss3.getPersonalIncome());
    }

    @Test
    @DisplayName("DashboardSummaryDTO.TrendDTO with 4 income/expense fields")
    void testTrendDTO() {
        LocalDate cs = LocalDate.of(2026, 3, 1);
        LocalDate ce = LocalDate.of(2026, 3, 31);
        LocalDate ps = LocalDate.of(2026, 2, 1);
        LocalDate pe = LocalDate.of(2026, 2, 28);

        DashboardSummaryDTO.TrendDTO trend = DashboardSummaryDTO.TrendDTO.builder()
                .currentIncome(new BigDecimal("1000"))
                .currentExpense(new BigDecimal("400"))
                .previousIncome(new BigDecimal("800"))
                .previousExpense(new BigDecimal("300"))
                .changePercentage(new BigDecimal("25.0"))
                .currentPeriodStart(cs)
                .currentPeriodEnd(ce)
                .previousPeriodStart(ps)
                .previousPeriodEnd(pe)
                .build();
        assertEquals(new BigDecimal("1000"), trend.getCurrentIncome());
        assertEquals(new BigDecimal("400"), trend.getCurrentExpense());
        assertEquals(new BigDecimal("800"), trend.getPreviousIncome());
        assertEquals(new BigDecimal("300"), trend.getPreviousExpense());
        assertEquals(new BigDecimal("25.0"), trend.getChangePercentage());
        assertEquals(cs, trend.getCurrentPeriodStart());
        assertEquals(ce, trend.getCurrentPeriodEnd());
        assertEquals(ps, trend.getPreviousPeriodStart());
        assertEquals(pe, trend.getPreviousPeriodEnd());

        // NoArgs + setters
        DashboardSummaryDTO.TrendDTO t2 = new DashboardSummaryDTO.TrendDTO();
        t2.setCurrentIncome(new BigDecimal("2000"));
        t2.setCurrentExpense(new BigDecimal("1000"));
        t2.setPreviousIncome(new BigDecimal("1500"));
        t2.setPreviousExpense(new BigDecimal("700"));
        t2.setChangePercentage(new BigDecimal("33.3"));
        t2.setCurrentPeriodStart(cs);
        t2.setCurrentPeriodEnd(ce);
        t2.setPreviousPeriodStart(ps);
        t2.setPreviousPeriodEnd(pe);
        assertEquals(new BigDecimal("2000"), t2.getCurrentIncome());

        // AllArgs
        DashboardSummaryDTO.TrendDTO t3 = new DashboardSummaryDTO.TrendDTO(
                new BigDecimal("3000"), new BigDecimal("1200"),
                new BigDecimal("2500"), new BigDecimal("1000"),
                new BigDecimal("20.0"),
                cs, ce, ps, pe);
        assertEquals(new BigDecimal("3000"), t3.getCurrentIncome());
    }

    @Test
    @DisplayName("ResultCategoryDTO all getters/setters including householdId and shared")
    void testResultCategory() {
        ResultCategoryDTO dto = new ResultCategoryDTO();
        dto.setId(1L);
        dto.setUserId(2L);
        dto.setName("Cat");
        dto.setDescription("Desc");
        dto.setParentId(3L);
        dto.setIcon("star");
        dto.setColor("#000");
        dto.setType(CategoryType.INCOME);
        dto.setActive(true);
        dto.setHouseholdId(100L);
        dto.setShared(true);
        assertEquals(1L, dto.getId());
        assertEquals(2L, dto.getUserId());
        assertEquals("Cat", dto.getName());
        assertEquals("Desc", dto.getDescription());
        assertEquals(3L, dto.getParentId());
        assertEquals("star", dto.getIcon());
        assertEquals("#000", dto.getColor());
        assertEquals(CategoryType.INCOME, dto.getType());
        assertTrue(dto.getActive());
        assertEquals(100L, dto.getHouseholdId());
        assertTrue(dto.getShared());

        // Default shared is false
        ResultCategoryDTO dto2 = new ResultCategoryDTO();
        assertFalse(dto2.getShared());
    }

    @Test
    @DisplayName("ResultMovementDTO all getters/setters including parentMovementId, isTransfer, transferPairId")
    void testResultMovement() {
        LocalDate now = LocalDate.now();
        ResultMovementDTO dto = new ResultMovementDTO();
        dto.setId(1L);
        dto.setUserId(2L);
        dto.setCategoryId(3L);
        dto.setDate(now);
        dto.setAmount(new BigDecimal("100"));
        dto.setDescription("test");
        dto.setScheduledMovementId(4L);
        dto.setParentMovementId(5L);
        dto.setIsTransfer(true);
        dto.setTransferPairId(6L);
        dto.setConfirmed(true);
        dto.setActive(true);
        assertEquals(1L, dto.getId());
        assertEquals(2L, dto.getUserId());
        assertEquals(3L, dto.getCategoryId());
        assertEquals(now, dto.getDate());
        assertEquals(new BigDecimal("100"), dto.getAmount());
        assertEquals("test", dto.getDescription());
        assertEquals(4L, dto.getScheduledMovementId());
        assertEquals(5L, dto.getParentMovementId());
        assertTrue(dto.getIsTransfer());
        assertEquals(6L, dto.getTransferPairId());
        assertTrue(dto.getConfirmed());
        assertTrue(dto.getActive());
        // Enrichment fields
        dto.setCategoryName("Salario");
        dto.setCategoryType("INCOME");
        dto.setCategoryIcon("wallet");
        dto.setCategoryColor("#4CAF50");
        assertEquals("Salario", dto.getCategoryName());
        assertEquals("INCOME", dto.getCategoryType());
        assertEquals("wallet", dto.getCategoryIcon());
        assertEquals("#4CAF50", dto.getCategoryColor());
    }

    @Test
    @DisplayName("ResultScheduledMovementDTO all getters/setters including categoryName and categoryType")
    void testResultScheduled() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 12, 31);
        ResultScheduledMovementDTO dto = new ResultScheduledMovementDTO();
        dto.setId(1L);
        dto.setUserId(2L);
        dto.setCategoryId(3L);
        dto.setName("Rent");
        dto.setAmount(new BigDecimal("1500"));
        dto.setFrequency(Frequency.MONTHLY);
        dto.setDurationMonths(12);
        dto.setStartDate(start);
        dto.setEndDate(end);
        dto.setDayOfMonth(1);
        dto.setActive(true);
        dto.setCategoryName("Vivienda");
        dto.setCategoryType("EXPENSE");
        assertEquals(1L, dto.getId());
        assertEquals(2L, dto.getUserId());
        assertEquals(3L, dto.getCategoryId());
        assertEquals("Rent", dto.getName());
        assertEquals(new BigDecimal("1500"), dto.getAmount());
        assertEquals(Frequency.MONTHLY, dto.getFrequency());
        assertEquals(12, dto.getDurationMonths());
        assertEquals(start, dto.getStartDate());
        assertEquals(end, dto.getEndDate());
        assertEquals(1, dto.getDayOfMonth());
        assertTrue(dto.getActive());
        assertEquals("Vivienda", dto.getCategoryName());
        assertEquals("EXPENSE", dto.getCategoryType());
    }

    @Test
    @DisplayName("ResultCategoryTreeDTO all fields including householdId and shared")
    void testCategoryTree() {
        ResultCategoryTreeDTO parent = new ResultCategoryTreeDTO();
        parent.setId(1L);
        parent.setName("Parent");
        parent.setDescription("Desc");
        parent.setIcon("folder");
        parent.setColor("#FFF");
        parent.setType(CategoryType.EXPENSE);
        parent.setHouseholdId(100L);
        parent.setShared(true);
        ResultCategoryTreeDTO child = new ResultCategoryTreeDTO();
        child.setId(2L);
        child.setName("Child");
        parent.getChildren().add(child);
        assertEquals(1L, parent.getId());
        assertEquals("Parent", parent.getName());
        assertEquals("Desc", parent.getDescription());
        assertEquals("folder", parent.getIcon());
        assertEquals("#FFF", parent.getColor());
        assertEquals(CategoryType.EXPENSE, parent.getType());
        assertEquals(100L, parent.getHouseholdId());
        assertTrue(parent.getShared());
        assertEquals(1, parent.getChildren().size());
        parent.setChildren(List.of(child));
        assertEquals(1, parent.getChildren().size());

        // Default shared is false
        ResultCategoryTreeDTO dto = new ResultCategoryTreeDTO();
        assertFalse(dto.getShared());
    }

    @Test
    @DisplayName("Entity - Category all constructors, builder, setters, getters including householdId")
    void testCategoryEntity() {
        Category cat = Category.builder()
                .id(1L).userId(2L).name("Cat").description("D")
                .parentId(3L).icon("star").color("#FFF")
                .householdId(100L)
                .type(CategoryType.EXPENSE).active(true).build();
        assertEquals(1L, cat.getId());
        assertEquals(2L, cat.getUserId());
        assertEquals("Cat", cat.getName());
        assertEquals("D", cat.getDescription());
        assertEquals(3L, cat.getParentId());
        assertEquals("star", cat.getIcon());
        assertEquals("#FFF", cat.getColor());
        assertEquals(100L, cat.getHouseholdId());
        assertEquals(CategoryType.EXPENSE, cat.getType());
        assertTrue(cat.getActive());

        // Test setters
        Category cat2 = new Category();
        cat2.setId(10L);
        cat2.setUserId(20L);
        cat2.setName("N");
        cat2.setDescription("D2");
        cat2.setParentId(30L);
        cat2.setIcon("i");
        cat2.setColor("#000");
        cat2.setHouseholdId(200L);
        cat2.setType(CategoryType.INCOME);
        cat2.setActive(false);
        cat2.setCreatedAt(LocalDateTime.now());
        cat2.setUpdatedAt(LocalDateTime.now());
        assertEquals(10L, cat2.getId());
        assertEquals(200L, cat2.getHouseholdId());
        assertNotNull(cat2.getCreatedAt());
        assertNotNull(cat2.getUpdatedAt());

        // AllArgs
        LocalDateTime now = LocalDateTime.now();
        Category cat3 = new Category(1L, 2L, "N", "D", 3L, "i", "#F", 50L, CategoryType.INCOME, true, now, now);
        assertEquals("N", cat3.getName());
        assertEquals(50L, cat3.getHouseholdId());
    }

    @Test
    @DisplayName("Entity - Movement all constructors, builder, setters, getters including parentMovementId, isTransfer, transferPairId")
    void testMovementEntity() {
        LocalDate date = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        Movement m = Movement.builder()
                .id(1L).userId(2L).categoryId(3L)
                .date(date).amount(new BigDecimal("100"))
                .description("test").scheduledMovementId(4L)
                .parentMovementId(5L)
                .isTransfer(true).transferPairId(6L)
                .confirmed(true).active(true).build();
        assertEquals(1L, m.getId());
        assertEquals(2L, m.getUserId());
        assertEquals(3L, m.getCategoryId());
        assertEquals(date, m.getDate());
        assertEquals(new BigDecimal("100"), m.getAmount());
        assertEquals("test", m.getDescription());
        assertEquals(4L, m.getScheduledMovementId());
        assertEquals(5L, m.getParentMovementId());
        assertTrue(m.getIsTransfer());
        assertEquals(6L, m.getTransferPairId());
        assertTrue(m.getConfirmed());
        assertTrue(m.getActive());

        // Setters
        Movement m2 = new Movement();
        m2.setId(10L);
        m2.setUserId(20L);
        m2.setCategoryId(30L);
        m2.setDate(date);
        m2.setAmount(new BigDecimal("200"));
        m2.setDescription("d");
        m2.setScheduledMovementId(40L);
        m2.setParentMovementId(50L);
        m2.setIsTransfer(false);
        m2.setTransferPairId(60L);
        m2.setConfirmed(false);
        m2.setActive(false);
        m2.setCreatedAt(now);
        m2.setUpdatedAt(now);
        assertEquals(10L, m2.getId());
        assertEquals(50L, m2.getParentMovementId());
        assertFalse(m2.getIsTransfer());
        assertEquals(60L, m2.getTransferPairId());
        assertNotNull(m2.getCreatedAt());
        assertNotNull(m2.getUpdatedAt());

        // AllArgs
        Movement m3 = new Movement(1L, 2L, 3L, date, new BigDecimal("50"), "d", 4L, date, 5L, true, 6L, true, true, now, now);
        assertEquals(new BigDecimal("50"), m3.getAmount());
        assertEquals(date, m3.getPeriodStart());
        assertEquals(5L, m3.getParentMovementId());
        assertTrue(m3.getIsTransfer());
        assertEquals(6L, m3.getTransferPairId());
    }

    @Test
    @DisplayName("Entity - ScheduledMovement all constructors, builder, setters, getters")
    void testScheduledEntity() {
        LocalDate date = LocalDate.now();
        LocalDate endDate = LocalDate.now().plusMonths(12);
        LocalDateTime now = LocalDateTime.now();
        ScheduledMovement sm = ScheduledMovement.builder()
                .id(1L).userId(2L).categoryId(3L).name("Rent")
                .amount(new BigDecimal("1500000")).frequency(Frequency.MONTHLY)
                .durationMonths(12).startDate(date).endDate(endDate)
                .dayOfMonth(1).active(true).build();
        assertEquals(1L, sm.getId());
        assertEquals(2L, sm.getUserId());
        assertEquals(3L, sm.getCategoryId());
        assertEquals("Rent", sm.getName());
        assertEquals(new BigDecimal("1500000"), sm.getAmount());
        assertEquals(Frequency.MONTHLY, sm.getFrequency());
        assertEquals(12, sm.getDurationMonths());
        assertEquals(date, sm.getStartDate());
        assertEquals(endDate, sm.getEndDate());
        assertEquals(1, sm.getDayOfMonth());
        assertTrue(sm.getActive());

        // Setters
        ScheduledMovement sm2 = new ScheduledMovement();
        sm2.setId(10L);
        sm2.setUserId(20L);
        sm2.setCategoryId(30L);
        sm2.setName("Test");
        sm2.setAmount(new BigDecimal("100"));
        sm2.setFrequency(Frequency.WEEKLY);
        sm2.setDurationMonths(6);
        sm2.setStartDate(date);
        sm2.setEndDate(endDate);
        sm2.setDayOfMonth(15);
        sm2.setActive(false);
        sm2.setCreatedAt(now);
        sm2.setUpdatedAt(now);
        assertEquals(10L, sm2.getId());
        assertNotNull(sm2.getCreatedAt());
        assertNotNull(sm2.getUpdatedAt());

        // AllArgs
        ScheduledMovement sm3 = new ScheduledMovement(1L, 2L, 3L, "N", new BigDecimal("50"),
                Frequency.ANNUAL, 12, date, endDate, 1, true, now, now);
        assertEquals("N", sm3.getName());
    }

    @Test
    @DisplayName("Entity - Household builder and fields")
    void testHouseholdEntity() {
        Household h = Household.builder().id(1L).name("Familia").active(true).build();
        assertEquals(1L, h.getId());
        assertEquals("Familia", h.getName());
        assertTrue(h.getActive());

        Household h2 = new Household();
        h2.setId(2L);
        h2.setName("Test");
        h2.setActive(false);
        assertEquals(2L, h2.getId());
    }

    @Test
    @DisplayName("Entity - HouseholdMember builder and fields")
    void testHouseholdMemberEntity() {
        HouseholdMember hm = HouseholdMember.builder()
                .id(1L).householdId(10L).userId(20L).role("OWNER").active(true).build();
        assertEquals(1L, hm.getId());
        assertEquals(10L, hm.getHouseholdId());
        assertEquals(20L, hm.getUserId());
        assertEquals("OWNER", hm.getRole());
        assertTrue(hm.getActive());

        HouseholdMember hm2 = new HouseholdMember();
        hm2.setId(2L);
        hm2.setHouseholdId(20L);
        hm2.setUserId(30L);
        hm2.setRole("MEMBER");
        hm2.setActive(false);
        assertEquals(2L, hm2.getId());
        assertEquals("MEMBER", hm2.getRole());
    }

    @Test
    @DisplayName("Entity - BudgetAllocation builder and fields")
    void testBudgetAllocationEntity() {
        LocalDate start = LocalDate.of(2026, 3, 1);
        LocalDate end = LocalDate.of(2026, 3, 31);
        BudgetAllocation ba = BudgetAllocation.builder()
                .id(1L).userId(2L).categoryId(3L).householdId(4L)
                .periodStart(start).periodEnd(end)
                .allocatedAmount(new BigDecimal("500000"))
                .status("ACTIVE").notes("test").active(true).build();
        assertEquals(1L, ba.getId());
        assertEquals(2L, ba.getUserId());
        assertEquals(3L, ba.getCategoryId());
        assertEquals(4L, ba.getHouseholdId());
        assertEquals(start, ba.getPeriodStart());
        assertEquals(end, ba.getPeriodEnd());
        assertEquals(new BigDecimal("500000"), ba.getAllocatedAmount());
        assertEquals("ACTIVE", ba.getStatus());
        assertEquals("test", ba.getNotes());
        assertTrue(ba.getActive());
    }

    @Test
    @DisplayName("Frequency enum has 8 values including DAILY")
    void testFrequencyEnum() {
        assertEquals(8, Frequency.values().length);
        assertNotNull(Frequency.valueOf("DAILY"));
        assertNotNull(Frequency.valueOf("WEEKLY"));
        assertNotNull(Frequency.valueOf("BIWEEKLY"));
        assertNotNull(Frequency.valueOf("MONTHLY"));
        assertNotNull(Frequency.valueOf("BIMONTHLY"));
        assertNotNull(Frequency.valueOf("QUARTERLY"));
        assertNotNull(Frequency.valueOf("SEMIANNUAL"));
        assertNotNull(Frequency.valueOf("ANNUAL"));
    }

    @Test
    @DisplayName("CategoryType enum values")
    void testCategoryTypeEnum() {
        assertEquals(2, CategoryType.values().length);
        assertNotNull(CategoryType.valueOf("INCOME"));
        assertNotNull(CategoryType.valueOf("EXPENSE"));
    }
}

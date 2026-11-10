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
        /*
         * El cuarto y ultimo constructor posicional de este fichero, con el
         * mismo tratamiento que Movement, MovementSaveDTO y Category: por
         * reflexion que EXISTA, y los valores con setters, que nombran cada
         * campo.
         *
         * Los cuatro se rompieron al crecer su clase, y ninguna de esas roturas
         * senalaba un problema real: solo argumentos que recolocar. Peor, una
         * lista de nueve parametros con tres Long y dos String seguidos acepta
         * encantada dos intercambiados y sigue en verde.
         */
        long camposDelDto = java.util.Arrays.stream(CategorySaveDTO.class.getDeclaredFields())
                .filter(f -> !java.lang.reflect.Modifier.isStatic(f.getModifiers()))
                .filter(f -> !f.isSynthetic())
                .count();
        assertTrue(java.util.Arrays.stream(CategorySaveDTO.class.getDeclaredConstructors())
                        .anyMatch(c -> c.getParameterCount() == camposDelDto),
                "@AllArgsConstructor dejo de generarse para CategorySaveDTO");

        CategorySaveDTO dto = new CategorySaveDTO();
        dto.setId(1L);
        dto.setUserId(2L);
        dto.setName("Cat");
        dto.setDescription("Desc");
        dto.setParentId(3L);
        dto.setIcon("icon");
        dto.setColor("#FFF");
        dto.setType(CategoryType.INCOME);
        dto.setHouseholdId(100L);
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
        /*
         * Mismo tratamiento que en Movement: por reflexion que EXISTA, y el
         * ida y vuelta con setters, que nombran cada campo. El constructor
         * posicional rompio dos veces en un dia y ninguna de las dos indicaba
         * un problema real del DTO.
         */
        long camposDeInstancia = java.util.Arrays.stream(MovementSaveDTO.class.getDeclaredFields())
                .filter(f -> !java.lang.reflect.Modifier.isStatic(f.getModifiers()))
                .filter(f -> !f.isSynthetic())
                .count();
        assertTrue(java.util.Arrays.stream(MovementSaveDTO.class.getDeclaredConstructors())
                        .anyMatch(c -> c.getParameterCount() == camposDeInstancia),
                "@AllArgsConstructor dejo de generarse para MovementSaveDTO");

        MovementSaveDTO dto = new MovementSaveDTO();
        dto.setId(1L);
        dto.setUserId(2L);
        dto.setCategoryId(3L);
        dto.setDate(now);
        dto.setAmount(new BigDecimal("1000"));
        dto.setDescription("desc");
        dto.setScheduledMovementId(4L);
        dto.setParentMovementId(5L);
        dto.setConfirmed(true);
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

        // Sin decir nada, confirmed es nulo: al crear vale "pagado", al editar
        // deja el estado como estaba (ver MovementServiceImpl).
        MovementSaveDTO dto3 = new MovementSaveDTO();
        assertNull(dto3.getConfirmed());
    }

    @Test
    @DisplayName("ScheduledMovementSaveDTO all fields via setters and AllArgsConstructor")
    void testScheduledSave() {
        LocalDate now = LocalDate.now();
        ScheduledMovementSaveDTO dto = new ScheduledMovementSaveDTO(1L, 2L, 3L, "Rent",
                new BigDecimal("500"), Frequency.MONTHLY, 12, now, 15, 3, 5,
                com.ohchurus.budget.enums.WeekendPolicy.PREVIOUS_BUSINESS_DAY, null, null, null, null);
        assertEquals(1L, dto.getId());
        assertEquals(2L, dto.getUserId());
        assertEquals(3L, dto.getCategoryId());
        assertEquals("Rent", dto.getName());
        assertEquals(new BigDecimal("500"), dto.getAmount());
        assertEquals(Frequency.MONTHLY, dto.getFrequency());
        assertEquals(12, dto.getDurationMonths());
        assertEquals(now, dto.getStartDate());
        assertEquals(15, dto.getDayOfMonth());
        assertEquals(3, dto.getWeekOfMonth());
        assertEquals(5, dto.getDayOfWeek());
        assertEquals(com.ohchurus.budget.enums.WeekendPolicy.PREVIOUS_BUSINESS_DAY, dto.getWeekendPolicy());

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
    @DisplayName("ResultMovementDTO all getters/setters including parentMovementId")
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

        /* Mismo tratamiento que Movement y MovementSaveDTO: por reflexion que
           el constructor EXISTA, y el ida y vuelta con el builder, que nombra
           cada campo. El posicional se rompia cada vez que la entidad crecia
           —ahora con `reimbursable`— y ademas aceptaba encantado dos Boolean
           intercambiados. */
        LocalDateTime now = LocalDateTime.now();
        long camposDeCategoria = java.util.Arrays.stream(Category.class.getDeclaredFields())
                .filter(f -> !java.lang.reflect.Modifier.isStatic(f.getModifiers()))
                .filter(f -> !f.isSynthetic())
                .count();
        assertTrue(java.util.Arrays.stream(Category.class.getDeclaredConstructors())
                        .anyMatch(c -> c.getParameterCount() == camposDeCategoria),
                "@AllArgsConstructor dejo de generarse para Category");

        Category cat3 = Category.builder()
                .id(1L).userId(2L).name("N").description("D").parentId(3L)
                .icon("i").color("#F").householdId(50L).type(CategoryType.INCOME)
                .reimbursable(false).active(true).createdAt(now).updatedAt(now)
                .build();
        assertEquals("N", cat3.getName());
        assertEquals(50L, cat3.getHouseholdId());
        assertFalse(cat3.getReimbursable());
    }

    @Test
    @DisplayName("Entity - Movement all constructors, builder, setters, getters including parentMovementId")
    void testMovementEntity() {
        LocalDate date = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        Movement m = Movement.builder()
                .id(1L).userId(2L).categoryId(3L)
                .date(date).amount(new BigDecimal("100"))
                .description("test").scheduledMovementId(4L)
                .parentMovementId(5L)
                .confirmed(true).active(true).build();
        assertEquals(1L, m.getId());
        assertEquals(2L, m.getUserId());
        assertEquals(3L, m.getCategoryId());
        assertEquals(date, m.getDate());
        assertEquals(new BigDecimal("100"), m.getAmount());
        assertEquals("test", m.getDescription());
        assertEquals(4L, m.getScheduledMovementId());
        assertEquals(5L, m.getParentMovementId());
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
        m2.setConfirmed(false);
        m2.setActive(false);
        m2.setCreatedAt(now);
        m2.setUpdatedAt(now);
        assertEquals(10L, m2.getId());
        assertEquals(50L, m2.getParentMovementId());
        assertNotNull(m2.getCreatedAt());
        assertNotNull(m2.getUpdatedAt());

        /*
         * @AllArgsConstructor: que EXISTA y acepte todos los campos.
         *
         * Antes esto se comprobaba llamandolo con la lista de argumentos
         * escrita a mano. Rompio dos veces en un mismo dia —al anadir la
         * cuenta y al anadir el reparto— y las dos veces el arreglo consistio
         * en recolocar literales en el orden correcto, que no demuestra nada
         * de la entidad y si le hace perder el tiempo a quien anade un campo.
         *
         * Peor todavia: un constructor posicional con quince Long y seis
         * Boolean seguidos acepta encantado dos argumentos intercambiados. La
         * prueba pasaba en verde con los valores en el sitio equivocado.
         *
         * Ahora se comprueba por reflexion que el constructor existe con
         * tantos parametros como campos de instancia, y el ida y vuelta de los
         * valores se prueba con el builder, que nombra cada uno.
         */
        long camposDeInstancia = java.util.Arrays.stream(Movement.class.getDeclaredFields())
                .filter(f -> !java.lang.reflect.Modifier.isStatic(f.getModifiers()))
                .filter(f -> !f.isSynthetic())
                .count();
        assertTrue(java.util.Arrays.stream(Movement.class.getDeclaredConstructors())
                        .anyMatch(c -> c.getParameterCount() == camposDeInstancia),
                "@AllArgsConstructor dejo de generarse para Movement");

        Movement m3 = Movement.builder()
                .id(1L).userId(2L).categoryId(3L).date(date).amount(new BigDecimal("50"))
                .description("d").scheduledMovementId(4L).periodStart(date).parentMovementId(5L)
                .confirmed(true).active(true).createdAt(now).updatedAt(now)
                .build();
        assertEquals(new BigDecimal("50"), m3.getAmount());
        assertEquals(date, m3.getPeriodStart());
        assertEquals(5L, m3.getParentMovementId());
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
                Frequency.ANNUAL, 12, date, endDate, 1, 3, 5,
                com.ohchurus.budget.enums.WeekendPolicy.KEEP, null, null, null, true, now, now);
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
                .notes("test").active(true).build();
        assertEquals(1L, ba.getId());
        assertEquals(2L, ba.getUserId());
        assertEquals(3L, ba.getCategoryId());
        assertEquals(4L, ba.getHouseholdId());
        assertEquals(start, ba.getPeriodStart());
        assertEquals(end, ba.getPeriodEnd());
        assertEquals(new BigDecimal("500000"), ba.getAllocatedAmount());
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

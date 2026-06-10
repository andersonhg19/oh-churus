package com.ohchurus.budget.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ohchurus.budget.dto.input.MovementFilterDTO;
import com.ohchurus.budget.dto.input.MovementSaveDTO;
import com.ohchurus.budget.dto.input.PeriodRequestDTO;
import com.ohchurus.budget.dto.output.ResultDTO;
import com.ohchurus.budget.security.JWTAuthorizationFilter;
import com.ohchurus.budget.security.SecParams;
import com.ohchurus.budget.service.MovementService;
import com.ohchurus.budget.service.impl.BudgetAllocationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MovementController.class)
@AutoConfigureMockMvc(addFilters = false)
class MovementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MovementService movementService;

    @MockBean
    private BudgetAllocationServiceImpl budgetAllocationService;

    @MockBean
    private JWTAuthorizationFilter jwtAuthorizationFilter;

    @MockBean
    private SecParams secParams;

    private ResultDTO successResult;
    private ResultDTO errorResult;

    @BeforeEach
    void setUp() {
        successResult = new ResultDTO("data");
        errorResult = new ResultDTO(false, "Error", 100);
    }

    @Nested
    @DisplayName("POST /v1/movements/save")
    class SaveTests {

        @Test
        @DisplayName("Should save movement and return 200")
        void shouldSaveMovement() throws Exception {
            MovementSaveDTO dto = new MovementSaveDTO();
            dto.setUserId(1L);
            dto.setCategoryId(1L);
            dto.setDate(LocalDate.of(2026, 3, 15));
            dto.setAmount(new BigDecimal("100.00"));

            when(movementService.saveAndUpdate(any(MovementSaveDTO.class))).thenReturn(successResult);

            mockMvc.perform(post("/v1/movements/save")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.correct").value(true));

            verify(movementService).saveAndUpdate(any(MovementSaveDTO.class));
        }

        @Test
        @DisplayName("Should return error when service fails")
        void shouldReturnErrorOnServiceFailure() throws Exception {
            MovementSaveDTO dto = new MovementSaveDTO();
            dto.setUserId(1L);
            dto.setCategoryId(1L);
            dto.setDate(LocalDate.of(2026, 3, 15));
            dto.setAmount(new BigDecimal("100.00"));

            when(movementService.saveAndUpdate(any(MovementSaveDTO.class))).thenReturn(errorResult);

            mockMvc.perform(post("/v1/movements/save")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.correct").value(false))
                    .andExpect(jsonPath("$.errorCode").value(100));
        }

        @Test
        @DisplayName("Should return 400 when userId is missing")
        void shouldReturn400WhenUserIdMissing() throws Exception {
            MovementSaveDTO dto = new MovementSaveDTO();
            dto.setCategoryId(1L);
            dto.setDate(LocalDate.of(2026, 3, 15));
            dto.setAmount(new BigDecimal("100.00"));

            mockMvc.perform(post("/v1/movements/save")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 400 when amount is missing")
        void shouldReturn400WhenAmountMissing() throws Exception {
            MovementSaveDTO dto = new MovementSaveDTO();
            dto.setUserId(1L);
            dto.setCategoryId(1L);
            dto.setDate(LocalDate.of(2026, 3, 15));

            mockMvc.perform(post("/v1/movements/save")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 400 when date is missing")
        void shouldReturn400WhenDateMissing() throws Exception {
            MovementSaveDTO dto = new MovementSaveDTO();
            dto.setUserId(1L);
            dto.setCategoryId(1L);
            dto.setAmount(new BigDecimal("100.00"));

            mockMvc.perform(post("/v1/movements/save")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 400 when amount is less than 0.01")
        void shouldReturn400WhenAmountTooSmall() throws Exception {
            MovementSaveDTO dto = new MovementSaveDTO();
            dto.setUserId(1L);
            dto.setCategoryId(1L);
            dto.setDate(LocalDate.of(2026, 3, 15));
            dto.setAmount(new BigDecimal("0.00"));

            mockMvc.perform(post("/v1/movements/save")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /v1/movements/get/{id}")
    class GetByIdTests {

        @Test
        @DisplayName("Should return movement by id")
        void shouldReturnMovementById() throws Exception {
            when(movementService.getById(1L)).thenReturn(successResult);

            mockMvc.perform(post("/v1/movements/get/1")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.correct").value(true));

            verify(movementService).getById(1L);
        }

        @Test
        @DisplayName("Should return error when not found")
        void shouldReturnErrorWhenNotFound() throws Exception {
            when(movementService.getById(99L)).thenReturn(errorResult);

            mockMvc.perform(post("/v1/movements/get/99")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.correct").value(false));

            verify(movementService).getById(99L);
        }
    }

    @Nested
    @DisplayName("POST /v1/movements/all")
    class GetAllTests {

        @Test
        @DisplayName("Should return all movements with filters")
        void shouldReturnAllMovements() throws Exception {
            MovementFilterDTO filter = new MovementFilterDTO();
            filter.setUserId(1L);

            when(movementService.getAll(any(MovementFilterDTO.class))).thenReturn(successResult);

            mockMvc.perform(post("/v1/movements/all")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(filter)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.correct").value(true));

            verify(movementService).getAll(any(MovementFilterDTO.class));
        }

        @Test
        @DisplayName("Should return error on service failure")
        void shouldReturnErrorOnServiceFailure() throws Exception {
            MovementFilterDTO filter = new MovementFilterDTO();

            when(movementService.getAll(any(MovementFilterDTO.class))).thenReturn(errorResult);

            mockMvc.perform(post("/v1/movements/all")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(filter)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.correct").value(false));
        }
    }

    @Nested
    @DisplayName("POST /v1/movements/delete/{id}")
    class DeleteTests {

        @Test
        @DisplayName("Should delete movement")
        void shouldDeleteMovement() throws Exception {
            when(movementService.delete(1L)).thenReturn(successResult);

            mockMvc.perform(post("/v1/movements/delete/1")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.correct").value(true));

            verify(movementService).delete(1L);
        }

        @Test
        @DisplayName("Should return error when delete fails")
        void shouldReturnErrorOnDeleteFailure() throws Exception {
            when(movementService.delete(1L)).thenReturn(errorResult);

            mockMvc.perform(post("/v1/movements/delete/1")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.correct").value(false));
        }
    }

    @Nested
    @DisplayName("POST /v1/movements/confirm/{id}")
    class ConfirmTests {

        @Test
        @DisplayName("Should confirm movement")
        void shouldConfirmMovement() throws Exception {
            when(movementService.confirm(1L)).thenReturn(successResult);

            mockMvc.perform(post("/v1/movements/confirm/1")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.correct").value(true));

            verify(movementService).confirm(1L);
        }

        @Test
        @DisplayName("Should confirm movement with amount")
        void shouldConfirmWithAmount() throws Exception {
            when(movementService.confirmWithAmount(eq(1L), any(BigDecimal.class))).thenReturn(successResult);

            mockMvc.perform(post("/v1/movements/confirm/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"amount\": 250000}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.correct").value(true));

            verify(movementService).confirmWithAmount(eq(1L), any(BigDecimal.class));
        }

        @Test
        @DisplayName("Should confirm with a body that has no amount key")
        void shouldConfirmWithBodyNoAmount() throws Exception {
            when(movementService.confirm(1L)).thenReturn(successResult);

            mockMvc.perform(post("/v1/movements/confirm/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isOk());

            verify(movementService).confirm(1L);
        }

        @Test
        @DisplayName("Should return error when confirm fails")
        void shouldReturnErrorOnConfirmFailure() throws Exception {
            when(movementService.confirm(99L)).thenReturn(errorResult);

            mockMvc.perform(post("/v1/movements/confirm/99")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.correct").value(false));
        }
    }

    @Nested
    @DisplayName("POST /v1/movements/by-period")
    class ByPeriodTests {

        @Test
        @DisplayName("Should return movements by period")
        void shouldReturnByPeriod() throws Exception {
            PeriodRequestDTO dto = new PeriodRequestDTO();
            dto.setUserId(1L);
            dto.setStartDate(LocalDate.of(2026, 3, 1));
            dto.setEndDate(LocalDate.of(2026, 3, 31));

            when(movementService.getByPeriod(eq(1L), any(LocalDate.class), any(LocalDate.class)))
                    .thenReturn(successResult);

            mockMvc.perform(post("/v1/movements/by-period")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.correct").value(true));

            verify(movementService).getByPeriod(eq(1L), any(LocalDate.class), any(LocalDate.class));
        }

        @Test
        @DisplayName("Should return 400 when userId is missing")
        void shouldReturn400WhenUserIdMissing() throws Exception {
            PeriodRequestDTO dto = new PeriodRequestDTO();
            dto.setStartDate(LocalDate.of(2026, 3, 1));
            dto.setEndDate(LocalDate.of(2026, 3, 31));

            mockMvc.perform(post("/v1/movements/by-period")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 400 when startDate is missing")
        void shouldReturn400WhenStartDateMissing() throws Exception {
            PeriodRequestDTO dto = new PeriodRequestDTO();
            dto.setUserId(1L);
            dto.setEndDate(LocalDate.of(2026, 3, 31));

            mockMvc.perform(post("/v1/movements/by-period")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /v1/movements/children/{parentId}")
    class ChildrenTests {

        @Test
        @DisplayName("Should return children of a parent movement")
        void shouldReturnChildren() throws Exception {
            when(movementService.getChildren(50L)).thenReturn(successResult);

            mockMvc.perform(post("/v1/movements/children/50")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.correct").value(true));

            verify(movementService).getChildren(50L);
        }

        @Test
        @DisplayName("Should return error when parent not found")
        void shouldReturnErrorWhenParentNotFound() throws Exception {
            when(movementService.getChildren(999L)).thenReturn(errorResult);

            mockMvc.perform(post("/v1/movements/children/999")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.correct").value(false));
        }
    }

    @Nested
    @DisplayName("POST /v1/movements/transfer")
    class TransferTests {

        @Test
        @DisplayName("Should parse body with description and delegate to BudgetAllocationService")
        void shouldTransferWithDescription() throws Exception {
            when(budgetAllocationService.transfer(eq(1L), eq(30L), eq(20L),
                    eq(new BigDecimal("50000")), eq("Disponibilizar"))).thenReturn(successResult);

            String body = "{\"userId\":1,\"fromCategoryId\":30,\"toCategoryId\":20,"
                    + "\"amount\":50000,\"description\":\"Disponibilizar\"}";

            mockMvc.perform(post("/v1/movements/transfer")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.correct").value(true));

            verify(budgetAllocationService).transfer(1L, 30L, 20L, new BigDecimal("50000"), "Disponibilizar");
        }

        @Test
        @DisplayName("Should default description to null when absent")
        void shouldTransferWithoutDescription() throws Exception {
            when(budgetAllocationService.transfer(eq(1L), eq(30L), eq(20L),
                    eq(new BigDecimal("50000")), eq(null))).thenReturn(successResult);

            String body = "{\"userId\":1,\"fromCategoryId\":30,\"toCategoryId\":20,\"amount\":50000}";

            mockMvc.perform(post("/v1/movements/transfer")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk());

            verify(budgetAllocationService).transfer(1L, 30L, 20L, new BigDecimal("50000"), null);
        }
    }
}

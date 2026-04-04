package com.ohchurus.budget.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ohchurus.budget.dto.input.DashboardRequestDTO;
import com.ohchurus.budget.dto.output.ResultDTO;
import com.ohchurus.budget.security.JWTAuthorizationFilter;
import com.ohchurus.budget.security.SecParams;
import com.ohchurus.budget.service.DashboardService;
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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DashboardController.class)
@AutoConfigureMockMvc(addFilters = false)
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DashboardService dashboardService;

    @MockBean
    private JWTAuthorizationFilter jwtAuthorizationFilter;

    @MockBean
    private SecParams secParams;

    private ResultDTO successResult;
    private ResultDTO errorResult;
    private DashboardRequestDTO validRequest;

    @BeforeEach
    void setUp() {
        successResult = new ResultDTO("data");
        errorResult = new ResultDTO(false, "Error", 100);

        validRequest = new DashboardRequestDTO();
        validRequest.setUserId(1L);
        validRequest.setBudgetStartDay(15);
    }

    @Nested
    @DisplayName("POST /v1/dashboard/summary")
    class SummaryTests {

        @Test
        @DisplayName("Should return dashboard summary")
        void shouldReturnSummary() throws Exception {
            when(dashboardService.getSummary(eq(1L), eq(15))).thenReturn(successResult);

            mockMvc.perform(post("/v1/dashboard/summary")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.correct").value(true));

            verify(dashboardService).getSummary(eq(1L), eq(15));
        }

        @Test
        @DisplayName("Should return error when service fails")
        void shouldReturnErrorOnServiceFailure() throws Exception {
            when(dashboardService.getSummary(eq(1L), eq(15))).thenReturn(errorResult);

            mockMvc.perform(post("/v1/dashboard/summary")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.correct").value(false));
        }

        @Test
        @DisplayName("Should return 400 when userId is missing")
        void shouldReturn400WhenUserIdMissing() throws Exception {
            DashboardRequestDTO dto = new DashboardRequestDTO();
            dto.setBudgetStartDay(15);

            mockMvc.perform(post("/v1/dashboard/summary")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /v1/dashboard/by-category")
    class ByCategoryTests {

        @Test
        @DisplayName("Should return breakdown by category")
        void shouldReturnByCategory() throws Exception {
            when(dashboardService.getByCategory(eq(1L), eq(15))).thenReturn(successResult);

            mockMvc.perform(post("/v1/dashboard/by-category")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.correct").value(true));

            verify(dashboardService).getByCategory(eq(1L), eq(15));
        }

        @Test
        @DisplayName("Should return error when service fails")
        void shouldReturnErrorOnServiceFailure() throws Exception {
            when(dashboardService.getByCategory(eq(1L), eq(15))).thenReturn(errorResult);

            mockMvc.perform(post("/v1/dashboard/by-category")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.correct").value(false));
        }

        @Test
        @DisplayName("Should return 400 when userId is missing")
        void shouldReturn400WhenUserIdMissing() throws Exception {
            DashboardRequestDTO dto = new DashboardRequestDTO();

            mockMvc.perform(post("/v1/dashboard/by-category")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /v1/dashboard/trend")
    class TrendTests {

        @Test
        @DisplayName("Should return trend data")
        void shouldReturnTrend() throws Exception {
            when(dashboardService.getTrend(eq(1L), eq(15))).thenReturn(successResult);

            mockMvc.perform(post("/v1/dashboard/trend")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.correct").value(true));

            verify(dashboardService).getTrend(eq(1L), eq(15));
        }

        @Test
        @DisplayName("Should return error when service fails")
        void shouldReturnErrorOnServiceFailure() throws Exception {
            when(dashboardService.getTrend(eq(1L), eq(15))).thenReturn(errorResult);

            mockMvc.perform(post("/v1/dashboard/trend")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.correct").value(false));
        }

        @Test
        @DisplayName("Should return 400 when userId is missing")
        void shouldReturn400WhenUserIdMissing() throws Exception {
            DashboardRequestDTO dto = new DashboardRequestDTO();

            mockMvc.perform(post("/v1/dashboard/trend")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /v1/dashboard/pending")
    class PendingTests {

        @Test
        @DisplayName("Should return pending movements")
        void shouldReturnPending() throws Exception {
            when(dashboardService.getPending(eq(1L), eq(15))).thenReturn(successResult);

            mockMvc.perform(post("/v1/dashboard/pending")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.correct").value(true));

            verify(dashboardService).getPending(eq(1L), eq(15));
        }

        @Test
        @DisplayName("Should return error when service fails")
        void shouldReturnErrorOnServiceFailure() throws Exception {
            when(dashboardService.getPending(eq(1L), eq(15))).thenReturn(errorResult);

            mockMvc.perform(post("/v1/dashboard/pending")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.correct").value(false));
        }

        @Test
        @DisplayName("Should return 400 when userId is missing")
        void shouldReturn400WhenUserIdMissing() throws Exception {
            DashboardRequestDTO dto = new DashboardRequestDTO();

            mockMvc.perform(post("/v1/dashboard/pending")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
        }
    }
}

package com.ohchurus.fasting.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ohchurus.fasting.dto.output.ResultDTO;
import com.ohchurus.fasting.enums.PlanType;
import com.ohchurus.fasting.security.JWTAuthorizationFilter;
import com.ohchurus.fasting.security.SecParams;
import com.ohchurus.fasting.service.impl.FastingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FastingController.class)
@AutoConfigureMockMvc(addFilters = false)
class FastingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private FastingServiceImpl fastingService;

    @MockBean
    private JWTAuthorizationFilter jwtAuthorizationFilter;

    @MockBean
    private SecParams secParams;

    private ResultDTO ok;

    @BeforeEach
    void setUp() {
        ok = new ResultDTO("data");
    }

    private String json(Map<String, Object> body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    @Test
    @DisplayName("POST /plan/get")
    void shouldGetPlan() throws Exception {
        when(fastingService.getPlanConfig(3L)).thenReturn(ok);
        mockMvc.perform(post("/v1/fasting/plan/get").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("userId", 3))))
                .andExpect(status().isOk());
        verify(fastingService).getPlanConfig(3L);
    }

    @Test
    @DisplayName("POST /plan/save with all optional fields")
    void shouldSavePlanFull() throws Exception {
        when(fastingService.savePlanConfig(eq(3L), eq(PlanType.CUSTOM), eq(20), eq(4), eq("20:00"), eq(true)))
                .thenReturn(ok);
        Map<String, Object> body = new HashMap<>();
        body.put("userId", 3);
        body.put("planType", "CUSTOM");
        body.put("fastingHours", 20);
        body.put("eatingHours", 4);
        body.put("suggestedStartTime", "20:00");
        body.put("remindersEnabled", true);

        mockMvc.perform(post("/v1/fasting/plan/save").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk());
        verify(fastingService).savePlanConfig(3L, PlanType.CUSTOM, 20, 4, "20:00", true);
    }

    @Test
    @DisplayName("POST /plan/save with defaults (optional fields absent)")
    void shouldSavePlanDefaults() throws Exception {
        when(fastingService.savePlanConfig(eq(3L), eq(PlanType.PLAN_16_8), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(ok);
        mockMvc.perform(post("/v1/fasting/plan/save").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("userId", 3, "planType", "PLAN_16_8"))))
                .andExpect(status().isOk());
        verify(fastingService).savePlanConfig(3L, PlanType.PLAN_16_8, null, null, null, null);
    }

    @Test
    @DisplayName("POST /plan/presets")
    void shouldGetPresets() throws Exception {
        when(fastingService.getPresets()).thenReturn(ok);
        mockMvc.perform(post("/v1/fasting/plan/presets").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        verify(fastingService).getPresets();
    }

    @Test
    @DisplayName("POST /session/start with custom startTime")
    void shouldStartWithTime() throws Exception {
        LocalDateTime start = LocalDateTime.of(2026, 3, 15, 20, 0);
        when(fastingService.startFasting(eq(3L), eq(start))).thenReturn(ok);
        mockMvc.perform(post("/v1/fasting/session/start").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("userId", 3, "startTime", start.toString()))))
                .andExpect(status().isOk());
        verify(fastingService).startFasting(3L, start);
    }

    @Test
    @DisplayName("POST /session/start without startTime")
    void shouldStartNow() throws Exception {
        when(fastingService.startFasting(eq(3L), isNull())).thenReturn(ok);
        mockMvc.perform(post("/v1/fasting/session/start").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("userId", 3))))
                .andExpect(status().isOk());
        verify(fastingService).startFasting(3L, null);
    }

    @Test
    @DisplayName("POST /session/stop with custom endTime")
    void shouldStopWithTime() throws Exception {
        LocalDateTime end = LocalDateTime.of(2026, 3, 16, 12, 0);
        when(fastingService.stopFasting(eq(3L), eq(end))).thenReturn(ok);
        mockMvc.perform(post("/v1/fasting/session/stop").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("userId", 3, "endTime", end.toString()))))
                .andExpect(status().isOk());
        verify(fastingService).stopFasting(3L, end);
    }

    @Test
    @DisplayName("POST /session/stop without endTime")
    void shouldStopNow() throws Exception {
        when(fastingService.stopFasting(eq(3L), isNull())).thenReturn(ok);
        mockMvc.perform(post("/v1/fasting/session/stop").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("userId", 3))))
                .andExpect(status().isOk());
        verify(fastingService).stopFasting(3L, null);
    }

    @Test
    @DisplayName("POST /session/cancel")
    void shouldCancel() throws Exception {
        when(fastingService.cancelFasting(3L)).thenReturn(ok);
        mockMvc.perform(post("/v1/fasting/session/cancel").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("userId", 3))))
                .andExpect(status().isOk());
        verify(fastingService).cancelFasting(3L);
    }

    @Test
    @DisplayName("POST /session/active")
    void shouldGetActive() throws Exception {
        when(fastingService.getActiveSession(3L)).thenReturn(ok);
        mockMvc.perform(post("/v1/fasting/session/active").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("userId", 3))))
                .andExpect(status().isOk());
        verify(fastingService).getActiveSession(3L);
    }

    @Test
    @DisplayName("POST /session/edit with start and end")
    void shouldEdit() throws Exception {
        LocalDateTime start = LocalDateTime.of(2026, 3, 15, 8, 0);
        LocalDateTime end = LocalDateTime.of(2026, 3, 16, 0, 0);
        when(fastingService.editSession(eq(1L), eq(start), eq(end))).thenReturn(ok);
        mockMvc.perform(post("/v1/fasting/session/edit").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("sessionId", 1, "startTime", start.toString(), "endTime", end.toString()))))
                .andExpect(status().isOk());
        verify(fastingService).editSession(1L, start, end);
    }

    @Test
    @DisplayName("POST /session/edit without optional times")
    void shouldEditNoTimes() throws Exception {
        when(fastingService.editSession(eq(1L), isNull(), isNull())).thenReturn(ok);
        mockMvc.perform(post("/v1/fasting/session/edit").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("sessionId", 1))))
                .andExpect(status().isOk());
        verify(fastingService).editSession(1L, null, null);
    }

    @Test
    @DisplayName("POST /history/by-period with referenceDate")
    void shouldGetHistory() throws Exception {
        LocalDate ref = LocalDate.of(2026, 3, 20);
        when(fastingService.getHistoryByPeriod(eq(3L), eq(15), eq(ref))).thenReturn(ok);
        mockMvc.perform(post("/v1/fasting/history/by-period").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("userId", 3, "budgetStartDay", 15, "referenceDate", ref.toString()))))
                .andExpect(status().isOk());
        verify(fastingService).getHistoryByPeriod(3L, 15, ref);
    }

    @Test
    @DisplayName("POST /history/by-period with defaults")
    void shouldGetHistoryDefaults() throws Exception {
        when(fastingService.getHistoryByPeriod(eq(3L), eq(1), isNull())).thenReturn(ok);
        mockMvc.perform(post("/v1/fasting/history/by-period").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("userId", 3))))
                .andExpect(status().isOk());
        verify(fastingService).getHistoryByPeriod(3L, 1, null);
    }

    @Test
    @DisplayName("POST /history/summary")
    void shouldGetSummary() throws Exception {
        when(fastingService.getSummaryByPeriod(eq(3L), eq(1), isNull())).thenReturn(ok);
        mockMvc.perform(post("/v1/fasting/history/summary").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("userId", 3))))
                .andExpect(status().isOk());
        verify(fastingService).getSummaryByPeriod(3L, 1, null);
    }

    @Test
    @DisplayName("POST /achievements")
    void shouldGetAchievements() throws Exception {
        when(fastingService.getAchievements(3L)).thenReturn(ok);
        mockMvc.perform(post("/v1/fasting/achievements").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("userId", 3))))
                .andExpect(status().isOk());
        verify(fastingService).getAchievements(3L);
    }

    @Test
    @DisplayName("POST /water/today")
    void shouldGetWater() throws Exception {
        when(fastingService.getWaterToday(3L)).thenReturn(ok);
        mockMvc.perform(post("/v1/fasting/water/today").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("userId", 3))))
                .andExpect(status().isOk());
        verify(fastingService).getWaterToday(3L);
    }

    @Test
    @DisplayName("POST /water/add with glasses")
    void shouldAddWater() throws Exception {
        when(fastingService.addWater(3L, 2)).thenReturn(ok);
        mockMvc.perform(post("/v1/fasting/water/add").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("userId", 3, "glasses", 2))))
                .andExpect(status().isOk());
        verify(fastingService).addWater(3L, 2);
    }

    @Test
    @DisplayName("POST /water/add defaults to 1 glass")
    void shouldAddWaterDefault() throws Exception {
        when(fastingService.addWater(3L, 1)).thenReturn(ok);
        mockMvc.perform(post("/v1/fasting/water/add").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("userId", 3))))
                .andExpect(status().isOk());
        verify(fastingService).addWater(3L, 1);
    }

    @Test
    @DisplayName("POST /water/set-goal")
    void shouldSetGoal() throws Exception {
        when(fastingService.setWaterGoal(3L, 10)).thenReturn(ok);
        mockMvc.perform(post("/v1/fasting/water/set-goal").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("userId", 3, "goalGlasses", 10))))
                .andExpect(status().isOk());
        verify(fastingService).setWaterGoal(3L, 10);
    }

    // ===== explicit null optional fields (present-but-null branches) =====

    @Test
    @DisplayName("POST /session/start with explicit null startTime")
    void shouldStartNullTime() throws Exception {
        when(fastingService.startFasting(eq(3L), isNull())).thenReturn(ok);
        mockMvc.perform(post("/v1/fasting/session/start").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":3,\"startTime\":null}"))
                .andExpect(status().isOk());
        verify(fastingService).startFasting(3L, null);
    }

    @Test
    @DisplayName("POST /session/stop with explicit null endTime")
    void shouldStopNullTime() throws Exception {
        when(fastingService.stopFasting(eq(3L), isNull())).thenReturn(ok);
        mockMvc.perform(post("/v1/fasting/session/stop").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":3,\"endTime\":null}"))
                .andExpect(status().isOk());
        verify(fastingService).stopFasting(3L, null);
    }

    @Test
    @DisplayName("POST /session/edit with explicit null times")
    void shouldEditNullTimes() throws Exception {
        when(fastingService.editSession(eq(5L), isNull(), isNull())).thenReturn(ok);
        mockMvc.perform(post("/v1/fasting/session/edit").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":5,\"startTime\":null,\"endTime\":null}"))
                .andExpect(status().isOk());
        verify(fastingService).editSession(5L, null, null);
    }

    @Test
    @DisplayName("POST /history/by-period with explicit null referenceDate")
    void shouldHistoryNullRef() throws Exception {
        when(fastingService.getHistoryByPeriod(eq(3L), eq(1), isNull())).thenReturn(ok);
        mockMvc.perform(post("/v1/fasting/history/by-period").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":3,\"referenceDate\":null}"))
                .andExpect(status().isOk());
        verify(fastingService).getHistoryByPeriod(3L, 1, null);
    }

    @Test
    @DisplayName("POST /history/summary with explicit null referenceDate and budgetStartDay")
    void shouldSummaryNullRef() throws Exception {
        when(fastingService.getSummaryByPeriod(eq(3L), eq(15), isNull())).thenReturn(ok);
        mockMvc.perform(post("/v1/fasting/history/summary").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":3,\"budgetStartDay\":15,\"referenceDate\":null}"))
                .andExpect(status().isOk());
        verify(fastingService).getSummaryByPeriod(3L, 15, null);
    }
}

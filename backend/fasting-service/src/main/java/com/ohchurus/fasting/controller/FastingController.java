package com.ohchurus.fasting.controller;

import com.ohchurus.fasting.dto.output.ResultDTO;
import com.ohchurus.fasting.enums.PlanType;
import com.ohchurus.fasting.service.impl.FastingServiceImpl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/v1/fasting")
public class FastingController {

    private final FastingServiceImpl fastingService;

    public FastingController(FastingServiceImpl fastingService) {
        this.fastingService = fastingService;
    }

    // ===== PLAN CONFIG =====

    @PostMapping(value = "/plan/get", produces = "application/json")
    public ResponseEntity<ResultDTO> getPlan(@RequestBody Map<String, Object> body) {
        Long userId = Long.valueOf(body.get("userId").toString());
        return ResponseEntity.ok(fastingService.getPlanConfig(userId));
    }

    @PostMapping(value = "/plan/save", produces = "application/json")
    public ResponseEntity<ResultDTO> savePlan(@RequestBody Map<String, Object> body) {
        Long userId = Long.valueOf(body.get("userId").toString());
        PlanType planType = PlanType.valueOf(body.get("planType").toString());
        Integer fastingHours = body.containsKey("fastingHours") ? Integer.valueOf(body.get("fastingHours").toString()) : null;
        Integer eatingHours = body.containsKey("eatingHours") ? Integer.valueOf(body.get("eatingHours").toString()) : null;
        String suggestedStartTime = body.containsKey("suggestedStartTime") ? (String) body.get("suggestedStartTime") : null;
        Boolean reminders = body.containsKey("remindersEnabled") ? Boolean.valueOf(body.get("remindersEnabled").toString()) : null;
        return ResponseEntity.ok(fastingService.savePlanConfig(userId, planType, fastingHours, eatingHours, suggestedStartTime, reminders));
    }

    @PostMapping(value = "/plan/presets", produces = "application/json")
    public ResponseEntity<ResultDTO> getPresets() {
        return ResponseEntity.ok(fastingService.getPresets());
    }

    // ===== SESSIONS =====

    @PostMapping(value = "/session/start", produces = "application/json")
    public ResponseEntity<ResultDTO> startFasting(@RequestBody Map<String, Object> body) {
        Long userId = Long.valueOf(body.get("userId").toString());
        LocalDateTime customStart = body.containsKey("startTime") && body.get("startTime") != null
                ? LocalDateTime.parse(body.get("startTime").toString()) : null;
        return ResponseEntity.ok(fastingService.startFasting(userId, customStart));
    }

    @PostMapping(value = "/session/stop", produces = "application/json")
    public ResponseEntity<ResultDTO> stopFasting(@RequestBody Map<String, Object> body) {
        Long userId = Long.valueOf(body.get("userId").toString());
        LocalDateTime customEnd = body.containsKey("endTime") && body.get("endTime") != null
                ? LocalDateTime.parse(body.get("endTime").toString()) : null;
        return ResponseEntity.ok(fastingService.stopFasting(userId, customEnd));
    }

    @PostMapping(value = "/session/cancel", produces = "application/json")
    public ResponseEntity<ResultDTO> cancelFasting(@RequestBody Map<String, Object> body) {
        Long userId = Long.valueOf(body.get("userId").toString());
        return ResponseEntity.ok(fastingService.cancelFasting(userId));
    }

    @PostMapping(value = "/session/active", produces = "application/json")
    public ResponseEntity<ResultDTO> getActiveSession(@RequestBody Map<String, Object> body) {
        Long userId = Long.valueOf(body.get("userId").toString());
        return ResponseEntity.ok(fastingService.getActiveSession(userId));
    }

    @PostMapping(value = "/session/edit", produces = "application/json")
    public ResponseEntity<ResultDTO> editSession(@RequestBody Map<String, Object> body) {
        Long sessionId = Long.valueOf(body.get("sessionId").toString());
        LocalDateTime newStart = body.containsKey("startTime") && body.get("startTime") != null
                ? LocalDateTime.parse(body.get("startTime").toString()) : null;
        LocalDateTime newEnd = body.containsKey("endTime") && body.get("endTime") != null
                ? LocalDateTime.parse(body.get("endTime").toString()) : null;
        return ResponseEntity.ok(fastingService.editSession(sessionId, newStart, newEnd));
    }

    // ===== HISTORY =====

    @PostMapping(value = "/history/by-period", produces = "application/json")
    public ResponseEntity<ResultDTO> getHistory(@RequestBody Map<String, Object> body) {
        Long userId = Long.valueOf(body.get("userId").toString());
        Integer budgetStartDay = body.containsKey("budgetStartDay") ? Integer.valueOf(body.get("budgetStartDay").toString()) : 1;
        LocalDate referenceDate = body.containsKey("referenceDate") && body.get("referenceDate") != null
                ? LocalDate.parse(body.get("referenceDate").toString()) : null;
        return ResponseEntity.ok(fastingService.getHistoryByPeriod(userId, budgetStartDay, referenceDate));
    }

    @PostMapping(value = "/history/summary", produces = "application/json")
    public ResponseEntity<ResultDTO> getSummary(@RequestBody Map<String, Object> body) {
        Long userId = Long.valueOf(body.get("userId").toString());
        Integer budgetStartDay = body.containsKey("budgetStartDay") ? Integer.valueOf(body.get("budgetStartDay").toString()) : 1;
        LocalDate referenceDate = body.containsKey("referenceDate") && body.get("referenceDate") != null
                ? LocalDate.parse(body.get("referenceDate").toString()) : null;
        return ResponseEntity.ok(fastingService.getSummaryByPeriod(userId, budgetStartDay, referenceDate));
    }

    // ===== ACHIEVEMENTS =====

    @PostMapping(value = "/achievements", produces = "application/json")
    public ResponseEntity<ResultDTO> getAchievements(@RequestBody Map<String, Object> body) {
        Long userId = Long.valueOf(body.get("userId").toString());
        return ResponseEntity.ok(fastingService.getAchievements(userId));
    }

    // ===== WATER =====

    @PostMapping(value = "/water/today", produces = "application/json")
    public ResponseEntity<ResultDTO> getWaterToday(@RequestBody Map<String, Object> body) {
        Long userId = Long.valueOf(body.get("userId").toString());
        return ResponseEntity.ok(fastingService.getWaterToday(userId));
    }

    @PostMapping(value = "/water/add", produces = "application/json")
    public ResponseEntity<ResultDTO> addWater(@RequestBody Map<String, Object> body) {
        Long userId = Long.valueOf(body.get("userId").toString());
        int glasses = body.containsKey("glasses") ? Integer.parseInt(body.get("glasses").toString()) : 1;
        return ResponseEntity.ok(fastingService.addWater(userId, glasses));
    }

    @PostMapping(value = "/water/set-goal", produces = "application/json")
    public ResponseEntity<ResultDTO> setWaterGoal(@RequestBody Map<String, Object> body) {
        Long userId = Long.valueOf(body.get("userId").toString());
        int goal = Integer.parseInt(body.get("goalGlasses").toString());
        return ResponseEntity.ok(fastingService.setWaterGoal(userId, goal));
    }
}

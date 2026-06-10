package com.ohchurus.fasting.service.impl;

import com.ohchurus.fasting.dto.output.ResultDTO;
import com.ohchurus.fasting.entity.FastingPlanConfig;
import com.ohchurus.fasting.entity.FastingSession;
import com.ohchurus.fasting.enums.PlanType;
import com.ohchurus.fasting.enums.SessionStatus;
import com.ohchurus.fasting.repository.PlanConfigRepository;
import com.ohchurus.fasting.repository.SessionRepository;
import com.ohchurus.fasting.util.PeriodUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
public class FastingServiceImpl {

    private static final ZoneId ZONE_CO = ZoneId.of("America/Bogota");

    private final PlanConfigRepository planConfigRepository;
    private final SessionRepository sessionRepository;
    private final com.ohchurus.fasting.repository.AchievementRepository achievementRepository;
    private final com.ohchurus.fasting.repository.WaterLogRepository waterLogRepository;

    public FastingServiceImpl(PlanConfigRepository planConfigRepository, SessionRepository sessionRepository,
                               com.ohchurus.fasting.repository.AchievementRepository achievementRepository,
                               com.ohchurus.fasting.repository.WaterLogRepository waterLogRepository) {
        this.planConfigRepository = planConfigRepository;
        this.sessionRepository = sessionRepository;
        this.achievementRepository = achievementRepository;
        this.waterLogRepository = waterLogRepository;
    }

    // Achievement definitions
    private static final Object[][] BADGE_DEFS = {
        {"FIRST_FAST", "Primer Ayuno", "Completaste tu primer ayuno", "🌟"},
        {"STREAK_3", "Racha de 3", "3 dias consecutivos", "🔥"},
        {"STREAK_7", "Semana Perfecta", "7 dias consecutivos", "💪"},
        {"STREAK_14", "Dos Semanas", "14 dias consecutivos", "⭐"},
        {"STREAK_30", "Mes Completo", "30 dias consecutivos", "🏆"},
        {"HOURS_50", "50 Horas", "50 horas de ayuno acumuladas", "⏱️"},
        {"HOURS_100", "100 Horas", "100 horas de ayuno acumuladas", "🕐"},
        {"HOURS_500", "500 Horas", "500 horas de ayuno acumuladas", "🎖️"},
        {"SESSIONS_10", "10 Sesiones", "10 ayunos completados", "📊"},
        {"SESSIONS_50", "50 Sesiones", "50 ayunos completados", "🎯"},
        {"NIGHT_OWL", "Buho Nocturno", "Ayuno que cruzo la medianoche", "🦉"},
        {"EARLY_BIRD", "Madrugador", "Iniciaste ayuno antes de las 6am", "🐦"},
    };

    private LocalDateTime nowColombia() {
        return LocalDateTime.now(ZONE_CO);
    }

    // ===== PLAN CONFIG =====

    public ResultDTO getPlanConfig(Long userId) {
        return planConfigRepository.findByUserIdAndActiveTrue(userId)
                .map(p -> new ResultDTO(p))
                .orElse(new ResultDTO(false, "No plan configured", 404));
    }

    public ResultDTO savePlanConfig(Long userId, PlanType planType, Integer fastingHours, Integer eatingHours,
                                     String suggestedStartTime, Boolean remindersEnabled) {
        try {
            int fh = planType != PlanType.CUSTOM ? planType.fastingHours : (fastingHours != null ? fastingHours : 16);
            int eh = planType != PlanType.CUSTOM ? planType.eatingHours : (eatingHours != null ? eatingHours : 8);

            if (fh + eh != 24) {
                return new ResultDTO(false, "Fasting + eating hours must equal 24", 400);
            }

            planConfigRepository.findByUserIdAndActiveTrue(userId).ifPresent(existing -> {
                existing.setActive(false);
                planConfigRepository.save(existing);
            });

            FastingPlanConfig config = FastingPlanConfig.builder()
                    .userId(userId).planType(planType).fastingHours(fh).eatingHours(eh)
                    .suggestedStartTime(suggestedStartTime)
                    .remindersEnabled(remindersEnabled != null ? remindersEnabled : false)
                    .active(true).build();

            return new ResultDTO(planConfigRepository.save(config));
        } catch (Exception e) {
            log.error("Error saving plan config: {}", e.getMessage(), e);
            return new ResultDTO(false, "Error saving plan config", 500);
        }
    }

    public ResultDTO getPresets() {
        List<Map<String, Object>> presets = Arrays.stream(PlanType.values())
                .filter(p -> p != PlanType.CUSTOM)
                .map(p -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("planType", p.name());
                    map.put("label", p.fastingHours + ":" + p.eatingHours);
                    map.put("fastingHours", p.fastingHours);
                    map.put("eatingHours", p.eatingHours);
                    return map;
                }).collect(Collectors.toList());
        return new ResultDTO(presets);
    }

    // ===== SESSIONS =====

    public ResultDTO startFasting(Long userId, LocalDateTime customStartTime) {
        try {
            Optional<FastingSession> active = sessionRepository.findByUserIdAndStatusAndActiveTrue(userId, SessionStatus.IN_PROGRESS);
            if (active.isPresent()) {
                return new ResultDTO(false, "Already have an active fasting session", 400);
            }

            Optional<FastingPlanConfig> planOpt = planConfigRepository.findByUserIdAndActiveTrue(userId);
            if (planOpt.isEmpty()) {
                return new ResultDTO(false, "No plan configured. Configure a plan first.", 404);
            }

            FastingPlanConfig plan = planOpt.get();
            LocalDateTime startTime = customStartTime != null ? customStartTime : nowColombia();
            LocalDateTime targetEnd = startTime.plusHours(plan.getFastingHours());

            FastingSession session = FastingSession.builder()
                    .userId(userId).planConfigId(plan.getId())
                    .startTime(startTime).targetEndTime(targetEnd)
                    .fastingHours(plan.getFastingHours())
                    .status(SessionStatus.IN_PROGRESS).active(true).build();

            FastingSession saved = sessionRepository.save(session);
            log.info("Fasting started: userId={}, start={}, target={}", userId, startTime, targetEnd);
            return new ResultDTO(saved);
        } catch (Exception e) {
            log.error("Error starting fasting: {}", e.getMessage(), e);
            return new ResultDTO(false, "Error starting fasting", 500);
        }
    }

    public ResultDTO stopFasting(Long userId, LocalDateTime customEndTime) {
        try {
            Optional<FastingSession> activeOpt = sessionRepository.findByUserIdAndStatusAndActiveTrue(userId, SessionStatus.IN_PROGRESS);
            if (activeOpt.isEmpty()) {
                return new ResultDTO(false, "No active fasting session", 404);
            }

            FastingSession session = activeOpt.get();
            LocalDateTime endTime = customEndTime != null ? customEndTime : nowColombia();
            session.setActualEndTime(endTime);

            if (endTime.isAfter(session.getTargetEndTime()) || endTime.isEqual(session.getTargetEndTime())) {
                session.setStatus(SessionStatus.COMPLETED);
            } else {
                session.setStatus(SessionStatus.INCOMPLETE);
            }

            FastingSession saved = sessionRepository.save(session);
            log.info("Fasting stopped: userId={}, status={}, endTime={}", userId, saved.getStatus(), endTime);

            // Check achievements
            checkAndGrantAchievements(userId, saved);

            return new ResultDTO(saved);
        } catch (Exception e) {
            log.error("Error stopping fasting: {}", e.getMessage(), e);
            return new ResultDTO(false, "Error stopping fasting", 500);
        }
    }

    public ResultDTO cancelFasting(Long userId) {
        try {
            Optional<FastingSession> activeOpt = sessionRepository.findByUserIdAndStatusAndActiveTrue(userId, SessionStatus.IN_PROGRESS);
            if (activeOpt.isEmpty()) {
                return new ResultDTO(false, "No active fasting session", 404);
            }
            FastingSession session = activeOpt.get();
            session.setStatus(SessionStatus.CANCELLED);
            session.setActualEndTime(nowColombia());
            sessionRepository.save(session);
            return new ResultDTO(true, "Fasting cancelled", 0);
        } catch (Exception e) {
            return new ResultDTO(false, "Error cancelling fasting", 500);
        }
    }

    @Transactional(readOnly = true)
    public ResultDTO getActiveSession(Long userId) {
        return sessionRepository.findByUserIdAndStatusAndActiveTrue(userId, SessionStatus.IN_PROGRESS)
                .map(s -> new ResultDTO(s))
                .orElse(new ResultDTO(null));
    }

    // ===== EDIT SESSION =====

    public ResultDTO editSession(Long sessionId, LocalDateTime newStartTime, LocalDateTime newEndTime) {
        try {
            Optional<FastingSession> opt = sessionRepository.findById(sessionId);
            if (opt.isEmpty()) return new ResultDTO(false, "Session not found", 404);

            FastingSession session = opt.get();
            if (!Boolean.TRUE.equals(session.getActive())) return new ResultDTO(false, "Session not active", 400);
            if (newStartTime != null) {
                session.setStartTime(newStartTime);
                session.setTargetEndTime(newStartTime.plusHours(session.getFastingHours()));
            }
            if (newEndTime != null) {
                session.setActualEndTime(newEndTime);
                LocalDateTime target = session.getTargetEndTime();
                if (newEndTime.isAfter(target) || newEndTime.isEqual(target)) {
                    session.setStatus(SessionStatus.COMPLETED);
                } else {
                    session.setStatus(SessionStatus.INCOMPLETE);
                }
            }

            return new ResultDTO(sessionRepository.save(session));
        } catch (Exception e) {
            return new ResultDTO(false, "Error editing session", 500);
        }
    }

    // ===== HISTORY =====

    @Transactional(readOnly = true)
    public ResultDTO getHistoryByPeriod(Long userId, Integer budgetStartDay, LocalDate referenceDate) {
        try {
            LocalDate ref = referenceDate != null ? referenceDate : LocalDate.now(ZONE_CO);
            LocalDate periodStart = PeriodUtils.getStartOfPeriod(budgetStartDay, ref);
            LocalDate periodEnd = PeriodUtils.getEndOfPeriod(budgetStartDay, periodStart);

            LocalDateTime start = periodStart.atStartOfDay();
            LocalDateTime end = periodEnd.atTime(LocalTime.MAX);

            List<FastingSession> sessions = sessionRepository
                    .findByUserIdAndStartTimeBetweenAndActiveTrueOrderByStartTimeAsc(userId, start, end);

            // SOLO dias con registros, no todos los dias del ciclo
            List<Map<String, Object>> days = sessions.stream().map(s -> {
                Map<String, Object> dayMap = new LinkedHashMap<>();
                dayMap.put("id", s.getId());
                dayMap.put("date", s.getStartTime().toLocalDate().toString());
                dayMap.put("planHours", s.getFastingHours());
                dayMap.put("startTime", s.getStartTime().toString());
                dayMap.put("targetEndTime", s.getTargetEndTime().toString());
                dayMap.put("actualEndTime", s.getActualEndTime() != null ? s.getActualEndTime().toString() : null);
                dayMap.put("status", s.getStatus().name());

                if (s.getActualEndTime() != null) {
                    long minutes = Duration.between(s.getStartTime().atZone(ZONE_CO), s.getActualEndTime().atZone(ZONE_CO)).toMinutes();
                    dayMap.put("durationMinutes", minutes);
                    dayMap.put("durationFormatted", String.format("%dh %dm", minutes / 60, minutes % 60));
                }
                return dayMap;
            }).collect(Collectors.toList());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("periodStart", periodStart.toString());
            result.put("periodEnd", periodEnd.toString());
            result.put("days", days);
            return new ResultDTO(result);
        } catch (Exception e) {
            log.error("Error getting history: {}", e.getMessage(), e);
            return new ResultDTO(false, "Error getting history", 500);
        }
    }

    @Transactional(readOnly = true)
    public ResultDTO getSummaryByPeriod(Long userId, Integer budgetStartDay, LocalDate referenceDate) {
        try {
            LocalDate ref = referenceDate != null ? referenceDate : LocalDate.now(ZONE_CO);
            LocalDate periodStart = PeriodUtils.getStartOfPeriod(budgetStartDay, ref);
            LocalDate periodEnd = PeriodUtils.getEndOfPeriod(budgetStartDay, periodStart);

            LocalDateTime start = periodStart.atStartOfDay();
            LocalDateTime end = periodEnd.atTime(LocalTime.MAX);

            List<FastingSession> sessions = sessionRepository
                    .findByUserIdAndStartTimeBetweenAndActiveTrueOrderByStartTimeAsc(userId, start, end);

            int completed = (int) sessions.stream().filter(s -> s.getStatus() == SessionStatus.COMPLETED).count();
            int incomplete = (int) sessions.stream().filter(s -> s.getStatus() == SessionStatus.INCOMPLETE).count();
            int inProgress = (int) sessions.stream().filter(s -> s.getStatus() == SessionStatus.IN_PROGRESS).count();
            int totalDays = (int) (periodEnd.toEpochDay() - periodStart.toEpochDay()) + 1;
            double complianceRate = totalDays > 0 ? (completed * 100.0 / totalDays) : 0;

            // Streaks
            int currentStreak = 0, bestStreak = 0, tempStreak = 0;
            Set<LocalDate> completedDates = sessions.stream()
                    .filter(s -> s.getStatus() == SessionStatus.COMPLETED)
                    .map(s -> s.getStartTime().toLocalDate())
                    .collect(Collectors.toSet());

            LocalDate d = periodStart;
            LocalDate today = LocalDate.now(ZONE_CO);
            while (!d.isAfter(periodEnd) && !d.isAfter(today)) {
                if (completedDates.contains(d)) { tempStreak++; bestStreak = Math.max(bestStreak, tempStreak); }
                else { tempStreak = 0; }
                d = d.plusDays(1);
            }
            currentStreak = tempStreak;

            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("periodStart", periodStart.toString());
            summary.put("periodEnd", periodEnd.toString());
            summary.put("totalDays", totalDays);
            summary.put("totalSessions", sessions.size());
            summary.put("completed", completed);
            summary.put("incomplete", incomplete);
            summary.put("inProgress", inProgress);
            summary.put("complianceRate", Math.round(complianceRate * 10.0) / 10.0);
            summary.put("currentStreak", currentStreak);
            summary.put("bestStreak", bestStreak);

            return new ResultDTO(summary);
        } catch (Exception e) {
            log.error("Error getting summary: {}", e.getMessage(), e);
            return new ResultDTO(false, "Error getting summary", 500);
        }
    }

    // ===== ACHIEVEMENTS =====

    @Transactional(readOnly = true)
    public ResultDTO getAchievements(Long userId) {
        List<com.ohchurus.fasting.entity.Achievement> unlocked = achievementRepository.findByUserIdOrderByUnlockedAtDesc(userId);

        List<Map<String, Object>> all = new ArrayList<>();
        for (Object[] def : BADGE_DEFS) {
            Map<String, Object> badge = new LinkedHashMap<>();
            String code = (String) def[0];
            badge.put("code", code);
            badge.put("name", def[1]);
            badge.put("description", def[2]);
            badge.put("icon", def[3]);
            badge.put("unlocked", unlocked.stream().anyMatch(a -> a.getCode().equals(code)));
            unlocked.stream().filter(a -> a.getCode().equals(code)).findFirst()
                    .ifPresent(a -> badge.put("unlockedAt", a.getUnlockedAt().toString()));
            all.add(badge);
        }
        return new ResultDTO(all);
    }

    private void checkAndGrantAchievements(Long userId, FastingSession session) {
        try {
            List<FastingSession> allCompleted = sessionRepository.findByUserIdAndActiveTrueOrderByStartTimeDesc(userId)
                    .stream().filter(s -> s.getStatus() == SessionStatus.COMPLETED).collect(Collectors.toList());

            int completedCount = allCompleted.size();
            long totalMinutes = allCompleted.stream()
                    .filter(s -> s.getActualEndTime() != null)
                    .mapToLong(s -> Duration.between(s.getStartTime().atZone(ZONE_CO), s.getActualEndTime().atZone(ZONE_CO)).toMinutes())
                    .sum();
            long totalHours = totalMinutes / 60;

            // First fast
            if (completedCount >= 1) grant(userId, "FIRST_FAST");

            // Session count
            if (completedCount >= 10) grant(userId, "SESSIONS_10");
            if (completedCount >= 50) grant(userId, "SESSIONS_50");

            // Hours
            if (totalHours >= 50) grant(userId, "HOURS_50");
            if (totalHours >= 100) grant(userId, "HOURS_100");
            if (totalHours >= 500) grant(userId, "HOURS_500");

            // Streaks - calculate from completed dates
            Set<LocalDate> dates = allCompleted.stream()
                    .map(s -> s.getStartTime().toLocalDate())
                    .collect(Collectors.toCollection(java.util.TreeSet::new));
            int streak = 0, maxStreak = 0, tempStreak = 0;
            LocalDate prev = null;
            for (LocalDate d : new java.util.TreeSet<>(dates)) {
                if (prev != null && d.equals(prev.plusDays(1))) { tempStreak++; }
                else { tempStreak = 1; }
                maxStreak = Math.max(maxStreak, tempStreak);
                prev = d;
            }
            if (maxStreak >= 3) grant(userId, "STREAK_3");
            if (maxStreak >= 7) grant(userId, "STREAK_7");
            if (maxStreak >= 14) grant(userId, "STREAK_14");
            if (maxStreak >= 30) grant(userId, "STREAK_30");

            // Special
            if (session.getStartTime().getHour() < 6) grant(userId, "EARLY_BIRD");
            if (session.getActualEndTime() != null &&
                !session.getStartTime().toLocalDate().equals(session.getActualEndTime().toLocalDate())) {
                grant(userId, "NIGHT_OWL");
            }
        } catch (Exception e) {
            log.warn("Error checking achievements: {}", e.getMessage());
        }
    }

    private void grant(Long userId, String code) {
        if (achievementRepository.existsByUserIdAndCode(userId, code)) return;
        for (Object[] def : BADGE_DEFS) {
            if (def[0].equals(code)) {
                achievementRepository.save(com.ohchurus.fasting.entity.Achievement.builder()
                        .userId(userId).code(code)
                        .name((String) def[1]).description((String) def[2]).icon((String) def[3])
                        .build());
                log.info("Achievement unlocked: userId={}, code={}", userId, code);
                return;
            }
        }
    }

    // ===== WATER TRACKER =====

    public ResultDTO getWaterToday(Long userId) {
        LocalDate today = LocalDate.now(ZONE_CO);
        com.ohchurus.fasting.entity.WaterLog log = waterLogRepository.findByUserIdAndLogDate(userId, today)
                .orElse(com.ohchurus.fasting.entity.WaterLog.builder()
                        .userId(userId).logDate(today).glasses(0).goalGlasses(8).build());
        return new ResultDTO(log);
    }

    public ResultDTO addWater(Long userId, int glasses) {
        LocalDate today = LocalDate.now(ZONE_CO);
        com.ohchurus.fasting.entity.WaterLog waterLog = waterLogRepository.findByUserIdAndLogDate(userId, today)
                .orElse(com.ohchurus.fasting.entity.WaterLog.builder()
                        .userId(userId).logDate(today).glasses(0).goalGlasses(8).build());
        waterLog.setGlasses(Math.max(0, waterLog.getGlasses() + glasses));
        com.ohchurus.fasting.entity.WaterLog saved = waterLogRepository.save(waterLog);
        return new ResultDTO(saved);
    }

    public ResultDTO setWaterGoal(Long userId, int goal) {
        if (goal < 1 || goal > 20) {
            return new ResultDTO(false, "Goal must be between 1 and 20", 400);
        }
        LocalDate today = LocalDate.now(ZONE_CO);
        com.ohchurus.fasting.entity.WaterLog waterLog = waterLogRepository.findByUserIdAndLogDate(userId, today)
                .orElse(com.ohchurus.fasting.entity.WaterLog.builder()
                        .userId(userId).logDate(today).glasses(0).goalGlasses(goal).build());
        waterLog.setGoalGlasses(goal);
        return new ResultDTO(waterLogRepository.save(waterLog));
    }
}

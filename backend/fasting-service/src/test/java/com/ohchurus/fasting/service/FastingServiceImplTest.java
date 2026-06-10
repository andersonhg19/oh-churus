package com.ohchurus.fasting.service;

import com.ohchurus.fasting.dto.output.ResultDTO;
import com.ohchurus.fasting.entity.Achievement;
import com.ohchurus.fasting.entity.FastingPlanConfig;
import com.ohchurus.fasting.entity.FastingSession;
import com.ohchurus.fasting.entity.WaterLog;
import com.ohchurus.fasting.enums.PlanType;
import com.ohchurus.fasting.enums.SessionStatus;
import com.ohchurus.fasting.repository.AchievementRepository;
import com.ohchurus.fasting.repository.PlanConfigRepository;
import com.ohchurus.fasting.repository.SessionRepository;
import com.ohchurus.fasting.repository.WaterLogRepository;
import com.ohchurus.fasting.service.impl.FastingServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FastingServiceImpl")
class FastingServiceImplTest {

    @Mock
    private PlanConfigRepository planConfigRepository;
    @Mock
    private SessionRepository sessionRepository;
    @Mock
    private AchievementRepository achievementRepository;
    @Mock
    private WaterLogRepository waterLogRepository;

    @InjectMocks
    private FastingServiceImpl service;

    private static final Long USER_ID = 3L;

    private FastingPlanConfig plan() {
        return FastingPlanConfig.builder()
                .id(1L).userId(USER_ID).planType(PlanType.PLAN_16_8)
                .fastingHours(16).eatingHours(8).active(true).build();
    }

    private FastingSession activeSession(LocalDateTime start, LocalDateTime target) {
        return FastingSession.builder()
                .id(1L).userId(USER_ID).planConfigId(1L)
                .startTime(start).targetEndTime(target).fastingHours(16)
                .status(SessionStatus.IN_PROGRESS).active(true).build();
    }

    // ===================== PLAN CONFIG =====================

    @Nested
    @DisplayName("getPlanConfig")
    class GetPlanConfigTests {
        @Test
        @DisplayName("Should return plan when configured")
        void shouldReturnPlan() {
            when(planConfigRepository.findByUserIdAndActiveTrue(USER_ID)).thenReturn(Optional.of(plan()));
            ResultDTO r = service.getPlanConfig(USER_ID);
            assertTrue(r.isCorrect());
        }

        @Test
        @DisplayName("Should return 404 when no plan configured")
        void shouldReturnNotFound() {
            when(planConfigRepository.findByUserIdAndActiveTrue(USER_ID)).thenReturn(Optional.empty());
            ResultDTO r = service.getPlanConfig(USER_ID);
            assertFalse(r.isCorrect());
            assertEquals(404, r.getErrorCode());
        }
    }

    @Nested
    @DisplayName("savePlanConfig")
    class SavePlanConfigTests {
        @Test
        @DisplayName("Should save preset plan deactivating previous one")
        void shouldSavePreset() {
            FastingPlanConfig existing = plan();
            when(planConfigRepository.findByUserIdAndActiveTrue(USER_ID)).thenReturn(Optional.of(existing));
            when(planConfigRepository.save(any(FastingPlanConfig.class))).thenAnswer(i -> i.getArgument(0));

            ResultDTO r = service.savePlanConfig(USER_ID, PlanType.PLAN_18_6, null, null, "20:00", true);

            assertTrue(r.isCorrect());
            FastingPlanConfig saved = (FastingPlanConfig) r.getObject();
            assertEquals(18, saved.getFastingHours());
            assertEquals(6, saved.getEatingHours());
            assertFalse(existing.getActive());
        }

        @Test
        @DisplayName("Should save CUSTOM plan with provided hours")
        void shouldSaveCustom() {
            when(planConfigRepository.findByUserIdAndActiveTrue(USER_ID)).thenReturn(Optional.empty());
            when(planConfigRepository.save(any(FastingPlanConfig.class))).thenAnswer(i -> i.getArgument(0));

            ResultDTO r = service.savePlanConfig(USER_ID, PlanType.CUSTOM, 20, 4, null, null);

            assertTrue(r.isCorrect());
            FastingPlanConfig saved = (FastingPlanConfig) r.getObject();
            assertEquals(20, saved.getFastingHours());
            assertFalse(saved.getRemindersEnabled());
        }

        @Test
        @DisplayName("Should default CUSTOM hours to 16/8 when null")
        void shouldDefaultCustomHours() {
            when(planConfigRepository.findByUserIdAndActiveTrue(USER_ID)).thenReturn(Optional.empty());
            when(planConfigRepository.save(any(FastingPlanConfig.class))).thenAnswer(i -> i.getArgument(0));

            ResultDTO r = service.savePlanConfig(USER_ID, PlanType.CUSTOM, null, null, null, null);

            assertTrue(r.isCorrect());
            FastingPlanConfig saved = (FastingPlanConfig) r.getObject();
            assertEquals(16, saved.getFastingHours());
            assertEquals(8, saved.getEatingHours());
        }

        @Test
        @DisplayName("Should reject when fasting + eating hours != 24")
        void shouldRejectInvalidHours() {
            ResultDTO r = service.savePlanConfig(USER_ID, PlanType.CUSTOM, 20, 8, null, null);
            assertFalse(r.isCorrect());
            assertEquals(400, r.getErrorCode());
        }

        @Test
        @DisplayName("Should return 500 when persistence fails")
        void shouldHandleException() {
            when(planConfigRepository.findByUserIdAndActiveTrue(USER_ID)).thenReturn(Optional.empty());
            when(planConfigRepository.save(any(FastingPlanConfig.class))).thenThrow(new RuntimeException("DB"));

            ResultDTO r = service.savePlanConfig(USER_ID, PlanType.PLAN_16_8, null, null, null, null);
            assertFalse(r.isCorrect());
            assertEquals(500, r.getErrorCode());
        }
    }

    @Nested
    @DisplayName("getPresets")
    class GetPresetsTests {
        @Test
        @DisplayName("Should return all presets except CUSTOM")
        @SuppressWarnings("unchecked")
        void shouldReturnPresets() {
            ResultDTO r = service.getPresets();
            assertTrue(r.isCorrect());
            List<Map<String, Object>> presets = (List<Map<String, Object>>) r.getObject();
            assertEquals(PlanType.values().length - 1, presets.size());
            assertTrue(presets.stream().noneMatch(p -> "CUSTOM".equals(p.get("planType"))));
        }
    }

    // ===================== SESSIONS =====================

    @Nested
    @DisplayName("startFasting")
    class StartFastingTests {
        @Test
        @DisplayName("Should start a session with custom start time")
        void shouldStartCustom() {
            when(sessionRepository.findByUserIdAndStatusAndActiveTrue(USER_ID, SessionStatus.IN_PROGRESS))
                    .thenReturn(Optional.empty());
            when(planConfigRepository.findByUserIdAndActiveTrue(USER_ID)).thenReturn(Optional.of(plan()));
            when(sessionRepository.save(any(FastingSession.class))).thenAnswer(i -> i.getArgument(0));

            LocalDateTime start = LocalDateTime.of(2026, 3, 15, 20, 0);
            ResultDTO r = service.startFasting(USER_ID, start);

            assertTrue(r.isCorrect());
            FastingSession s = (FastingSession) r.getObject();
            assertEquals(start, s.getStartTime());
            assertEquals(start.plusHours(16), s.getTargetEndTime());
            assertEquals(SessionStatus.IN_PROGRESS, s.getStatus());
        }

        @Test
        @DisplayName("Should start a session using current time when custom is null")
        void shouldStartNow() {
            when(sessionRepository.findByUserIdAndStatusAndActiveTrue(USER_ID, SessionStatus.IN_PROGRESS))
                    .thenReturn(Optional.empty());
            when(planConfigRepository.findByUserIdAndActiveTrue(USER_ID)).thenReturn(Optional.of(plan()));
            when(sessionRepository.save(any(FastingSession.class))).thenAnswer(i -> i.getArgument(0));

            ResultDTO r = service.startFasting(USER_ID, null);
            assertTrue(r.isCorrect());
            assertNotNull(((FastingSession) r.getObject()).getStartTime());
        }

        @Test
        @DisplayName("Should reject when a session is already active")
        void shouldRejectActive() {
            when(sessionRepository.findByUserIdAndStatusAndActiveTrue(USER_ID, SessionStatus.IN_PROGRESS))
                    .thenReturn(Optional.of(activeSession(LocalDateTime.now(), LocalDateTime.now().plusHours(16))));

            ResultDTO r = service.startFasting(USER_ID, null);
            assertFalse(r.isCorrect());
            assertEquals(400, r.getErrorCode());
        }

        @Test
        @DisplayName("Should return 404 when no plan configured")
        void shouldRejectNoPlan() {
            when(sessionRepository.findByUserIdAndStatusAndActiveTrue(USER_ID, SessionStatus.IN_PROGRESS))
                    .thenReturn(Optional.empty());
            when(planConfigRepository.findByUserIdAndActiveTrue(USER_ID)).thenReturn(Optional.empty());

            ResultDTO r = service.startFasting(USER_ID, null);
            assertFalse(r.isCorrect());
            assertEquals(404, r.getErrorCode());
        }

        @Test
        @DisplayName("Should return 500 when persistence fails")
        void shouldHandleException() {
            when(sessionRepository.findByUserIdAndStatusAndActiveTrue(USER_ID, SessionStatus.IN_PROGRESS))
                    .thenThrow(new RuntimeException("DB"));

            ResultDTO r = service.startFasting(USER_ID, null);
            assertFalse(r.isCorrect());
            assertEquals(500, r.getErrorCode());
        }
    }

    @Nested
    @DisplayName("stopFasting")
    class StopFastingTests {
        @Test
        @DisplayName("Should complete session when end >= target and grant achievements")
        void shouldCompleteAndGrant() {
            LocalDateTime start = LocalDateTime.of(2026, 3, 15, 5, 0); // early bird (<6)
            LocalDateTime target = start.plusHours(16);
            FastingSession session = activeSession(start, target);

            when(sessionRepository.findByUserIdAndStatusAndActiveTrue(USER_ID, SessionStatus.IN_PROGRESS))
                    .thenReturn(Optional.of(session));
            when(sessionRepository.save(any(FastingSession.class))).thenAnswer(i -> i.getArgument(0));

            // 50 completed sessions on consecutive days, 10h each => 500h, streak 50
            List<FastingSession> history = new ArrayList<>();
            LocalDate d = LocalDate.of(2026, 1, 1);
            for (int i = 0; i < 50; i++) {
                LocalDateTime s = d.plusDays(i).atTime(8, 0);
                history.add(FastingSession.builder()
                        .id((long) (i + 100)).userId(USER_ID)
                        .startTime(s).targetEndTime(s.plusHours(10)).actualEndTime(s.plusHours(10))
                        .fastingHours(10).status(SessionStatus.COMPLETED).active(true).build());
            }
            when(sessionRepository.findByUserIdAndActiveTrueOrderByStartTimeDesc(USER_ID)).thenReturn(history);
            when(achievementRepository.existsByUserIdAndCode(eq(USER_ID), anyString())).thenReturn(false);

            LocalDateTime end = start.plusDays(1).plusHours(2); // crosses midnight => night owl, after target
            ResultDTO r = service.stopFasting(USER_ID, end);

            assertTrue(r.isCorrect());
            assertEquals(SessionStatus.COMPLETED, session.getStatus());
            // many badges granted (first, sessions 10/50, hours 50/100/500, streaks, early bird, night owl)
            verify(achievementRepository, atLeast(8)).save(any(Achievement.class));
        }

        @Test
        @DisplayName("Should mark INCOMPLETE when end < target")
        void shouldMarkIncomplete() {
            LocalDateTime start = LocalDateTime.of(2026, 3, 15, 10, 0);
            FastingSession session = activeSession(start, start.plusHours(16));
            when(sessionRepository.findByUserIdAndStatusAndActiveTrue(USER_ID, SessionStatus.IN_PROGRESS))
                    .thenReturn(Optional.of(session));
            when(sessionRepository.save(any(FastingSession.class))).thenAnswer(i -> i.getArgument(0));
            when(sessionRepository.findByUserIdAndActiveTrueOrderByStartTimeDesc(USER_ID))
                    .thenReturn(Collections.emptyList());

            ResultDTO r = service.stopFasting(USER_ID, start.plusHours(2));
            assertTrue(r.isCorrect());
            assertEquals(SessionStatus.INCOMPLETE, session.getStatus());
        }

        @Test
        @DisplayName("Should not fail stop when achievement check throws")
        void shouldSwallowAchievementError() {
            LocalDateTime start = LocalDateTime.of(2026, 3, 15, 10, 0);
            FastingSession session = activeSession(start, start.plusHours(16));
            when(sessionRepository.findByUserIdAndStatusAndActiveTrue(USER_ID, SessionStatus.IN_PROGRESS))
                    .thenReturn(Optional.of(session));
            when(sessionRepository.save(any(FastingSession.class))).thenAnswer(i -> i.getArgument(0));
            when(sessionRepository.findByUserIdAndActiveTrueOrderByStartTimeDesc(USER_ID))
                    .thenThrow(new RuntimeException("boom"));

            ResultDTO r = service.stopFasting(USER_ID, start.plusHours(20));
            assertTrue(r.isCorrect());
        }

        @Test
        @DisplayName("Should return 404 when no active session")
        void shouldReturnNotFound() {
            when(sessionRepository.findByUserIdAndStatusAndActiveTrue(USER_ID, SessionStatus.IN_PROGRESS))
                    .thenReturn(Optional.empty());
            ResultDTO r = service.stopFasting(USER_ID, null);
            assertFalse(r.isCorrect());
            assertEquals(404, r.getErrorCode());
        }

        @Test
        @DisplayName("Should return 500 when lookup fails")
        void shouldHandleException() {
            when(sessionRepository.findByUserIdAndStatusAndActiveTrue(USER_ID, SessionStatus.IN_PROGRESS))
                    .thenThrow(new RuntimeException("DB"));
            ResultDTO r = service.stopFasting(USER_ID, null);
            assertFalse(r.isCorrect());
            assertEquals(500, r.getErrorCode());
        }
    }

    @Nested
    @DisplayName("cancelFasting")
    class CancelFastingTests {
        @Test
        @DisplayName("Should cancel active session")
        void shouldCancel() {
            FastingSession session = activeSession(LocalDateTime.now(), LocalDateTime.now().plusHours(16));
            when(sessionRepository.findByUserIdAndStatusAndActiveTrue(USER_ID, SessionStatus.IN_PROGRESS))
                    .thenReturn(Optional.of(session));

            ResultDTO r = service.cancelFasting(USER_ID);
            assertTrue(r.isCorrect());
            assertEquals(SessionStatus.CANCELLED, session.getStatus());
        }

        @Test
        @DisplayName("Should return 404 when no active session")
        void shouldReturnNotFound() {
            when(sessionRepository.findByUserIdAndStatusAndActiveTrue(USER_ID, SessionStatus.IN_PROGRESS))
                    .thenReturn(Optional.empty());
            ResultDTO r = service.cancelFasting(USER_ID);
            assertFalse(r.isCorrect());
            assertEquals(404, r.getErrorCode());
        }

        @Test
        @DisplayName("Should return 500 when lookup fails")
        void shouldHandleException() {
            when(sessionRepository.findByUserIdAndStatusAndActiveTrue(USER_ID, SessionStatus.IN_PROGRESS))
                    .thenThrow(new RuntimeException("DB"));
            ResultDTO r = service.cancelFasting(USER_ID);
            assertFalse(r.isCorrect());
            assertEquals(500, r.getErrorCode());
        }
    }

    @Nested
    @DisplayName("getActiveSession")
    class GetActiveSessionTests {
        @Test
        @DisplayName("Should return active session when present")
        void shouldReturnActive() {
            when(sessionRepository.findByUserIdAndStatusAndActiveTrue(USER_ID, SessionStatus.IN_PROGRESS))
                    .thenReturn(Optional.of(activeSession(LocalDateTime.now(), LocalDateTime.now().plusHours(16))));
            ResultDTO r = service.getActiveSession(USER_ID);
            assertTrue(r.isCorrect());
            assertNotNull(r.getObject());
        }

        @Test
        @DisplayName("Should return null object when no active session")
        void shouldReturnNull() {
            when(sessionRepository.findByUserIdAndStatusAndActiveTrue(USER_ID, SessionStatus.IN_PROGRESS))
                    .thenReturn(Optional.empty());
            ResultDTO r = service.getActiveSession(USER_ID);
            assertTrue(r.isCorrect());
            assertNull(r.getObject());
        }
    }

    @Nested
    @DisplayName("editSession")
    class EditSessionTests {
        @Test
        @DisplayName("Should update start time and recompute target")
        void shouldUpdateStart() {
            FastingSession session = activeSession(LocalDateTime.of(2026, 3, 15, 8, 0),
                    LocalDateTime.of(2026, 3, 16, 0, 0));
            when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
            when(sessionRepository.save(any(FastingSession.class))).thenAnswer(i -> i.getArgument(0));

            LocalDateTime newStart = LocalDateTime.of(2026, 3, 15, 10, 0);
            ResultDTO r = service.editSession(1L, newStart, null);

            assertTrue(r.isCorrect());
            assertEquals(newStart, session.getStartTime());
            assertEquals(newStart.plusHours(16), session.getTargetEndTime());
        }

        @Test
        @DisplayName("Should set COMPLETED when new end >= target")
        void shouldSetCompleted() {
            FastingSession session = activeSession(LocalDateTime.of(2026, 3, 15, 8, 0),
                    LocalDateTime.of(2026, 3, 16, 0, 0));
            when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
            when(sessionRepository.save(any(FastingSession.class))).thenAnswer(i -> i.getArgument(0));

            ResultDTO r = service.editSession(1L, null, LocalDateTime.of(2026, 3, 16, 1, 0));
            assertTrue(r.isCorrect());
            assertEquals(SessionStatus.COMPLETED, session.getStatus());
        }

        @Test
        @DisplayName("Should set INCOMPLETE when new end < target")
        void shouldSetIncomplete() {
            FastingSession session = activeSession(LocalDateTime.of(2026, 3, 15, 8, 0),
                    LocalDateTime.of(2026, 3, 16, 0, 0));
            when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
            when(sessionRepository.save(any(FastingSession.class))).thenAnswer(i -> i.getArgument(0));

            ResultDTO r = service.editSession(1L, null, LocalDateTime.of(2026, 3, 15, 12, 0));
            assertTrue(r.isCorrect());
            assertEquals(SessionStatus.INCOMPLETE, session.getStatus());
        }

        @Test
        @DisplayName("Should return 404 when session not found")
        void shouldReturnNotFound() {
            when(sessionRepository.findById(1L)).thenReturn(Optional.empty());
            ResultDTO r = service.editSession(1L, null, null);
            assertFalse(r.isCorrect());
            assertEquals(404, r.getErrorCode());
        }

        @Test
        @DisplayName("Should return 400 when session is not active")
        void shouldRejectInactive() {
            FastingSession session = activeSession(LocalDateTime.now(), LocalDateTime.now().plusHours(16));
            session.setActive(false);
            when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
            ResultDTO r = service.editSession(1L, null, null);
            assertFalse(r.isCorrect());
            assertEquals(400, r.getErrorCode());
        }

        @Test
        @DisplayName("Should return 500 when persistence fails")
        void shouldHandleException() {
            when(sessionRepository.findById(1L)).thenThrow(new RuntimeException("DB"));
            ResultDTO r = service.editSession(1L, null, null);
            assertFalse(r.isCorrect());
            assertEquals(500, r.getErrorCode());
        }
    }

    // ===================== HISTORY & SUMMARY =====================

    @Nested
    @DisplayName("getHistoryByPeriod")
    class HistoryTests {
        @Test
        @DisplayName("Should map sessions with durations")
        @SuppressWarnings("unchecked")
        void shouldReturnHistory() {
            LocalDateTime s = LocalDateTime.of(2026, 3, 15, 8, 0);
            FastingSession done = FastingSession.builder().id(1L).userId(USER_ID)
                    .startTime(s).targetEndTime(s.plusHours(16)).actualEndTime(s.plusHours(16))
                    .fastingHours(16).status(SessionStatus.COMPLETED).active(true).build();
            FastingSession noEnd = FastingSession.builder().id(2L).userId(USER_ID)
                    .startTime(s.plusDays(1)).targetEndTime(s.plusDays(1).plusHours(16))
                    .fastingHours(16).status(SessionStatus.IN_PROGRESS).active(true).build();

            when(sessionRepository.findByUserIdAndStartTimeBetweenAndActiveTrueOrderByStartTimeAsc(eq(USER_ID), any(), any()))
                    .thenReturn(List.of(done, noEnd));

            ResultDTO r = service.getHistoryByPeriod(USER_ID, 1, LocalDate.of(2026, 3, 20));
            assertTrue(r.isCorrect());
            Map<String, Object> result = (Map<String, Object>) r.getObject();
            List<Map<String, Object>> days = (List<Map<String, Object>>) result.get("days");
            assertEquals(2, days.size());
            assertEquals("16h 0m", days.get(0).get("durationFormatted"));
            assertFalse(days.get(1).containsKey("durationMinutes"));
        }

        @Test
        @DisplayName("Should default reference date to today when null")
        void shouldDefaultReference() {
            when(sessionRepository.findByUserIdAndStartTimeBetweenAndActiveTrueOrderByStartTimeAsc(eq(USER_ID), any(), any()))
                    .thenReturn(Collections.emptyList());
            ResultDTO r = service.getHistoryByPeriod(USER_ID, 1, null);
            assertTrue(r.isCorrect());
        }

        @Test
        @DisplayName("Should return 500 when query fails")
        void shouldHandleException() {
            when(sessionRepository.findByUserIdAndStartTimeBetweenAndActiveTrueOrderByStartTimeAsc(eq(USER_ID), any(), any()))
                    .thenThrow(new RuntimeException("DB"));
            ResultDTO r = service.getHistoryByPeriod(USER_ID, 1, LocalDate.of(2026, 3, 20));
            assertFalse(r.isCorrect());
            assertEquals(500, r.getErrorCode());
        }
    }

    @Nested
    @DisplayName("getSummaryByPeriod")
    class SummaryTests {
        @Test
        @DisplayName("Should compute counts, compliance and streaks")
        @SuppressWarnings("unchecked")
        void shouldReturnSummary() {
            LocalDate periodRef = LocalDate.now();
            LocalDateTime base = periodRef.withDayOfMonth(2).atTime(8, 0);
            FastingSession c1 = FastingSession.builder().id(1L).userId(USER_ID)
                    .startTime(base).targetEndTime(base.plusHours(16)).actualEndTime(base.plusHours(16))
                    .fastingHours(16).status(SessionStatus.COMPLETED).active(true).build();
            FastingSession c2 = FastingSession.builder().id(2L).userId(USER_ID)
                    .startTime(base.plusDays(1)).targetEndTime(base.plusDays(1).plusHours(16))
                    .actualEndTime(base.plusDays(1).plusHours(16))
                    .fastingHours(16).status(SessionStatus.COMPLETED).active(true).build();
            FastingSession inc = FastingSession.builder().id(3L).userId(USER_ID)
                    .startTime(base.plusDays(2)).targetEndTime(base.plusDays(2).plusHours(16))
                    .fastingHours(16).status(SessionStatus.INCOMPLETE).active(true).build();

            when(sessionRepository.findByUserIdAndStartTimeBetweenAndActiveTrueOrderByStartTimeAsc(eq(USER_ID), any(), any()))
                    .thenReturn(List.of(c1, c2, inc));

            ResultDTO r = service.getSummaryByPeriod(USER_ID, 1, periodRef);
            assertTrue(r.isCorrect());
            Map<String, Object> summary = (Map<String, Object>) r.getObject();
            assertEquals(2, summary.get("completed"));
            assertEquals(1, summary.get("incomplete"));
            assertEquals(3, summary.get("totalSessions"));
            assertTrue((int) summary.get("bestStreak") >= 2);
        }

        @Test
        @DisplayName("Should default reference date to today when null")
        void shouldDefaultReference() {
            when(sessionRepository.findByUserIdAndStartTimeBetweenAndActiveTrueOrderByStartTimeAsc(eq(USER_ID), any(), any()))
                    .thenReturn(Collections.emptyList());
            ResultDTO r = service.getSummaryByPeriod(USER_ID, 1, null);
            assertTrue(r.isCorrect());
        }

        @Test
        @DisplayName("Should return 500 when query fails")
        void shouldHandleException() {
            when(sessionRepository.findByUserIdAndStartTimeBetweenAndActiveTrueOrderByStartTimeAsc(eq(USER_ID), any(), any()))
                    .thenThrow(new RuntimeException("DB"));
            ResultDTO r = service.getSummaryByPeriod(USER_ID, 1, LocalDate.now());
            assertFalse(r.isCorrect());
            assertEquals(500, r.getErrorCode());
        }
    }

    // ===================== ACHIEVEMENTS =====================

    @Nested
    @DisplayName("getAchievements")
    class GetAchievementsTests {
        @Test
        @DisplayName("Should mark unlocked badges and include unlock date")
        @SuppressWarnings("unchecked")
        void shouldReturnAchievements() {
            Achievement first = Achievement.builder().id(1L).userId(USER_ID).code("FIRST_FAST")
                    .name("Primer Ayuno").description("d").icon("x").unlockedAt(LocalDateTime.now()).build();
            when(achievementRepository.findByUserIdOrderByUnlockedAtDesc(USER_ID)).thenReturn(List.of(first));

            ResultDTO r = service.getAchievements(USER_ID);
            assertTrue(r.isCorrect());
            List<Map<String, Object>> all = (List<Map<String, Object>>) r.getObject();
            assertEquals(12, all.size());
            Map<String, Object> firstBadge = all.stream()
                    .filter(b -> "FIRST_FAST".equals(b.get("code"))).findFirst().orElseThrow();
            assertEquals(true, firstBadge.get("unlocked"));
            assertTrue(firstBadge.containsKey("unlockedAt"));
            Map<String, Object> locked = all.stream()
                    .filter(b -> "STREAK_30".equals(b.get("code"))).findFirst().orElseThrow();
            assertEquals(false, locked.get("unlocked"));
        }
    }

    // ===================== WATER =====================

    @Nested
    @DisplayName("water tracker")
    class WaterTests {
        @Test
        @DisplayName("getWaterToday should return existing log")
        void shouldGetExisting() {
            WaterLog log = WaterLog.builder().id(1L).userId(USER_ID).logDate(LocalDate.now())
                    .glasses(3).goalGlasses(8).build();
            when(waterLogRepository.findByUserIdAndLogDate(eq(USER_ID), any())).thenReturn(Optional.of(log));
            ResultDTO r = service.getWaterToday(USER_ID);
            assertTrue(r.isCorrect());
            assertEquals(3, ((WaterLog) r.getObject()).getGlasses());
        }

        @Test
        @DisplayName("getWaterToday should default when no log exists")
        void shouldGetDefault() {
            when(waterLogRepository.findByUserIdAndLogDate(eq(USER_ID), any())).thenReturn(Optional.empty());
            ResultDTO r = service.getWaterToday(USER_ID);
            assertTrue(r.isCorrect());
            assertEquals(0, ((WaterLog) r.getObject()).getGlasses());
        }

        @Test
        @DisplayName("addWater should increment and persist")
        void shouldAddWater() {
            WaterLog log = WaterLog.builder().id(1L).userId(USER_ID).logDate(LocalDate.now())
                    .glasses(2).goalGlasses(8).build();
            when(waterLogRepository.findByUserIdAndLogDate(eq(USER_ID), any())).thenReturn(Optional.of(log));
            when(waterLogRepository.save(any(WaterLog.class))).thenAnswer(i -> i.getArgument(0));

            ResultDTO r = service.addWater(USER_ID, 3);
            assertTrue(r.isCorrect());
            assertEquals(5, ((WaterLog) r.getObject()).getGlasses());
        }

        @Test
        @DisplayName("addWater should clamp to zero on negative")
        void shouldClampToZero() {
            WaterLog log = WaterLog.builder().id(1L).userId(USER_ID).logDate(LocalDate.now())
                    .glasses(1).goalGlasses(8).build();
            when(waterLogRepository.findByUserIdAndLogDate(eq(USER_ID), any())).thenReturn(Optional.of(log));
            when(waterLogRepository.save(any(WaterLog.class))).thenAnswer(i -> i.getArgument(0));

            ResultDTO r = service.addWater(USER_ID, -5);
            assertEquals(0, ((WaterLog) r.getObject()).getGlasses());
        }

        @Test
        @DisplayName("setWaterGoal should update goal when valid")
        void shouldSetGoal() {
            when(waterLogRepository.findByUserIdAndLogDate(eq(USER_ID), any())).thenReturn(Optional.empty());
            when(waterLogRepository.save(any(WaterLog.class))).thenAnswer(i -> i.getArgument(0));

            ResultDTO r = service.setWaterGoal(USER_ID, 10);
            assertTrue(r.isCorrect());
            assertEquals(10, ((WaterLog) r.getObject()).getGoalGlasses());
        }

        @Test
        @DisplayName("setWaterGoal should reject out-of-range values")
        void shouldRejectInvalidGoal() {
            assertEquals(400, service.setWaterGoal(USER_ID, 0).getErrorCode());
            assertEquals(400, service.setWaterGoal(USER_ID, 21).getErrorCode());
        }
    }

    // ===================== ADDITIONAL BRANCH COVERAGE =====================

    @Nested
    @DisplayName("additional branches")
    class MoreBranchesTests {

        @Test
        @DisplayName("stopFasting with null end time uses current time (still in progress -> incomplete)")
        void stopWithNullEndTime() {
            LocalDateTime start = LocalDateTime.now().minusHours(1);
            FastingSession session = activeSession(start, start.plusHours(16)); // target in the future
            when(sessionRepository.findByUserIdAndStatusAndActiveTrue(USER_ID, SessionStatus.IN_PROGRESS))
                    .thenReturn(Optional.of(session));
            when(sessionRepository.save(any(FastingSession.class))).thenAnswer(i -> i.getArgument(0));
            when(sessionRepository.findByUserIdAndActiveTrueOrderByStartTimeDesc(USER_ID))
                    .thenReturn(Collections.emptyList());

            ResultDTO r = service.stopFasting(USER_ID, null);
            assertTrue(r.isCorrect());
            assertEquals(SessionStatus.INCOMPLETE, session.getStatus());
        }

        @Test
        @DisplayName("stopFasting completes when end time equals target exactly")
        void stopWhenEndEqualsTarget() {
            LocalDateTime start = LocalDateTime.of(2026, 3, 15, 10, 0);
            LocalDateTime target = start.plusHours(16);
            FastingSession session = activeSession(start, target);
            when(sessionRepository.findByUserIdAndStatusAndActiveTrue(USER_ID, SessionStatus.IN_PROGRESS))
                    .thenReturn(Optional.of(session));
            when(sessionRepository.save(any(FastingSession.class))).thenAnswer(i -> i.getArgument(0));
            when(sessionRepository.findByUserIdAndActiveTrueOrderByStartTimeDesc(USER_ID))
                    .thenReturn(Collections.emptyList());

            ResultDTO r = service.stopFasting(USER_ID, target); // exactly equal
            assertTrue(r.isCorrect());
            assertEquals(SessionStatus.COMPLETED, session.getStatus());
        }

        @Test
        @DisplayName("checkAndGrant should skip non-completed / null-end sessions and already-owned badges")
        void grantWithMixedHistoryAndExistingBadge() {
            LocalDateTime start = LocalDateTime.of(2026, 3, 15, 10, 0); // not early bird
            FastingSession session = activeSession(start, start.plusHours(16));
            when(sessionRepository.findByUserIdAndStatusAndActiveTrue(USER_ID, SessionStatus.IN_PROGRESS))
                    .thenReturn(Optional.of(session));
            when(sessionRepository.save(any(FastingSession.class))).thenAnswer(i -> i.getArgument(0));

            FastingSession completed = FastingSession.builder().id(10L).userId(USER_ID)
                    .startTime(start).targetEndTime(start.plusHours(10)).actualEndTime(start.plusHours(10))
                    .fastingHours(10).status(SessionStatus.COMPLETED).active(true).build();
            FastingSession completedNoEnd = FastingSession.builder().id(11L).userId(USER_ID)
                    .startTime(start.minusDays(1)).targetEndTime(start.minusDays(1).plusHours(10)).actualEndTime(null)
                    .fastingHours(10).status(SessionStatus.COMPLETED).active(true).build();
            FastingSession incomplete = FastingSession.builder().id(12L).userId(USER_ID)
                    .startTime(start.minusDays(2)).targetEndTime(start.minusDays(2).plusHours(10))
                    .fastingHours(10).status(SessionStatus.INCOMPLETE).active(true).build();

            when(sessionRepository.findByUserIdAndActiveTrueOrderByStartTimeDesc(USER_ID))
                    .thenReturn(List.of(completed, completedNoEnd, incomplete));
            // badge already owned -> grant() short-circuits
            when(achievementRepository.existsByUserIdAndCode(eq(USER_ID), anyString())).thenReturn(true);

            ResultDTO r = service.stopFasting(USER_ID, start.plusHours(20));
            assertTrue(r.isCorrect());
            verify(achievementRepository, never()).save(any(Achievement.class));
        }

        @Test
        @DisplayName("getSummaryByPeriod should count an in-progress session")
        @SuppressWarnings("unchecked")
        void summaryWithInProgress() {
            LocalDate ref = LocalDate.now();
            LocalDateTime base = ref.withDayOfMonth(2).atTime(8, 0);
            FastingSession inProgress = FastingSession.builder().id(1L).userId(USER_ID)
                    .startTime(base).targetEndTime(base.plusHours(16))
                    .fastingHours(16).status(SessionStatus.IN_PROGRESS).active(true).build();
            when(sessionRepository.findByUserIdAndStartTimeBetweenAndActiveTrueOrderByStartTimeAsc(eq(USER_ID), any(), any()))
                    .thenReturn(List.of(inProgress));

            ResultDTO r = service.getSummaryByPeriod(USER_ID, 1, ref);
            assertTrue(r.isCorrect());
            Map<String, Object> summary = (Map<String, Object>) r.getObject();
            assertEquals(1, summary.get("inProgress"));
        }

        @Test
        @DisplayName("editSession completes when new end equals target exactly")
        void editEndEqualsTarget() {
            FastingSession session = activeSession(LocalDateTime.of(2026, 3, 15, 8, 0),
                    LocalDateTime.of(2026, 3, 16, 0, 0));
            when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
            when(sessionRepository.save(any(FastingSession.class))).thenAnswer(i -> i.getArgument(0));

            ResultDTO r = service.editSession(1L, null, LocalDateTime.of(2026, 3, 16, 0, 0)); // equals target
            assertTrue(r.isCorrect());
            assertEquals(SessionStatus.COMPLETED, session.getStatus());
        }
    }
}

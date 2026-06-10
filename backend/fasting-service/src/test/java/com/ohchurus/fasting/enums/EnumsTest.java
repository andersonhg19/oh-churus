package com.ohchurus.fasting.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Fasting enums")
class EnumsTest {

    @Test
    @DisplayName("PlanType presets should have fasting+eating hours summing to 24")
    void planTypeHoursSumTo24() {
        for (PlanType p : PlanType.values()) {
            if (p == PlanType.CUSTOM) {
                assertEquals(0, p.fastingHours);
                assertEquals(0, p.eatingHours);
            } else {
                assertEquals(24, p.fastingHours + p.eatingHours, "Plan " + p + " must sum 24");
            }
        }
        assertEquals(PlanType.PLAN_16_8, PlanType.valueOf("PLAN_16_8"));
    }

    @Test
    @DisplayName("SessionStatus should expose the expected states")
    void sessionStatusValues() {
        assertEquals(4, SessionStatus.values().length);
        assertEquals(SessionStatus.IN_PROGRESS, SessionStatus.valueOf("IN_PROGRESS"));
        assertEquals(SessionStatus.COMPLETED, SessionStatus.valueOf("COMPLETED"));
        assertEquals(SessionStatus.INCOMPLETE, SessionStatus.valueOf("INCOMPLETE"));
        assertEquals(SessionStatus.CANCELLED, SessionStatus.valueOf("CANCELLED"));
    }
}

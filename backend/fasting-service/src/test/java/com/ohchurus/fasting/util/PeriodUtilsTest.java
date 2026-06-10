package com.ohchurus.fasting.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PeriodUtils (fasting)")
class PeriodUtilsTest {

    @Nested
    @DisplayName("getStartOfPeriod")
    class StartTests {

        @Test
        @DisplayName("Should return same-month start when reference is on/after the start day")
        void shouldReturnSameMonth() {
            LocalDate ref = LocalDate.of(2026, 3, 20);
            assertEquals(LocalDate.of(2026, 3, 15), PeriodUtils.getStartOfPeriod(15, ref));
        }

        @Test
        @DisplayName("Should roll back to previous month when reference is before the start day")
        void shouldReturnPreviousMonth() {
            LocalDate ref = LocalDate.of(2026, 3, 10);
            assertEquals(LocalDate.of(2026, 2, 15), PeriodUtils.getStartOfPeriod(15, ref));
        }

        @Test
        @DisplayName("Should clamp day 31 to last day on short months")
        void shouldClampShortMonth() {
            // February 2026 (non-leap, 28 days) with start day 31 -> 28
            LocalDate ref = LocalDate.of(2026, 2, 28);
            assertEquals(LocalDate.of(2026, 2, 28), PeriodUtils.getStartOfPeriod(31, ref));
        }
    }

    @Nested
    @DisplayName("getEndOfPeriod")
    class EndTests {

        @Test
        @DisplayName("Should end the day before next period start")
        void shouldComputeEnd() {
            LocalDate start = LocalDate.of(2026, 3, 15);
            assertEquals(LocalDate.of(2026, 4, 14), PeriodUtils.getEndOfPeriod(15, start));
        }

        @Test
        @DisplayName("Should clamp end when next month is shorter")
        void shouldClampEnd() {
            // start day 31 January -> next month Feb clamps to 28 -> end = 27 Feb
            LocalDate start = LocalDate.of(2026, 1, 31);
            assertEquals(LocalDate.of(2026, 2, 27), PeriodUtils.getEndOfPeriod(31, start));
        }
    }
}

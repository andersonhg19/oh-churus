package com.ohchurus.budget.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class PeriodUtilsTest {

    @Nested
    @DisplayName("getStartOfPeriod")
    class GetStartOfPeriodTests {

        @Test
        @DisplayName("Should return start of period for day 1 in regular month")
        void shouldReturnStartForDay1() {
            LocalDate ref = LocalDate.of(2026, 3, 15);
            LocalDate result = PeriodUtils.getStartOfPeriod(1, ref);
            assertEquals(LocalDate.of(2026, 3, 1), result);
        }

        @Test
        @DisplayName("Should return start of period for day 15 when reference is after start day")
        void shouldReturnStartForDay15AfterStartDay() {
            LocalDate ref = LocalDate.of(2026, 3, 20);
            LocalDate result = PeriodUtils.getStartOfPeriod(15, ref);
            assertEquals(LocalDate.of(2026, 3, 15), result);
        }

        @Test
        @DisplayName("Should return start of period for day 15 when reference is on start day")
        void shouldReturnStartForDay15OnStartDay() {
            LocalDate ref = LocalDate.of(2026, 3, 15);
            LocalDate result = PeriodUtils.getStartOfPeriod(15, ref);
            assertEquals(LocalDate.of(2026, 3, 15), result);
        }

        @Test
        @DisplayName("Should return previous month when reference is before start day")
        void shouldReturnPreviousMonthWhenBeforeStartDay() {
            LocalDate ref = LocalDate.of(2026, 3, 10);
            LocalDate result = PeriodUtils.getStartOfPeriod(15, ref);
            assertEquals(LocalDate.of(2026, 2, 15), result);
        }

        @Test
        @DisplayName("Should adjust day 31 to 28 for February non-leap year")
        void shouldAdjustDay31ToFeb28() {
            // Reference March 10 with startDay=31 -> previous month is Feb (28 days)
            LocalDate ref = LocalDate.of(2026, 3, 10);
            LocalDate result = PeriodUtils.getStartOfPeriod(31, ref);
            assertEquals(LocalDate.of(2026, 2, 28), result);
        }

        @Test
        @DisplayName("Should adjust day 31 to 29 for February leap year")
        void shouldAdjustDay31ToFeb29LeapYear() {
            // 2028 is a leap year. Reference March 10, startDay=31 -> previous month is Feb (29 days)
            LocalDate ref = LocalDate.of(2028, 3, 10);
            LocalDate result = PeriodUtils.getStartOfPeriod(31, ref);
            assertEquals(LocalDate.of(2028, 2, 29), result);
        }

        @Test
        @DisplayName("Should adjust day 30 for April (30 days) when reference is after start day")
        void shouldUseDay30InApril() {
            LocalDate ref = LocalDate.of(2026, 4, 30);
            LocalDate result = PeriodUtils.getStartOfPeriod(30, ref);
            assertEquals(LocalDate.of(2026, 4, 30), result);
        }

        @Test
        @DisplayName("Should use day 31 in January (31 days)")
        void shouldUseDay31InJanuary() {
            LocalDate ref = LocalDate.of(2026, 1, 31);
            LocalDate result = PeriodUtils.getStartOfPeriod(31, ref);
            assertEquals(LocalDate.of(2026, 1, 31), result);
        }

        @Test
        @DisplayName("Should handle period spanning year boundary (December -> January)")
        void shouldHandleYearBoundary() {
            // Reference Jan 5 with startDay=15 -> previous month is Dec
            LocalDate ref = LocalDate.of(2026, 1, 5);
            LocalDate result = PeriodUtils.getStartOfPeriod(15, ref);
            assertEquals(LocalDate.of(2025, 12, 15), result);
        }

        @Test
        @DisplayName("Should adjust day 31 in current month with 30 days when reference is on day 30")
        void shouldAdjustDay31InMonthWith30Days() {
            // April has 30 days, startDay=31 -> adjusted to 30
            LocalDate ref = LocalDate.of(2026, 4, 30);
            LocalDate result = PeriodUtils.getStartOfPeriod(31, ref);
            assertEquals(LocalDate.of(2026, 4, 30), result);
        }

        @Test
        @DisplayName("Should go to previous month when reference is before adjusted start in February")
        void shouldGoPreviousMonthBeforeAdjustedStartFeb() {
            // February with startDay=31 -> adjusted to 28. Reference Feb 20 is before 28.
            LocalDate ref = LocalDate.of(2026, 2, 20);
            LocalDate result = PeriodUtils.getStartOfPeriod(31, ref);
            assertEquals(LocalDate.of(2026, 1, 31), result);
        }
    }

    @Nested
    @DisplayName("getEndOfPeriod")
    class GetEndOfPeriodTests {

        @Test
        @DisplayName("Should return end of period as next month start minus 1 day")
        void shouldReturnEndOfPeriod() {
            LocalDate start = LocalDate.of(2026, 3, 15);
            LocalDate result = PeriodUtils.getEndOfPeriod(15, start);
            // Next month April 15 minus 1 = April 14
            assertEquals(LocalDate.of(2026, 4, 14), result);
        }

        @Test
        @DisplayName("Should return end of period for day 1")
        void shouldReturnEndForDay1() {
            LocalDate start = LocalDate.of(2026, 3, 1);
            LocalDate result = PeriodUtils.getEndOfPeriod(1, start);
            // Next month April 1 minus 1 = March 31
            assertEquals(LocalDate.of(2026, 3, 31), result);
        }

        @Test
        @DisplayName("Should handle end of period with February adjustment")
        void shouldHandleEndWithFebAdjustment() {
            // Start Jan 31, budgetStartDay=31, next month is Feb (28 days) -> adjusted 28 - 1 = 27
            LocalDate start = LocalDate.of(2026, 1, 31);
            LocalDate result = PeriodUtils.getEndOfPeriod(31, start);
            assertEquals(LocalDate.of(2026, 2, 27), result);
        }

        @Test
        @DisplayName("Should handle end of period spanning year boundary")
        void shouldHandleEndSpanningYearBoundary() {
            LocalDate start = LocalDate.of(2025, 12, 15);
            LocalDate result = PeriodUtils.getEndOfPeriod(15, start);
            // Next month Jan 15 minus 1 = Jan 14
            assertEquals(LocalDate.of(2026, 1, 14), result);
        }

        @Test
        @DisplayName("Should handle end of period for leap year February")
        void shouldHandleEndForLeapYearFeb() {
            // Start Jan 31 2028 (leap year), budgetStartDay=31, next month Feb has 29 -> adjusted 29 - 1 = 28
            LocalDate start = LocalDate.of(2028, 1, 31);
            LocalDate result = PeriodUtils.getEndOfPeriod(31, start);
            assertEquals(LocalDate.of(2028, 2, 28), result);
        }
    }
}

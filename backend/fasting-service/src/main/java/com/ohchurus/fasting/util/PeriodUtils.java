package com.ohchurus.fasting.util;

import java.time.LocalDate;
import java.time.YearMonth;

public final class PeriodUtils {

    private PeriodUtils() {}

    public static LocalDate getStartOfPeriod(int budgetStartDay, LocalDate referenceDate) {
        YearMonth currentMonth = YearMonth.from(referenceDate);
        int adjustedDay = Math.min(budgetStartDay, currentMonth.lengthOfMonth());
        LocalDate startOfCurrentPeriod = currentMonth.atDay(adjustedDay);

        if (referenceDate.isBefore(startOfCurrentPeriod)) {
            YearMonth prevMonth = currentMonth.minusMonths(1);
            adjustedDay = Math.min(budgetStartDay, prevMonth.lengthOfMonth());
            return prevMonth.atDay(adjustedDay);
        }
        return startOfCurrentPeriod;
    }

    public static LocalDate getEndOfPeriod(int budgetStartDay, LocalDate startOfPeriod) {
        YearMonth nextMonth = YearMonth.from(startOfPeriod).plusMonths(1);
        int adjustedDay = Math.min(budgetStartDay, nextMonth.lengthOfMonth());
        return nextMonth.atDay(adjustedDay).minusDays(1);
    }
}

package com.ohchurus.budget.dto.output;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardSummaryDTO {

    private BigDecimal totalIncome;
    private BigDecimal totalExpense;
    private BigDecimal balance;
    private BigDecimal budgetTotal;
    private int pendingCount;
    private BigDecimal pendingAmount;
    private LocalDate periodStart;
    private LocalDate periodEnd;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CategorySummary {
        private Long categoryId;
        private String categoryName;
        private String categoryType;
        private String icon;
        private String color;
        private BigDecimal total;
        private long count;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SplitSummary {
        private BigDecimal personalIncome;
        private BigDecimal personalExpense;
        private BigDecimal personalBalance;
        private BigDecimal sharedIncome;
        private BigDecimal sharedExpense;
        private BigDecimal sharedBalance;
        private BigDecimal totalIncome;
        private BigDecimal totalExpense;
        private BigDecimal totalBalance;
        private int personalPendingCount;
        private int sharedPendingCount;
        private LocalDate periodStart;
        private LocalDate periodEnd;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TrendDTO {
        private BigDecimal currentIncome;
        private BigDecimal currentExpense;
        private BigDecimal previousIncome;
        private BigDecimal previousExpense;
        private BigDecimal changePercentage;
        private LocalDate currentPeriodStart;
        private LocalDate currentPeriodEnd;
        private LocalDate previousPeriodStart;
        private LocalDate previousPeriodEnd;
    }
}

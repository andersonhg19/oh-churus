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
    /* Ola 5: lo PRESUPUESTADO (las asignaciones del periodo). Antes era la
       suma de lo programado, y la pantalla de Presupuesto decia otra cifra:
       dos "presupuestos" distintos para el mismo mes. Si el periodo no tiene
       asignaciones se cae a lo programado, que es lo que mostraba antes. */
    private BigDecimal budgetTotal;
    /* Lo que suman los gastos programados del periodo (pagados o no). */
    private BigDecimal scheduledExpenseTotal;
    private int pendingCount;
    /* pendingAmount mezclaba sueldos por cobrar con cuentas por pagar, y la
       suma no significaba nada. Se conserva por compatibilidad; las dos
       cifras utiles van separadas. */
    private BigDecimal pendingAmount;
    private BigDecimal pendingIncomeAmount;
    private BigDecimal pendingExpenseAmount;
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
        /* Ola 5: el presupuesto de cada ambito y lo que falta por pagar en
           cada uno. Sin esto Conjunto y Personal solo sabian de lo confirmado
           y con el mes recien empezado salian en cero. */
        private BigDecimal sharedBudget;
        private BigDecimal personalBudget;
        private BigDecimal sharedPendingExpense;
        private BigDecimal personalPendingExpense;
        private BigDecimal sharedPendingIncome;
        private BigDecimal personalPendingIncome;
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

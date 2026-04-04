package com.ohchurus.fasting.enums;

public enum PlanType {
    PLAN_12_12(12, 12),
    PLAN_14_10(14, 10),
    PLAN_16_8(16, 8),
    PLAN_18_6(18, 6),
    PLAN_20_4(20, 4),
    CUSTOM(0, 0);

    public final int fastingHours;
    public final int eatingHours;

    PlanType(int fastingHours, int eatingHours) {
        this.fastingHours = fastingHours;
        this.eatingHours = eatingHours;
    }
}

package com.ohchurus.auth.util;

import com.ohchurus.auth.dto.output.ResultDTO;

public final class ValidationUtils {

    private ValidationUtils() {}

    public static ResultDTO error(String message, int errorCode) {
        return new ResultDTO(false, message, errorCode);
    }

    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public static boolean isValidBudgetStartDay(Integer day) {
        return day != null && day >= 1 && day <= 31;
    }
}

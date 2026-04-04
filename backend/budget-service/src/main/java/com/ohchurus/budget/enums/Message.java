package com.ohchurus.budget.enums;

public class Message {

    private Message() {}

    public enum Msj {
        movementNotFound,
        scheduledNotFound,
        movementSavedSuccessfully,
        movementDeletedSuccessfully,
        scheduledSavedSuccessfully,
        scheduledDeletedSuccessfully,
        movementConfirmedSuccessfully,
        pendingMovementsGenerated,
        invalidAmount,
        invalidDayOfMonth,
        startDateRequired,
        userIdRequired,
        categoryNotFound,
        categoryNameDuplicate,
        maxDepthExceeded,
        parentCategoryNotFound,
        categoryHasChildren,
        categorySavedSuccessfully,
        categoryDeletedSuccessfully
    }
}

package com.ohchurus.auth.enums;

public class Message {

    private Message() {}

    public enum Msj {
        emailAlreadyInUse,
        userNotFound,
        invalidCredentials,
        userInactive,
        passwordRequired,
        budgetStartDayInvalid,
        userSavedSuccessfully,
        userUpdatedSuccessfully,
        userDeletedSuccessfully,
        userListRetrieved
    }
}

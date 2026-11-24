package com.codencode.learningq.entity;

public enum TokenPurpose {
    EMAIL_VERIFICATION("email_verification"),
    PASSWORD_RESET("password_reset");

    private final String databaseValue;

    TokenPurpose(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    public String databaseValue() {
        return databaseValue;
    }
}

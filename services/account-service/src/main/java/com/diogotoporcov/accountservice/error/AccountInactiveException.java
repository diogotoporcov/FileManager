package com.diogotoporcov.accountservice.error;

public class AccountInactiveException extends RuntimeException {
    public AccountInactiveException() {
        super("Account is inactive");
    }

    public AccountInactiveException(String message) {
        super(message);
    }
}

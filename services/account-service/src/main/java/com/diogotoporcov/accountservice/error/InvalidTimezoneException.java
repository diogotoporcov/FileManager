package com.diogotoporcov.accountservice.error;

public class InvalidTimezoneException extends RuntimeException {
    public InvalidTimezoneException(String message) {
        super(message);
    }
}

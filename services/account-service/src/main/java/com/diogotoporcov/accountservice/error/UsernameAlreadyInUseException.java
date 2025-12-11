package com.diogotoporcov.accountservice.error;

public class UsernameAlreadyInUseException extends RuntimeException {
    public UsernameAlreadyInUseException(String username) {
        super("Username already in use: " + username);
    }
}

package com.diogotoporcov.authservice.error;

import java.util.UUID;

public class SessionNotFoundException extends RuntimeException {
    public SessionNotFoundException(UUID sessionId) {
        super("Session not found: " + sessionId);
    }

    public SessionNotFoundException() {
        super("Session not found");
    }
}

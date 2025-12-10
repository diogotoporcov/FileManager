package com.diogotoporcov.authservice.events.register;

public record UserRegisteredEvent(
        String userId,
        String email
) {}

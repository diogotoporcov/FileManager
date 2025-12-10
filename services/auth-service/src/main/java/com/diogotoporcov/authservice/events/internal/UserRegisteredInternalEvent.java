package com.diogotoporcov.authservice.events.internal;

import java.util.UUID;

public record UserRegisteredInternalEvent(
        UUID userId,
        String email
) {}

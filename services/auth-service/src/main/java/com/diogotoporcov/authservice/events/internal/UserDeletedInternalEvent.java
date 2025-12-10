package com.diogotoporcov.authservice.events.internal;

import java.util.UUID;

public record UserDeletedInternalEvent(
        UUID userId
) {}

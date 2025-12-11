package com.diogotoporcov.accountservice.events.user.internal;

import java.util.UUID;

public record UserRegisteredInternalEvent(
        String userId
) {
    public UUID userIdAsUuid() {
        return UUID.fromString(userId);
    }
}

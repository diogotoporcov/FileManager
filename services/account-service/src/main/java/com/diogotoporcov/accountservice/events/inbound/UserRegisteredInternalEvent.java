package com.diogotoporcov.accountservice.events.inbound;

import java.util.UUID;

public record UserRegisteredInternalEvent(
        String userId,
        String email
) {
    public UUID userIdAsUuid() {
        return UUID.fromString(userId);
    }
}

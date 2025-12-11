package com.diogotoporcov.accountservice.events.inbound;

import java.util.UUID;

public record UserDeletedInternalEvent(
        String userId
) {
    public UUID userIdAsUuid() {
        return UUID.fromString(userId);
    }
}

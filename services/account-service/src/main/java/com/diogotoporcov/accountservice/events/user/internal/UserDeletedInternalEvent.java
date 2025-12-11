package com.diogotoporcov.accountservice.events.user.internal;

import java.util.UUID;

public record UserDeletedInternalEvent(
        String userId
) {
    public UUID userIdAsUuid() {
        return UUID.fromString(userId);
    }
}

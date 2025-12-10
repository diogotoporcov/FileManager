package com.diogotoporcov.authservice.events;

import java.util.UUID;

public interface UserEventPublisher {
    void publishUserRegistered(UUID userId, String email);
    void publishUserDeleted(UUID userId);
}

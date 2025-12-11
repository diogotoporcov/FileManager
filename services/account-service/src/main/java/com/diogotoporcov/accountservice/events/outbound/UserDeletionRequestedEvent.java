package com.diogotoporcov.accountservice.events.outbound;

import java.util.UUID;

public record UserDeletionRequestedEvent(
        UUID userId
) {}

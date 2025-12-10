package com.diogotoporcov.authservice.api.dto;

import java.time.Instant;

public record SessionResponse(
        String sessionId,
        String deviceName,
        String userAgent,
        String ipAddress,
        Instant createdAt,
        Instant lastUsedAt,
        boolean current
) {}

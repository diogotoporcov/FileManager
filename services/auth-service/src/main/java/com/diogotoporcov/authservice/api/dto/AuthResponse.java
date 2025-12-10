package com.diogotoporcov.authservice.api.dto;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        String refreshToken
) {}

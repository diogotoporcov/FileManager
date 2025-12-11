package com.diogotoporcov.accountservice.api.dto;

public record MyProfileResponse(
        String userId,
        String email,
        String fullName,
        String username,
        String locale,
        String timezone,
        String status
) {}

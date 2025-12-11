package com.diogotoporcov.accountservice.api.dto;

public record UpdateMyProfileRequest(
        String fullName,
        String username,
        String locale,
        String timezone
) {}

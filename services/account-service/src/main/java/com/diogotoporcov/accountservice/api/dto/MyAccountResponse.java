package com.diogotoporcov.accountservice.api.dto;

public record MyAccountResponse(
        String userId,
        String fullName,
        String username,
        String locale,
        String timezone,
        String status
) {}

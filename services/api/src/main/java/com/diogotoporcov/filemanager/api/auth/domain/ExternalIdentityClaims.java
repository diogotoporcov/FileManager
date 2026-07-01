package com.diogotoporcov.filemanager.api.auth.domain;

public record ExternalIdentityClaims(
        String subject,
        String email,
        String firstName,
        String lastName,
        Boolean emailVerified
) {
}

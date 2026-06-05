package com.filemanager.api.auth.domain;

public record AuthenticatedIdentity(
        String provider,
        String subject,
        String email,
        String firstName,
        String lastName,
        Boolean emailVerified
) {
}

package com.filemanager.api.port;

public record AuthenticatedIdentity(
        String provider,
        String subject,
        String email,
        String firstName,
        String lastName,
        Boolean emailVerified
) {
}

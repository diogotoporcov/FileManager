package com.filemanager.api.adapter;

import com.filemanager.api.port.AuthenticatedIdentity;
import com.filemanager.api.port.IdentityProviderPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class OidcJwtIdentityProviderAdapter implements IdentityProviderPort {

    private final String provider;

    public OidcJwtIdentityProviderAdapter(@Value("${app.auth.provider-name:keycloak}") String provider) {
        this.provider = provider;
    }

    @Override
    public AuthenticatedIdentity extractIdentity(Jwt jwt) {
        String subject = jwt.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("Subject (sub) claim is missing or blank in JWT");
        }

        String rawEmail = jwt.getClaimAsString("email");
        if (rawEmail == null || rawEmail.isBlank()) {
            throw new IllegalArgumentException("Email claim is missing or blank in JWT");
        }

        return new AuthenticatedIdentity(
                provider,
                subject,
                rawEmail.trim().toLowerCase(),
                jwt.getClaimAsString("given_name"),
                jwt.getClaimAsString("family_name"),
                jwt.getClaimAsBoolean("email_verified")
        );
    }
}

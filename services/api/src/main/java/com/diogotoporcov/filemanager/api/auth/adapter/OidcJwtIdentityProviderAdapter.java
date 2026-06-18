package com.diogotoporcov.filemanager.api.auth.adapter;

import com.diogotoporcov.filemanager.api.config.AppProperties;
import com.diogotoporcov.filemanager.api.auth.domain.AuthenticatedIdentity;
import com.diogotoporcov.filemanager.api.auth.port.IdentityProviderPort;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class OidcJwtIdentityProviderAdapter implements IdentityProviderPort {

    private final AppProperties appProperties;

    public OidcJwtIdentityProviderAdapter(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Override
    public AuthenticatedIdentity extractIdentity(Jwt jwt) {
        AppProperties.Auth auth = appProperties.getAuth();
        AppProperties.Auth.Claims claims = auth.getClaims();
        String subject = jwt.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("Subject (sub) claim is missing or blank in JWT");
        }

        String rawEmail = jwt.getClaimAsString(claims.getEmail());
        if (rawEmail == null || rawEmail.isBlank()) {
            throw new IllegalArgumentException("Email claim is missing or blank in JWT");
        }

        return new AuthenticatedIdentity(
                auth.getProviderName(),
                subject,
                rawEmail.trim().toLowerCase(),
                jwt.getClaimAsString(claims.getFirstName()),
                jwt.getClaimAsString(claims.getLastName()),
                jwt.getClaimAsBoolean(claims.getEmailVerified())
        );
    }
}

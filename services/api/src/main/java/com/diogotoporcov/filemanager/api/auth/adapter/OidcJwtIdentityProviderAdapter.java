package com.diogotoporcov.filemanager.api.auth.adapter;

import com.diogotoporcov.filemanager.api.config.AppProperties;
import com.diogotoporcov.filemanager.api.auth.domain.AuthenticatedIdentity;
import com.diogotoporcov.filemanager.api.auth.domain.ExternalIdentityClaims;
import com.diogotoporcov.filemanager.api.auth.port.IdentityProviderPort;
import org.springframework.stereotype.Component;

@Component
public class OidcJwtIdentityProviderAdapter implements IdentityProviderPort {

    private final AppProperties appProperties;

    public OidcJwtIdentityProviderAdapter(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Override
    public AuthenticatedIdentity extractIdentity(ExternalIdentityClaims claims) {
        AppProperties.Auth auth = appProperties.getAuth();
        String subject = claims.subject();
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("Subject (sub) claim is missing or blank in JWT");
        }

        String rawEmail = claims.email();
        if (rawEmail == null || rawEmail.isBlank()) {
            throw new IllegalArgumentException("Email claim is missing or blank in JWT");
        }

        return new AuthenticatedIdentity(
                auth.getProviderName(),
                subject,
                rawEmail.trim().toLowerCase(),
                claims.firstName(),
                claims.lastName(),
                claims.emailVerified()
        );
    }
}

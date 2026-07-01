package com.diogotoporcov.filemanager.api.auth.security;

import com.diogotoporcov.filemanager.api.auth.application.CurrentUserProvider;
import com.diogotoporcov.filemanager.api.auth.domain.ExternalIdentityClaims;
import com.diogotoporcov.filemanager.api.config.AppProperties;
import com.diogotoporcov.filemanager.api.identity.application.IdentityResolutionService;
import com.diogotoporcov.filemanager.api.identity.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SpringSecurityCurrentUserProvider implements CurrentUserProvider {

    private final IdentityResolutionService identityResolutionService;
    private final AppProperties appProperties;

    @Override
    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new AuthenticationCredentialsNotFoundException("No authenticated JWT principal found");
        }

        return identityResolutionService.resolveUser(toExternalIdentityClaims(jwt));
    }

    private ExternalIdentityClaims toExternalIdentityClaims(Jwt jwt) {
        AppProperties.Auth.Claims claims = appProperties.getAuth().getClaims();

        return new ExternalIdentityClaims(
                jwt.getSubject(),
                jwt.getClaimAsString(claims.getEmail()),
                jwt.getClaimAsString(claims.getFirstName()),
                jwt.getClaimAsString(claims.getLastName()),
                jwt.getClaimAsBoolean(claims.getEmailVerified()));
    }
}

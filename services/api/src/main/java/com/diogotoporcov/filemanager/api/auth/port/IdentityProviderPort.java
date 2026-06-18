package com.diogotoporcov.filemanager.api.auth.port;

import org.springframework.security.oauth2.jwt.Jwt;
import com.diogotoporcov.filemanager.api.auth.domain.AuthenticatedIdentity;

public interface IdentityProviderPort {
    AuthenticatedIdentity extractIdentity(Jwt jwt);
}

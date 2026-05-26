package com.filemanager.api.port;

import org.springframework.security.oauth2.jwt.Jwt;

public interface IdentityProviderPort {
    AuthenticatedIdentity extractIdentity(Jwt jwt);
}

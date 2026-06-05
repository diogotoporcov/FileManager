package com.filemanager.api.auth.port;

import org.springframework.security.oauth2.jwt.Jwt;
import com.filemanager.api.auth.domain.AuthenticatedIdentity;

public interface IdentityProviderPort {
    AuthenticatedIdentity extractIdentity(Jwt jwt);
}

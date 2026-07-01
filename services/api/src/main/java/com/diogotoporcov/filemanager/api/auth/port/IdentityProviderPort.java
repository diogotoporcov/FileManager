package com.diogotoporcov.filemanager.api.auth.port;

import com.diogotoporcov.filemanager.api.auth.domain.AuthenticatedIdentity;
import com.diogotoporcov.filemanager.api.auth.domain.ExternalIdentityClaims;

public interface IdentityProviderPort {
    AuthenticatedIdentity extractIdentity(ExternalIdentityClaims claims);
}

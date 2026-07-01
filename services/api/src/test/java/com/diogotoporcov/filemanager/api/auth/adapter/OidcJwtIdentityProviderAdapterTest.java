package com.diogotoporcov.filemanager.api.auth.adapter;

import com.diogotoporcov.filemanager.api.config.AppProperties;
import com.diogotoporcov.filemanager.api.auth.domain.AuthenticatedIdentity;
import com.diogotoporcov.filemanager.api.auth.domain.ExternalIdentityClaims;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OidcJwtIdentityProviderAdapterTest {

    private final AppProperties appProperties = new AppProperties();
    private final OidcJwtIdentityProviderAdapter adapter = new OidcJwtIdentityProviderAdapter(appProperties);

    @Test
    void extractIdentity_MapsClaimsToAuthenticatedIdentity() {
        appProperties.getAuth().setProviderName("custom-oidc");

        AuthenticatedIdentity identity = adapter.extractIdentity(new ExternalIdentityClaims(
                "subject-123",
                " USER@Example.Com ",
                "First",
                "Last",
                true));

        assertThat(identity.provider()).isEqualTo("custom-oidc");
        assertThat(identity.subject()).isEqualTo("subject-123");
        assertThat(identity.email()).isEqualTo("user@example.com");
        assertThat(identity.firstName()).isEqualTo("First");
        assertThat(identity.lastName()).isEqualTo("Last");
        assertThat(identity.emailVerified()).isTrue();
    }

    @Test
    void extractIdentity_MissingSubject_ThrowsException() {
        ExternalIdentityClaims claims = new ExternalIdentityClaims(null, "user@example.com", null, null, true);

        assertThatThrownBy(() -> adapter.extractIdentity(claims))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Subject (sub) claim is missing or blank");
    }

    @Test
    void extractIdentity_BlankSubject_ThrowsException() {
        ExternalIdentityClaims claims = new ExternalIdentityClaims(" ", "user@example.com", null, null, true);

        assertThatThrownBy(() -> adapter.extractIdentity(claims))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Subject (sub) claim is missing or blank");
    }

    @Test
    void extractIdentity_MissingEmail_ThrowsException() {
        ExternalIdentityClaims claims = new ExternalIdentityClaims("subject-123", null, null, null, true);

        assertThatThrownBy(() -> adapter.extractIdentity(claims))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email claim is missing or blank");
    }

    @Test
    void extractIdentity_BlankEmail_ThrowsException() {
        ExternalIdentityClaims claims = new ExternalIdentityClaims("subject-123", " ", null, null, true);

        assertThatThrownBy(() -> adapter.extractIdentity(claims))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email claim is missing or blank");
    }
}

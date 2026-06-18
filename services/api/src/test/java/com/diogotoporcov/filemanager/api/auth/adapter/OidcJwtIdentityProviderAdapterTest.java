package com.diogotoporcov.filemanager.api.auth.adapter;

import com.diogotoporcov.filemanager.api.config.AppProperties;
import com.diogotoporcov.filemanager.api.auth.domain.AuthenticatedIdentity;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OidcJwtIdentityProviderAdapterTest {

    private final AppProperties appProperties = new AppProperties();
    private final OidcJwtIdentityProviderAdapter adapter = new OidcJwtIdentityProviderAdapter(appProperties);

    @Test
    void extractIdentity_UsesConfiguredClaimNames() {
        appProperties.getAuth().setProviderName("custom-oidc");
        appProperties.getAuth().getClaims().setEmail("mail");
        appProperties.getAuth().getClaims().setFirstName("first");
        appProperties.getAuth().getClaims().setLastName("last");
        appProperties.getAuth().getClaims().setEmailVerified("verified");

        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("subject-123");
        when(jwt.getClaimAsString("mail")).thenReturn(" USER@Example.Com ");
        when(jwt.getClaimAsString("first")).thenReturn("First");
        when(jwt.getClaimAsString("last")).thenReturn("Last");
        when(jwt.getClaimAsBoolean("verified")).thenReturn(true);

        AuthenticatedIdentity identity = adapter.extractIdentity(jwt);

        assertThat(identity.provider()).isEqualTo("custom-oidc");
        assertThat(identity.subject()).isEqualTo("subject-123");
        assertThat(identity.email()).isEqualTo("user@example.com");
        assertThat(identity.firstName()).isEqualTo("First");
        assertThat(identity.lastName()).isEqualTo("Last");
        assertThat(identity.emailVerified()).isTrue();
    }

    @Test
    void extractIdentity_MissingSubject_ThrowsException() {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn(" ");

        assertThatThrownBy(() -> adapter.extractIdentity(jwt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Subject (sub) claim is missing or blank");
    }

    @Test
    void extractIdentity_MissingEmail_ThrowsException() {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("subject-123");
        when(jwt.getClaimAsString("email")).thenReturn(" ");

        assertThatThrownBy(() -> adapter.extractIdentity(jwt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email claim is missing or blank");
    }
}

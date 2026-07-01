package com.diogotoporcov.filemanager.api.auth.security;

import com.diogotoporcov.filemanager.api.auth.domain.ExternalIdentityClaims;
import com.diogotoporcov.filemanager.api.config.AppProperties;
import com.diogotoporcov.filemanager.api.identity.application.IdentityResolutionService;
import com.diogotoporcov.filemanager.api.identity.domain.User;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpringSecurityCurrentUserProviderTest {

    @Mock
    private IdentityResolutionService identityResolutionService;

    private AppProperties appProperties;
    private SpringSecurityCurrentUserProvider currentUserProvider;

    @BeforeEach
    void setUp() {
        appProperties = new AppProperties();
        currentUserProvider = new SpringSecurityCurrentUserProvider(identityResolutionService, appProperties);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getCurrentUser_MapsJwtUsingConfiguredClaims() {
        appProperties.getAuth().getClaims().setEmail("mail");
        appProperties.getAuth().getClaims().setFirstName("first");
        appProperties.getAuth().getClaims().setLastName("last");
        appProperties.getAuth().getClaims().setEmailVerified("verified");

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("subject-123")
                .claim("mail", " USER@Example.Com ")
                .claim("first", "First")
                .claim("last", "Last")
                .claim("verified", true)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(jwt, jwt.getTokenValue()));
        ExternalIdentityClaims expectedClaims = new ExternalIdentityClaims(
                "subject-123",
                " USER@Example.Com ",
                "First",
                "Last",
                true);
        User user = User.builder().id(UUID.randomUUID()).email("user@example.com").build();
        when(identityResolutionService.resolveUser(expectedClaims)).thenReturn(user);

        User result = currentUserProvider.getCurrentUser();

        assertThat(result).isEqualTo(user);
        verify(identityResolutionService).resolveUser(expectedClaims);
    }

    @Test
    void getCurrentUser_WithoutJwtPrincipal_ThrowsException() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("user", "password"));

        assertThatThrownBy(() -> currentUserProvider.getCurrentUser())
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class)
                .hasMessageContaining("No authenticated JWT principal found");
    }
}

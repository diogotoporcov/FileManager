package com.filemanager.api.service;

import com.filemanager.api.entity.User;
import com.filemanager.api.entity.UserIdentity;
import com.filemanager.api.config.AppProperties;
import com.filemanager.api.port.AuthenticatedIdentity;
import com.filemanager.api.port.IdentityProviderPort;
import com.filemanager.api.repository.UserIdentityRepository;
import com.filemanager.api.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdentityResolutionServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserIdentityRepository userIdentityRepository;

    @Mock
    private IdentityProviderPort identityProviderPort;

    private final AppProperties appProperties = new AppProperties();

    private IdentityResolutionService identityResolutionService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        identityResolutionService = new IdentityResolutionService(userRepository, userIdentityRepository, identityProviderPort, appProperties);
    }

    @Test
    void resolveUser_ExistingIdentity_ReturnsUser() {
        String subject = "sub-123";
        Jwt jwt = mock(Jwt.class);
        when(identityProviderPort.extractIdentity(jwt)).thenReturn(identity(subject, "test@example.com", null, null, null));

        User user = User.builder().id(UUID.randomUUID()).email("test@example.com").build();
        UserIdentity identity = UserIdentity.builder().user(user).build();

        when(userIdentityRepository.findByProviderAndProviderSubject("keycloak", subject))
                .thenReturn(Optional.of(identity));

        User result = identityResolutionService.resolveUser(jwt);

        assertThat(result).isEqualTo(user);
        verify(userIdentityRepository, never()).save(any());
    }

    @Test
    void resolveUser_NewIdentityExistingUser_RejectsAutoLinkByDefault() {
        String subject = "sub-123";
        String email = "test@example.com";
        Jwt jwt = mock(Jwt.class);
        when(identityProviderPort.extractIdentity(jwt)).thenReturn(identity(subject, email, null, null, true));

        User user = User.builder().id(UUID.randomUUID()).email(email).build();

        when(userIdentityRepository.findByProviderAndProviderSubject("keycloak", subject))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> identityResolutionService.resolveUser(jwt))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Automatic linking to an existing user is not enabled");
        verify(userIdentityRepository, never()).save(any(UserIdentity.class));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void resolveUser_NewIdentityExistingUser_LinksWhenTrustedProviderEnabled() {
        appProperties.getAuth().setAutoLinkExistingUsers(true);
        appProperties.getAuth().getTrustedAutoLinkProviders().add("keycloak");

        String subject = "sub-123";
        String email = "test@example.com";
        Jwt jwt = mock(Jwt.class);
        when(identityProviderPort.extractIdentity(jwt)).thenReturn(identity(subject, email, null, null, true));

        User user = User.builder().id(UUID.randomUUID()).email(email).build();

        when(userIdentityRepository.findByProviderAndProviderSubject("keycloak", subject))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        User result = identityResolutionService.resolveUser(jwt);

        assertThat(result).isEqualTo(user);
        verify(userIdentityRepository).save(any(UserIdentity.class));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void resolveUser_NewUser_ProvisionsAndReturnsUser() {
        String subject = "sub-123";
        String email = "new@example.com";
        Jwt jwt = mock(Jwt.class);
        when(identityProviderPort.extractIdentity(jwt)).thenReturn(identity(subject, email, "First", "Last", null));

        when(userIdentityRepository.findByProviderAndProviderSubject("keycloak", subject))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = identityResolutionService.resolveUser(jwt);

        assertThat(result.getEmail()).isEqualTo(email);
        assertThat(result.getFirstName()).isEqualTo("First");
        assertThat(result.getLastName()).isEqualTo("Last");
        verify(userRepository).save(any(User.class));
        verify(userIdentityRepository).save(any(UserIdentity.class));
    }

    @Test
    void resolveUser_MissingSubject_ThrowsException() {
        Jwt jwt = mock(Jwt.class);
        when(identityProviderPort.extractIdentity(jwt))
                .thenThrow(new IllegalArgumentException("Subject (sub) claim is missing or blank in JWT"));

        assertThatThrownBy(() -> identityResolutionService.resolveUser(jwt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Subject (sub) claim is missing or blank");
    }

    @Test
    void resolveUser_MissingEmail_ThrowsException() {
        Jwt jwt = mock(Jwt.class);
        when(identityProviderPort.extractIdentity(jwt))
                .thenThrow(new IllegalArgumentException("Email claim is missing or blank in JWT"));

        assertThatThrownBy(() -> identityResolutionService.resolveUser(jwt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email claim is missing or blank");
    }

    @Test
    void resolveUser_UnverifiedEmailLinking_ThrowsException() {
        String subject = "sub-123";
        String email = "test@example.com";
        Jwt jwt = mock(Jwt.class);
        when(identityProviderPort.extractIdentity(jwt)).thenReturn(identity(subject, email, null, null, false));

        User user = User.builder().id(UUID.randomUUID()).email(email).build();

        when(userIdentityRepository.findByProviderAndProviderSubject("keycloak", subject))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        appProperties.getAuth().setAutoLinkExistingUsers(true);
        appProperties.getAuth().getTrustedAutoLinkProviders().add("keycloak");

        assertThatThrownBy(() -> identityResolutionService.resolveUser(jwt))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot link identity to existing user with unverified email");
    }

    @Test
    void resolveUser_EmailNormalization_Works() {
        String subject = "sub-123";
        String normalizedEmail = "test@example.com";
        Jwt jwt = mock(Jwt.class);
        when(identityProviderPort.extractIdentity(jwt)).thenReturn(identity(subject, normalizedEmail, null, null, true));

        User user = User.builder().id(UUID.randomUUID()).email(normalizedEmail).build();

        when(userIdentityRepository.findByProviderAndProviderSubject("keycloak", subject))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail(normalizedEmail)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> identityResolutionService.resolveUser(jwt))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Automatic linking to an existing user is not enabled");
        verify(userRepository).findByEmail(normalizedEmail);
    }

    private AuthenticatedIdentity identity(String subject, String email, String firstName, String lastName, Boolean emailVerified) {
        return new AuthenticatedIdentity("keycloak", subject, email, firstName, lastName, emailVerified);
    }
}

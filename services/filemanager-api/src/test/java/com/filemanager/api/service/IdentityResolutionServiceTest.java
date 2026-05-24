package com.filemanager.api.service;

import com.filemanager.api.entity.User;
import com.filemanager.api.entity.UserIdentity;
import com.filemanager.api.repository.UserIdentityRepository;
import com.filemanager.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;

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

    @InjectMocks
    private IdentityResolutionService identityResolutionService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(identityResolutionService, "provider", "keycloak");
    }

    @Test
    void resolveUser_ExistingIdentity_ReturnsUser() {
        String subject = "sub-123";
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn(subject);

        User user = User.builder().id(UUID.randomUUID()).email("test@example.com").build();
        UserIdentity identity = UserIdentity.builder().user(user).build();

        when(userIdentityRepository.findByProviderAndProviderSubject("keycloak", subject))
                .thenReturn(Optional.of(identity));

        User result = identityResolutionService.resolveUser(jwt);

        assertThat(result).isEqualTo(user);
        verify(userIdentityRepository, never()).save(any());
    }

    @Test
    void resolveUser_NewIdentityExistingUser_LinksAndReturnsUser() {
        String subject = "sub-123";
        String email = "test@example.com";
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn(subject);
        when(jwt.getClaimAsString("email")).thenReturn(email);
        when(jwt.getClaimAsBoolean("email_verified")).thenReturn(true);

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
        when(jwt.getSubject()).thenReturn(subject);
        when(jwt.getClaimAsString("email")).thenReturn(email);
        when(jwt.getClaimAsString("given_name")).thenReturn("First");
        when(jwt.getClaimAsString("family_name")).thenReturn("Last");

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
        when(jwt.getSubject()).thenReturn(null);

        assertThatThrownBy(() -> identityResolutionService.resolveUser(jwt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Subject (sub) claim is missing or blank");
    }

    @Test
    void resolveUser_MissingEmail_ThrowsException() {
        String subject = "sub-123";
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn(subject);
        when(jwt.getClaimAsString("email")).thenReturn(null);

        when(userIdentityRepository.findByProviderAndProviderSubject("keycloak", subject))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> identityResolutionService.resolveUser(jwt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email claim is missing or blank");
    }

    @Test
    void resolveUser_UnverifiedEmailLinking_ThrowsException() {
        String subject = "sub-123";
        String email = "test@example.com";
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn(subject);
        when(jwt.getClaimAsString("email")).thenReturn(email);
        when(jwt.getClaimAsBoolean("email_verified")).thenReturn(false);

        User user = User.builder().id(UUID.randomUUID()).email(email).build();

        when(userIdentityRepository.findByProviderAndProviderSubject("keycloak", subject))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> identityResolutionService.resolveUser(jwt))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot link identity to existing user with unverified email");
    }

    @Test
    void resolveUser_EmailNormalization_Works() {
        String subject = "sub-123";
        String email = " TEST@Example.Com ";
        String normalizedEmail = "test@example.com";
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn(subject);
        when(jwt.getClaimAsString("email")).thenReturn(email);
        when(jwt.getClaimAsBoolean("email_verified")).thenReturn(true);

        User user = User.builder().id(UUID.randomUUID()).email(normalizedEmail).build();

        when(userIdentityRepository.findByProviderAndProviderSubject("keycloak", subject))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail(normalizedEmail)).thenReturn(Optional.of(user));

        User result = identityResolutionService.resolveUser(jwt);

        assertThat(result).isEqualTo(user);
        verify(userRepository).findByEmail(normalizedEmail);
    }
}

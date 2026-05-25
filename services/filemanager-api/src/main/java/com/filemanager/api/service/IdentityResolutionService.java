package com.filemanager.api.service;

import com.filemanager.api.entity.User;
import com.filemanager.api.entity.UserIdentity;
import com.filemanager.api.port.AuthenticatedIdentity;
import com.filemanager.api.port.IdentityProviderPort;
import com.filemanager.api.repository.UserIdentityRepository;
import com.filemanager.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IdentityResolutionService {

    private final UserRepository userRepository;
    private final UserIdentityRepository userIdentityRepository;
    private final IdentityProviderPort identityProviderPort;

    @Transactional
    public User resolveUser(Jwt jwt) {
        AuthenticatedIdentity identity = identityProviderPort.extractIdentity(jwt);

        return userIdentityRepository.findByProviderAndProviderSubject(identity.provider(), identity.subject())
                .map(UserIdentity::getUser)
                .orElseGet(() -> provisionUser(identity));
    }

    private User provisionUser(AuthenticatedIdentity identity) {
        return userRepository.findByEmail(identity.email())
                .map(user -> {
                    // Safety check: ensure the external email is verified before linking to an existing internal account.
                    if (identity.emailVerified() != null && !identity.emailVerified()) {
                        throw new IllegalStateException("Cannot link identity to existing user with unverified email");
                    }
                    return createIdentity(user, identity.provider(), identity.subject());
                })
                .orElseGet(() -> {
                    User newUser = User.builder()
                            .email(identity.email())
                            .firstName(identity.firstName())
                            .lastName(identity.lastName())
                            .build();
                    User savedUser = userRepository.save(newUser);
                    return createIdentity(savedUser, identity.provider(), identity.subject());
                });
    }

    private User createIdentity(User user, String provider, String subject) {
        UserIdentity identity = UserIdentity.builder()
                .user(user)
                .provider(provider)
                .providerSubject(subject)
                .build();
        userIdentityRepository.save(identity);
        return user;
    }
}

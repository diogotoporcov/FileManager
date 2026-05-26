package com.filemanager.api.service;

import com.filemanager.api.config.AppProperties;
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
    private final AppProperties appProperties;

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
                    if (!canAutoLinkExistingUser(identity)) {
                        throw new IllegalStateException("Automatic linking to an existing user is not enabled for this identity provider");
                    }
                    if (!Boolean.TRUE.equals(identity.emailVerified())) {
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

    private boolean canAutoLinkExistingUser(AuthenticatedIdentity identity) {
        return appProperties.getAuth().isAutoLinkExistingUsers()
                && appProperties.getAuth().getTrustedAutoLinkProviders().contains(identity.provider());
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

package com.diogotoporcov.filemanager.api.identity.application;

import com.diogotoporcov.filemanager.api.auth.domain.AuthenticatedIdentity;
import com.diogotoporcov.filemanager.api.auth.port.IdentityProviderPort;
import com.diogotoporcov.filemanager.api.config.AppProperties;
import com.diogotoporcov.filemanager.api.identity.domain.User;
import com.diogotoporcov.filemanager.api.identity.domain.UserIdentity;
import com.diogotoporcov.filemanager.api.identity.persistence.UserIdentityRepository;
import com.diogotoporcov.filemanager.api.identity.persistence.UserRepository;
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
        assertVerifiedEmailIfRequired(identity);

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

    private void assertVerifiedEmailIfRequired(AuthenticatedIdentity identity) {
        if (appProperties.getAuth().isRequireVerifiedEmail() && !Boolean.TRUE.equals(identity.emailVerified())) {
            throw new IllegalStateException("Cannot provision identity with unverified email");
        }
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

package com.filemanager.api.service;

import com.filemanager.api.config.AppProperties;
import com.filemanager.api.entity.User;
import com.filemanager.api.entity.UserIdentity;
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
    private final AppProperties appProperties;

    @Transactional
    public User resolveUser(Jwt jwt) {
        String subject = jwt.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("Subject (sub) claim is missing or blank in JWT");
        }

        String provider = appProperties.getAuth().getProviderName();
        return userIdentityRepository.findByProviderAndProviderSubject(provider, subject)
                .map(UserIdentity::getUser)
                .orElseGet(() -> provisionUser(jwt, provider, subject));
    }

    private User provisionUser(Jwt jwt, String provider, String subject) {
        AppProperties.Auth.Claims claims = appProperties.getAuth().getClaims();
        String rawEmail = jwt.getClaimAsString(claims.getEmail());
        if (rawEmail == null || rawEmail.isBlank()) {
            throw new IllegalArgumentException("Email claim is missing or blank in JWT");
        }
        String email = rawEmail.trim().toLowerCase();
        
        String firstName = jwt.getClaimAsString(claims.getFirstName());
        String lastName = jwt.getClaimAsString(claims.getLastName());
        Boolean emailVerified = jwt.getClaimAsBoolean(claims.getEmailVerified());

        return userRepository.findByEmail(email)
                .map(user -> {
                    // Safety check: ensure the external email is verified before linking to an existing internal account.
                    if (emailVerified != null && !emailVerified) {
                        throw new IllegalStateException("Cannot link identity to existing user with unverified email");
                    }
                    return createIdentity(user, provider, subject);
                })
                .orElseGet(() -> {
                    User newUser = User.builder()
                            .email(email)
                            .firstName(firstName)
                            .lastName(lastName)
                            .build();
                    User savedUser = userRepository.save(newUser);
                    return createIdentity(savedUser, provider, subject);
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

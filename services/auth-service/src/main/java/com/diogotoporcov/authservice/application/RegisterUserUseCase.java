package com.diogotoporcov.authservice.application;

import com.diogotoporcov.authservice.api.dto.AuthResponse;
import com.diogotoporcov.authservice.api.dto.RegisterRequest;
import com.diogotoporcov.authservice.error.EmailAlreadyInUseException;
import com.diogotoporcov.authservice.events.internal.UserRegisteredInternalEvent;
import com.diogotoporcov.authservice.token.JwtTokenService;
import com.diogotoporcov.authservice.token.RefreshTokenService;
import com.diogotoporcov.authservice.user.entity.LocalCredential;
import com.diogotoporcov.authservice.user.entity.UserIdentity;
import com.diogotoporcov.authservice.user.entity.UserStatus;
import com.diogotoporcov.authservice.user.repository.LocalCredentialRepository;
import com.diogotoporcov.authservice.user.repository.UserIdentityRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
public class RegisterUserUseCase {

    private final UserIdentityRepository users;
    private final LocalCredentialRepository credentials;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService tokenService;
    private final RefreshTokenService refreshTokens;
    private final ApplicationEventPublisher appEvents;

    public RegisterUserUseCase(
            UserIdentityRepository users,
            LocalCredentialRepository credentials,
            PasswordEncoder passwordEncoder,
            JwtTokenService tokenService,
            RefreshTokenService refreshTokens,
            ApplicationEventPublisher appEvents
    ) {
        this.users = users;
        this.credentials = credentials;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.refreshTokens = refreshTokens;
        this.appEvents = appEvents;
    }

    @Transactional
    public AuthResponse execute(RegisterRequest req) {
        String email = normalizeEmail(req.email());

        if (users.existsByEmailIgnoreCase(email)) {
            throw new EmailAlreadyInUseException(email);
        }

        UUID userId = UUID.randomUUID();
        UserIdentity user = new UserIdentity(userId, email, UserStatus.ACTIVE);
        users.save(user);

        String hash = passwordEncoder.encode(req.password());
        credentials.save(new LocalCredential(user, hash));

        appEvents.publishEvent(new UserRegisteredInternalEvent(userId, email));

        var access = tokenService.mintAccessToken(userId, email);
        String refresh = refreshTokens.issueNew(userId);

        return new AuthResponse(access.accessToken(), access.tokenType(), access.expiresIn(), refresh);
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}

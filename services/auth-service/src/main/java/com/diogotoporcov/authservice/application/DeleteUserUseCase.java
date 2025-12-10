package com.diogotoporcov.authservice.application;

import com.diogotoporcov.authservice.error.UserNotFoundException;
import com.diogotoporcov.authservice.events.internal.UserDeletedInternalEvent;
import com.diogotoporcov.authservice.token.RefreshTokenService;
import com.diogotoporcov.authservice.user.repository.LocalCredentialRepository;
import com.diogotoporcov.authservice.user.repository.UserIdentityRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class DeleteUserUseCase {

    private final UserIdentityRepository users;
    private final LocalCredentialRepository credentials;
    private final RefreshTokenService refreshTokens;
    private final ApplicationEventPublisher appEvents;

    public DeleteUserUseCase(
            UserIdentityRepository users,
            LocalCredentialRepository credentials,
            RefreshTokenService refreshTokens,
            ApplicationEventPublisher appEvents
    ) {
        this.users = users;
        this.credentials = credentials;
        this.refreshTokens = refreshTokens;
        this.appEvents = appEvents;
    }

    @Transactional
    public void execute(UUID userId) {

        if (!users.existsById(userId)) {
            throw new UserNotFoundException(userId);
        }

        refreshTokens.revokeAllForUser(userId);
        credentials.findById(userId).ifPresent(credentials::delete);
        users.deleteById(userId);

        appEvents.publishEvent(new UserDeletedInternalEvent(userId));
    }
}

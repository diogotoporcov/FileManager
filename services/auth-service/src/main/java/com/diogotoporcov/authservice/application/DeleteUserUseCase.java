package com.diogotoporcov.authservice.application;

import com.diogotoporcov.authservice.error.UserNotFoundException;
import com.diogotoporcov.authservice.events.internal.UserDeletedInternalEvent;
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
    private final ApplicationEventPublisher appEvents;

    public DeleteUserUseCase(
            UserIdentityRepository users,
            LocalCredentialRepository credentials,
            ApplicationEventPublisher appEvents
    ) {
        this.users = users;
        this.credentials = credentials;
        this.appEvents = appEvents;
    }

    @Transactional
    public void execute(UUID userId) {
        if (!users.existsById(userId)) {
            throw new UserNotFoundException(userId);
        }

        // Remove credenciais (se existirem)
        credentials.findById(userId).ifPresent(credentials::delete);

        // Remove identidade
        users.deleteById(userId);

        // Publica evento interno; envio ao Rabbit ocorrerá após commit via @TransactionalEventListener
        appEvents.publishEvent(new UserDeletedInternalEvent(userId));
    }
}

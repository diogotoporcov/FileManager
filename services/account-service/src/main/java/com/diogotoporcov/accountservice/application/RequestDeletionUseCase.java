package com.diogotoporcov.accountservice.application;

import com.diogotoporcov.accountservice.events.outbound.UserDeletionPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class RequestDeletionUseCase {

    private final UserDeletionPublisher publisher;

    public RequestDeletionUseCase(UserDeletionPublisher publisher) {
        this.publisher = publisher;
    }

    @Transactional
    public void execute(UUID userId) {
        publisher.publish(userId);
    }
}

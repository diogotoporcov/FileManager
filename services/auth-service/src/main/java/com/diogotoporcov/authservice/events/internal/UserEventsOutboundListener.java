package com.diogotoporcov.authservice.events.internal;

import com.diogotoporcov.authservice.events.UserEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class UserEventsOutboundListener {

    private final UserEventPublisher publisher;

    public UserEventsOutboundListener(UserEventPublisher publisher) {
        this.publisher = publisher;
    }

    @TransactionalEventListener
    public void onUserRegistered(UserRegisteredInternalEvent event) {
        publisher.publishUserRegistered(event.userId(), event.email());
    }

    @TransactionalEventListener
    public void onUserDeleted(UserDeletedInternalEvent event) {
        publisher.publishUserDeleted(event.userId());
    }
}

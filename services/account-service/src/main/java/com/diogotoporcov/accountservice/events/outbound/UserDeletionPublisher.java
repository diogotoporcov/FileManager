package com.diogotoporcov.accountservice.events.outbound;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class UserDeletionPublisher {

    private final RabbitTemplate rabbit;

    public UserDeletionPublisher(RabbitTemplate rabbit) {
        this.rabbit = rabbit;
    }

    public void publish(UUID userId) {
        rabbit.convertAndSend(
                DeletionRequestRabbitConfig.USER_DELETION_REQUEST_EXCHANGE,
                DeletionRequestRabbitConfig.USER_DELETION_REQUEST_KEY,
                new UserDeletionRequestedEvent(userId)
        );
    }
}

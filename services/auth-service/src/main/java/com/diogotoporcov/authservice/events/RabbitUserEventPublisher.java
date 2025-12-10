package com.diogotoporcov.authservice.events;

import com.diogotoporcov.authservice.events.delete.DeleteRabbitConfig;
import com.diogotoporcov.authservice.events.delete.UserDeletedEvent;
import com.diogotoporcov.authservice.events.register.RegisterRabbitConfig;
import com.diogotoporcov.authservice.events.register.UserRegisteredEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class RabbitUserEventPublisher implements UserEventPublisher {

    private final RabbitTemplate rabbit;

    public RabbitUserEventPublisher(RabbitTemplate rabbit) {
        this.rabbit = rabbit;
    }

    @Override
    public void publishUserRegistered(UUID userId, String email) {
        rabbit.convertAndSend(
                RegisterRabbitConfig.USER_REGISTER_EXCHANGE,
                RegisterRabbitConfig.USER_REGISTER_ROUTING_KEY,
                new UserRegisteredEvent(userId.toString(), email)
        );
    }

    @Override
    public void publishUserDeleted(UUID userId) {
        rabbit.convertAndSend(
                DeleteRabbitConfig.USER_DELETE_EXCHANGE,
                DeleteRabbitConfig.USER_DELETE_ROUTING_KEY,
                new UserDeletedEvent(userId.toString())
        );
    }
}

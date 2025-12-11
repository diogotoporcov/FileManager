package com.diogotoporcov.accountservice.events.inbound;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AuthEventsRabbitConfig {

    public static final String USER_REGISTER_EXCHANGE = "user.register.exchange";
    public static final String USER_REGISTER_KEY = "user.register";
    public static final String ACCOUNT_REGISTER_QUEUE = "account.user.register.queue";

    public static final String USER_DELETED_EXCHANGE = "user.deleted.exchange";
    public static final String USER_DELETED_KEY = "user.deleted";
    public static final String ACCOUNT_DELETED_QUEUE = "account.user.deleted.queue";

    @Bean
    Declarables authInboundDeclarables() {
        DirectExchange regEx = new DirectExchange(USER_REGISTER_EXCHANGE, true, false);
        Queue regQ = QueueBuilder.durable(ACCOUNT_REGISTER_QUEUE).build();
        Binding regB = BindingBuilder.bind(regQ).to(regEx).with(USER_REGISTER_KEY);

        DirectExchange delEx = new DirectExchange(USER_DELETED_EXCHANGE, true, false);
        Queue delQ = QueueBuilder.durable(ACCOUNT_DELETED_QUEUE).build();
        Binding delB = BindingBuilder.bind(delQ).to(delEx).with(USER_DELETED_KEY);

        return new Declarables(regEx, regQ, regB, delEx, delQ, delB);
    }
}

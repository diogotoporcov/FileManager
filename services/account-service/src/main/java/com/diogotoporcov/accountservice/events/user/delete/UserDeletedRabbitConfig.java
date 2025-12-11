package com.diogotoporcov.accountservice.events.user.delete;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UserDeletedRabbitConfig {

    public static final String USER_DELETE_EXCHANGE = "user.delete.exchange";
    public static final String USER_DELETE_KEY = "user.delete";
    public static final String ACCOUNT_DELETE_QUEUE = "account.user.delete.queue";

    @Bean
    public Declarables userDeletedDeclarables() {
        DirectExchange exchange = new DirectExchange(USER_DELETE_EXCHANGE, true, false);
        Queue queue = QueueBuilder.durable(ACCOUNT_DELETE_QUEUE).build();
        Binding binding = BindingBuilder.bind(queue).to(exchange).with(USER_DELETE_KEY);

        return new Declarables(exchange, queue, binding);
    }
}

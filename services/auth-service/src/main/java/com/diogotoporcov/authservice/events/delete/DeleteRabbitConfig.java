package com.diogotoporcov.authservice.events.delete;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DeleteRabbitConfig {

    public static final String USER_DELETE_EXCHANGE = "user.delete.exchange";
    public static final String USER_DELETE_QUEUE = "user.delete.queue";
    public static final String USER_DELETE_ROUTING_KEY = "user.delete";

    @Bean
    public Queue userDeleteQueue() {
        return new Queue(USER_DELETE_QUEUE, true);
    }

    @Bean
    public DirectExchange userDeleteExchange() {
        return new DirectExchange(USER_DELETE_EXCHANGE, true, false);
    }

    @Bean
    public Binding userDeleteBinding() {
        return BindingBuilder.bind(userDeleteQueue())
                .to(userDeleteExchange())
                .with(USER_DELETE_ROUTING_KEY);
    }
}

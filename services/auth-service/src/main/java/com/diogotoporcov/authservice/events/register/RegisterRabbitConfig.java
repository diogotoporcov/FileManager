package com.diogotoporcov.authservice.events.register;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RegisterRabbitConfig {

    public static final String USER_REGISTER_EXCHANGE = "user.register.exchange";
    public static final String USER_REGISTER_QUEUE = "user.register.queue";
    public static final String USER_REGISTER_ROUTING_KEY = "user.register";

    @Bean
    public Queue userRegisterQueue() {
        return new Queue(USER_REGISTER_QUEUE, true);
    }

    @Bean
    public DirectExchange userRegisterExchange() {
        return new DirectExchange(USER_REGISTER_EXCHANGE, true, false);
    }

    @Bean
    public Binding userRegisterBinding() {
        return BindingBuilder.bind(userRegisterQueue())
                .to(userRegisterExchange())
                .with(USER_REGISTER_ROUTING_KEY);
    }
}

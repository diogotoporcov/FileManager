package com.diogotoporcov.accountservice.events.user.register;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UserRegisterRabbitConfig {

    public static final String USER_REGISTER_EXCHANGE = "user.register.exchange";
    public static final String USER_REGISTER_KEY = "user.register";
    public static final String ACCOUNT_REGISTER_QUEUE = "account.user.register.queue";

    @Bean
    public Declarables userRegisterDeclarables() {
        DirectExchange exchange = new DirectExchange(USER_REGISTER_EXCHANGE, true, false);
        Queue queue = QueueBuilder.durable(ACCOUNT_REGISTER_QUEUE).build();
        Binding binding = BindingBuilder.bind(queue).to(exchange).with(USER_REGISTER_KEY);

        return new Declarables(exchange, queue, binding);
    }
}

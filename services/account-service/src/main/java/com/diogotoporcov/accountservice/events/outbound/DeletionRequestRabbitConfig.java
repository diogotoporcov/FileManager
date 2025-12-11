package com.diogotoporcov.accountservice.events.outbound;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DeletionRequestRabbitConfig {

    public static final String USER_DELETION_REQUEST_EXCHANGE = "user.deletion.request.exchange";
    public static final String USER_DELETION_REQUEST_KEY = "user.deletion.request";

    @Bean
    DirectExchange deletionRequestExchange() {
        return new DirectExchange(USER_DELETION_REQUEST_EXCHANGE, true, false);
    }
}

package com.diogotoporcov.accountservice.events.user.register;

import com.diogotoporcov.accountservice.application.CreateAccountUseCase;
import com.diogotoporcov.accountservice.events.user.internal.UserRegisteredInternalEvent;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import static com.diogotoporcov.accountservice.events.user.register.UserRegisterRabbitConfig.ACCOUNT_REGISTER_QUEUE;

@Component
public class UserRegisteredEventsListener {

    private final CreateAccountUseCase createAccount;

    public UserRegisteredEventsListener(CreateAccountUseCase createAccount) {
        this.createAccount = createAccount;
    }

    @Transactional
    @RabbitListener(queues = ACCOUNT_REGISTER_QUEUE)
    public void onUserRegistered(UserRegisteredInternalEvent event) {
        System.out.println("Received user registered event: " + event);
        createAccount.execute(event.userIdAsUuid());
    }
}

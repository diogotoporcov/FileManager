package com.diogotoporcov.accountservice.events.user.delete;

import com.diogotoporcov.accountservice.events.user.internal.UserDeletedInternalEvent;
import com.diogotoporcov.accountservice.account.repository.UserAccountRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import static com.diogotoporcov.accountservice.events.user.delete.UserDeletedRabbitConfig.ACCOUNT_DELETE_QUEUE;

@Component
public class UserDeletedEventsListener {

    private final UserAccountRepository accounts;

    public UserDeletedEventsListener(UserAccountRepository accounts) {
        this.accounts = accounts;
    }

    @Transactional
    @RabbitListener(queues = ACCOUNT_DELETE_QUEUE)
    public void onUserDeleted(UserDeletedInternalEvent event) {
        System.out.println("Received user deleted event: " + event.toString());
        accounts.deleteById(event.userIdAsUuid());
    }
}

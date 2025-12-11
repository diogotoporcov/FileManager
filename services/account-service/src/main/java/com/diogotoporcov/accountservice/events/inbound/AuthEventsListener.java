package com.diogotoporcov.accountservice.events.inbound;

import com.diogotoporcov.accountservice.application.GetMyProfileUseCase;
import com.diogotoporcov.accountservice.profile.repository.UserProfileRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AuthEventsListener {

    private final GetMyProfileUseCase getMyProfile;
    private final UserProfileRepository profiles;

    public AuthEventsListener(GetMyProfileUseCase getMyProfile, UserProfileRepository profiles) {
        this.getMyProfile = getMyProfile;
        this.profiles = profiles;
    }

    @Transactional
    @RabbitListener(queues = AuthEventsRabbitConfig.ACCOUNT_REGISTER_QUEUE)
    public void onUserRegistered(UserRegisteredInternalEvent event) {
        getMyProfile.execute(event.userIdAsUuid(), event.email());
    }

    @Transactional
    @RabbitListener(queues = AuthEventsRabbitConfig.ACCOUNT_DELETED_QUEUE)
    public void onUserDeleted(UserDeletedInternalEvent event) {
        profiles.deleteById(event.userIdAsUuid());
    }
}

package com.diogotoporcov.accountservice.application;

import com.diogotoporcov.accountservice.profile.UsernameGenerator;
import com.diogotoporcov.accountservice.profile.entity.AccountStatus;
import com.diogotoporcov.accountservice.profile.entity.UserProfile;
import com.diogotoporcov.accountservice.profile.repository.UserProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class GetMyProfileUseCase {

    private final UserProfileRepository profiles;
    private final UsernameGenerator usernameGenerator;

    public GetMyProfileUseCase(UserProfileRepository profiles, UsernameGenerator usernameGenerator) {
        this.profiles = profiles;
        this.usernameGenerator = usernameGenerator;
    }

    @Transactional
    public UserProfile execute(UUID userId, String email) {
        return profiles.findById(userId).orElseGet(() -> {
            String username = uniqueUsername();
            return profiles.save(new UserProfile(
                    userId,
                    email,
                    username,
                    null,
                    null,
                    AccountStatus.INACTIVE
            ));
        });
    }

    private String uniqueUsername() {
        for (int i = 0; i < 8; i++) {
            String candidate = usernameGenerator.generate();
            if (!profiles.existsByUsername(candidate)) return candidate;
        }
        throw new IllegalStateException("Could not generate unique username");
    }
}

package com.diogotoporcov.accountservice.application;

import com.diogotoporcov.accountservice.account.UsernameGenerator;
import com.diogotoporcov.accountservice.account.entity.AccountStatus;
import com.diogotoporcov.accountservice.account.entity.UserAccount;
import com.diogotoporcov.accountservice.account.repository.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CreateAccountUseCase {

    private final UserAccountRepository accounts;
    private final UsernameGenerator usernameGenerator;

    public CreateAccountUseCase(UserAccountRepository accounts, UsernameGenerator usernameGenerator) {
        this.accounts = accounts;
        this.usernameGenerator = usernameGenerator;
    }

    @Transactional
    public UserAccount execute(UUID userId) {

        if (accounts.existsById(userId)) {
            throw new IllegalStateException("Account already exists for userId: " + userId);
        }

        String username = generateUniqueUsername();

        UserAccount newAccount = new UserAccount(
                userId,
                username,
                null,
                null,
                AccountStatus.INACTIVE
        );

        return accounts.save(newAccount);
    }

    private String generateUniqueUsername() {
        for (int i = 0; i < 8; i++) {
            String candidate = usernameGenerator.generate();
            if (!accounts.existsByUsername(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not generate unique username");
    }
}

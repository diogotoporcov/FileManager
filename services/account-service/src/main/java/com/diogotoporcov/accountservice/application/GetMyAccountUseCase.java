package com.diogotoporcov.accountservice.application;

import com.diogotoporcov.accountservice.account.entity.UserAccount;
import com.diogotoporcov.accountservice.account.repository.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class GetMyAccountUseCase {

    private final UserAccountRepository accounts;

    public GetMyAccountUseCase(UserAccountRepository accounts) {
        this.accounts = accounts;
    }

    @Transactional(readOnly = true)
    public UserAccount execute(UUID userId) {
        return accounts.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found for userId: " + userId));
    }
}


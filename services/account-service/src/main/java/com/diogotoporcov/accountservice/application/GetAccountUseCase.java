package com.diogotoporcov.accountservice.application;

import com.diogotoporcov.accountservice.account.entity.UserAccount;
import com.diogotoporcov.accountservice.account.repository.UserAccountRepository;
import com.diogotoporcov.accountservice.error.AccountNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class GetAccountUseCase {

    private final UserAccountRepository accounts;

    public GetAccountUseCase(UserAccountRepository accounts) {
        this.accounts = accounts;
    }

    @Transactional(readOnly = true)
    public UserAccount execute(UUID userId) {
        return accounts.findById(userId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found for userId: " + userId));
    }

    @Transactional(readOnly = true)
    public UserAccount execute(String username) {
        return accounts.findByUsername(username)
                .orElseThrow(() -> new AccountNotFoundException("Account not found for username: " + username));
    }
}

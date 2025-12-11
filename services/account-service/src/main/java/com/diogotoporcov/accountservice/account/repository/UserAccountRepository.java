package com.diogotoporcov.accountservice.account.repository;

import com.diogotoporcov.accountservice.account.entity.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {
    boolean existsByUsername(String username);
    boolean existsByUsernameAndUserIdNot(String username, UUID userId);

    Optional<UserAccount> findByUsername(String username);
}

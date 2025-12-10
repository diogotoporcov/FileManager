package com.diogotoporcov.authservice.user.repository;

import com.diogotoporcov.authservice.user.entity.UserIdentity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserIdentityRepository extends JpaRepository<UserIdentity, UUID> {

    boolean existsByEmailIgnoreCase(String email);

    Optional<UserIdentity> findByEmailIgnoreCase(String email);
}

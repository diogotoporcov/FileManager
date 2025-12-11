package com.diogotoporcov.accountservice.profile.repository;

import com.diogotoporcov.accountservice.profile.entity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {
    boolean existsByUsername(String username);
    boolean existsByUsernameAndUserIdNot(String username, UUID userId);
}

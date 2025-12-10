package com.diogotoporcov.authservice.session.repository;

import com.diogotoporcov.authservice.session.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {

    List<UserSession> findByUserIdAndRevokedAtIsNullOrderByLastUsedAtDesc(UUID userId);

    Optional<UserSession> findByIdAndUserId(UUID id, UUID userId);

    @Modifying
    @Query("""
           update UserSession s
           set s.revokedAt = :now
           where s.userId = :userId and s.revokedAt is null
           """)
    int revokeAllForUser(UUID userId, Instant now);
}

package com.diogotoporcov.authservice.token.repository;

import com.diogotoporcov.authservice.token.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    @Modifying
    @Query("""
           update RefreshToken t
           set t.revokedAt = :now
           where t.userId = :userId and t.revokedAt is null
           """)
    int revokeAllForUser(UUID userId, Instant now);

    @Modifying
    @Query("""
           update RefreshToken t
           set t.revokedAt = :now
           where t.familyId = :familyId and t.revokedAt is null
           """)
    int revokeFamily(UUID familyId, Instant now);
}

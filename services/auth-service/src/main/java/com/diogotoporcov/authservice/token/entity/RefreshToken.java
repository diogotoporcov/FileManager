package com.diogotoporcov.authservice.token.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_token", indexes = {
        @Index(name = "ix_refresh_token_user", columnList = "user_id"),
        @Index(name = "ix_refresh_token_family", columnList = "family_id")
})
public class RefreshToken {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "family_id", nullable = false, updatable = false)
    private UUID familyId;

    @Column(name = "secret_hash", nullable = false, length = 64)
    private String secretHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "replaced_by")
    private UUID replacedBy;

    protected RefreshToken() {}

    public RefreshToken(UUID id, UUID userId, UUID familyId, String secretHash, Instant expiresAt) {
        this.id = id;
        this.userId = userId;
        this.familyId = familyId;
        this.secretHash = secretHash;
        this.expiresAt = expiresAt;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getFamilyId() { return familyId; }
    public String getSecretHash() { return secretHash; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public UUID getReplacedBy() { return replacedBy; }

    public boolean isExpired(Instant now) {
        return now.isAfter(expiresAt);
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean hasBeenReplaced() {
        return replacedBy != null;
    }

    public void revoke(Instant when) {
        this.revokedAt = when;
    }

    public void replaceWith(UUID newTokenId, Instant when) {
        this.replacedBy = newTokenId;
        this.revokedAt = when; // invalidate old token immediately
    }
}

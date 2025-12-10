package com.diogotoporcov.authservice.session.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_session", indexes = {
        @Index(name = "ix_user_session_user", columnList = "user_id"),
        @Index(name = "ix_user_session_revoked", columnList = "revoked_at")
})
public class UserSession {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "device_name", length = 120)
    private String deviceName;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_used_at", nullable = false)
    private Instant lastUsedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected UserSession() {}

    public UserSession(UUID id, UUID userId, String deviceName, String userAgent, String ipAddress) {
        this.id = id;
        this.userId = userId;
        this.deviceName = deviceName;
        this.userAgent = userAgent;
        this.ipAddress = ipAddress;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.lastUsedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getDeviceName() { return deviceName; }
    public String getUserAgent() { return userAgent; }
    public String getIpAddress() { return ipAddress; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastUsedAt() { return lastUsedAt; }
    public Instant getRevokedAt() { return revokedAt; }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public void touch(String userAgent, String ipAddress) {
        this.lastUsedAt = Instant.now();
        if (userAgent != null && !userAgent.isBlank()) this.userAgent = userAgent;
        if (ipAddress != null && !ipAddress.isBlank()) this.ipAddress = ipAddress;
    }

    public void revoke(Instant when) {
        this.revokedAt = when;
    }
}

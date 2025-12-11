package com.diogotoporcov.accountservice.profile.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "user_profile",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_user_profile_username", columnNames = "username"),
                @UniqueConstraint(name = "uk_user_profile_email", columnNames = "email")
        },
        indexes = {
                @Index(name = "ix_user_profile_username", columnList = "username"),
                @Index(name = "ix_user_profile_email", columnList = "email")
        }
)
public class UserProfile {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "email", nullable = false, updatable = false, length = 254)
    private String email;

    @Column(name = "full_name", length = 200)
    private String fullName;

    @Column(name = "username", nullable = false, length = 40)
    private String username;

    @Column(name = "locale", nullable = false, length = 10)
    private String locale;

    @Column(name = "timezone", nullable = false, length = 64)
    private String timezone;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private AccountStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserProfile() {}

    public UserProfile(UUID userId, String email, String username, String locale, String timezone, AccountStatus status) {
        this.userId = userId;
        this.email = email;
        this.username = username;
        this.locale = locale;
        this.timezone = timezone;
        this.status = status;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getUserId() { return userId; }
    public String getEmail() { return email; }
    public String getFullName() { return fullName; }
    public String getUsername() { return username; }
    public String getLocale() { return locale; }
    public String getTimezone() { return timezone; }
    public AccountStatus getStatus() { return status; }

    public void setFullName(String fullName) { this.fullName = fullName; }
    public void setUsername(String username) { this.username = username; }
    public void setLocale(String locale) { this.locale = locale; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    public void setStatus(AccountStatus status) { this.status = status; }
}

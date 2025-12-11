package com.diogotoporcov.accountservice.account.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_account")
public class UserAccount {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "full_name", length = 200)
    private String fullName;

    @Column(name = "username", unique = true, nullable = false, length = 40)
    private String username;

    @Column(name = "locale", length = 10)
    private String locale;

    @Column(name = "timezone", length = 64)
    private String timezone;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private AccountStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserAccount() {}

    public UserAccount(UUID userId, String username, String locale, String timezone, AccountStatus status) {
        this.userId = userId;
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

    public boolean isAccountComplete() {
        return notBlank(fullName)
                && notBlank(username)
                && notBlank(locale)
                && notBlank(timezone);
    }

    private static boolean notBlank(String string) {
        return string != null && !string.isBlank();
    }
}

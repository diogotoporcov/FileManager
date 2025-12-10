package com.diogotoporcov.authservice.application;

import com.diogotoporcov.authservice.token.RefreshTokenService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class RevokeSessionUseCase {

    private final RefreshTokenService refreshTokens;

    public RevokeSessionUseCase(RefreshTokenService refreshTokens) {
        this.refreshTokens = refreshTokens;
    }

    @Transactional
    public void execute(UUID userId, UUID sessionId) {
        refreshTokens.revokeSession(userId, sessionId);
    }
}

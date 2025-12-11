package com.diogotoporcov.authservice.application;

import com.diogotoporcov.authservice.api.RequestContextExtractor.SessionContext;
import com.diogotoporcov.authservice.api.dto.AuthResponse;
import com.diogotoporcov.authservice.api.dto.RefreshRequest;
import com.diogotoporcov.authservice.token.JwtTokenService;
import com.diogotoporcov.authservice.token.RefreshTokenService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenUseCase {

    private final RefreshTokenService refreshTokens;
    private final JwtTokenService jwt;

    public RefreshTokenUseCase(RefreshTokenService refreshTokens, JwtTokenService jwt) {
        this.refreshTokens = refreshTokens;
        this.jwt = jwt;
    }

    @Transactional
    public AuthResponse execute(RefreshRequest req, SessionContext ctx) {
        var rotation = refreshTokens.rotate(req.refreshToken(), ctx);

        var access = jwt.mintAccessToken(rotation.userId(), rotation.sessionId());
        return new AuthResponse(access.accessToken(), access.tokenType(), access.expiresIn(), rotation.newRefreshToken());
    }
}

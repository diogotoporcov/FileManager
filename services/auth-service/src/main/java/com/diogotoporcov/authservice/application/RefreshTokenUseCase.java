package com.diogotoporcov.authservice.application;

import com.diogotoporcov.authservice.api.dto.AuthResponse;
import com.diogotoporcov.authservice.api.dto.RefreshRequest;
import com.diogotoporcov.authservice.token.JwtTokenService;
import com.diogotoporcov.authservice.token.RefreshTokenService;
import com.diogotoporcov.authservice.user.repository.UserIdentityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenUseCase {

    private final RefreshTokenService refreshTokens;
    private final UserIdentityRepository users;
    private final JwtTokenService jwt;

    public RefreshTokenUseCase(RefreshTokenService refreshTokens, UserIdentityRepository users, JwtTokenService jwt) {
        this.refreshTokens = refreshTokens;
        this.users = users;
        this.jwt = jwt;
    }

    @Transactional
    public AuthResponse execute(RefreshRequest req) {
        var rotation = refreshTokens.rotate(req.refreshToken());

        String email = users.findById(rotation.userId())
                .orElseThrow()
                .getEmail();

        var access = jwt.mintAccessToken(rotation.userId(), email);
        return new AuthResponse(access.accessToken(), access.tokenType(), access.expiresIn(), rotation.newRefreshToken());
    }
}

package com.diogotoporcov.authservice.application;

import com.diogotoporcov.authservice.api.dto.AuthResponse;
import com.diogotoporcov.authservice.api.dto.LoginRequest;
import com.diogotoporcov.authservice.error.InvalidCredentialsException;
import com.diogotoporcov.authservice.token.JwtTokenService;
import com.diogotoporcov.authservice.token.RefreshTokenService;
import com.diogotoporcov.authservice.user.entity.LocalCredential;
import com.diogotoporcov.authservice.user.entity.UserIdentity;
import com.diogotoporcov.authservice.user.repository.LocalCredentialRepository;
import com.diogotoporcov.authservice.user.repository.UserIdentityRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class LoginUserUseCase {

    private final UserIdentityRepository users;
    private final LocalCredentialRepository credentials;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService tokenService;
    private final RefreshTokenService refreshTokens;

    public LoginUserUseCase(
            UserIdentityRepository users,
            LocalCredentialRepository credentials,
            PasswordEncoder passwordEncoder,
            JwtTokenService tokenService,
            RefreshTokenService refreshTokens
    ) {
        this.users = users;
        this.credentials = credentials;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.refreshTokens = refreshTokens;
    }

    @Transactional
    public AuthResponse execute(LoginRequest req) {
        String email = normalizeEmail(req.email());

        UserIdentity user = users.findByEmailIgnoreCase(email)
                .orElseThrow(InvalidCredentialsException::new);

        if (!user.isActive()) throw new InvalidCredentialsException();

        LocalCredential cred = credentials.findById(user.getId())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(req.password(), cred.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        var access = tokenService.mintAccessToken(user.getId(), user.getEmail());
        String refresh = refreshTokens.issueNew(user.getId());

        return new AuthResponse(access.accessToken(), access.tokenType(), access.expiresIn(), refresh);
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}

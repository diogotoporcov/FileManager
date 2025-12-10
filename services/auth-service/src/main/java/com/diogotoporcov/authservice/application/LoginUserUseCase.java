package com.diogotoporcov.authservice.application;

import com.diogotoporcov.authservice.api.dto.AuthResponse;
import com.diogotoporcov.authservice.api.dto.LoginRequest;
import com.diogotoporcov.authservice.error.InvalidCredentialsException;
import com.diogotoporcov.authservice.token.JwtTokenService;
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

    public LoginUserUseCase(
            UserIdentityRepository users,
            LocalCredentialRepository credentials,
            PasswordEncoder passwordEncoder,
            JwtTokenService tokenService
    ) {
        this.users = users;
        this.credentials = credentials;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
    }

    @Transactional(readOnly = true)
    public AuthResponse execute(LoginRequest req) {
        String email = normalizeEmail(req.email());

        UserIdentity user = users.findByEmailIgnoreCase(email)
                .orElseThrow(InvalidCredentialsException::new);

        if (!user.isActive()) {
            throw new InvalidCredentialsException();
        }

        LocalCredential cred = credentials.findById(user.getId())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(req.password(), cred.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        JwtTokenService.TokenPair token = tokenService.mintAccessToken(user.getId(), user.getEmail());
        return new AuthResponse(token.accessToken(), token.tokenType(), token.expiresIn());
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}

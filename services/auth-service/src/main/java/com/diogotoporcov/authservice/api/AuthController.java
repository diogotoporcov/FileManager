package com.diogotoporcov.authservice.api;

import com.diogotoporcov.authservice.api.dto.AuthResponse;
import com.diogotoporcov.authservice.api.dto.LoginRequest;
import com.diogotoporcov.authservice.api.dto.RefreshRequest;
import com.diogotoporcov.authservice.api.dto.RegisterRequest;
import com.diogotoporcov.authservice.application.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final RegisterUserUseCase registerUser;
    private final LoginUserUseCase loginUser;
    private final RefreshTokenUseCase refreshToken;
    private final LogoutUseCase logout;
    private final DeleteUserUseCase deleteUser;

    public AuthController(
            RegisterUserUseCase registerUser,
            LoginUserUseCase loginUser,
            RefreshTokenUseCase refreshToken,
            LogoutUseCase logout,
            DeleteUserUseCase deleteUser
    ) {
        this.registerUser = registerUser;
        this.loginUser = loginUser;
        this.refreshToken = refreshToken;
        this.logout = logout;
        this.deleteUser = deleteUser;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return registerUser.execute(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return loginUser.execute(request);
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return refreshToken.execute(request);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        logout.execute(userId);
    }

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMe(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        deleteUser.execute(userId);
    }
}

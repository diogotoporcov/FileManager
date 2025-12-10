package com.diogotoporcov.authservice.api;

import com.diogotoporcov.authservice.api.RequestContextExtractor.SessionContext;
import com.diogotoporcov.authservice.api.dto.*;
import com.diogotoporcov.authservice.application.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final RequestContextExtractor ctxExtractor;

    private final RegisterUserUseCase registerUser;
    private final LoginUserUseCase loginUser;
    private final RefreshTokenUseCase refreshToken;
    private final LogoutUseCase logout;
    private final DeleteUserUseCase deleteUser;

    private final ListSessionsUseCase listSessions;
    private final RevokeSessionUseCase revokeSession;

    public AuthController(
            RequestContextExtractor ctxExtractor,
            RegisterUserUseCase registerUser,
            LoginUserUseCase loginUser,
            RefreshTokenUseCase refreshToken,
            LogoutUseCase logout,
            DeleteUserUseCase deleteUser,
            ListSessionsUseCase listSessions,
            RevokeSessionUseCase revokeSession
    ) {
        this.ctxExtractor = ctxExtractor;
        this.registerUser = registerUser;
        this.loginUser = loginUser;
        this.refreshToken = refreshToken;
        this.logout = logout;
        this.deleteUser = deleteUser;
        this.listSessions = listSessions;
        this.revokeSession = revokeSession;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
        SessionContext ctx = ctxExtractor.extract(http, request.deviceName());
        return registerUser.execute(request, ctx);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        SessionContext ctx = ctxExtractor.extract(http, request.deviceName());
        return loginUser.execute(request, ctx);
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request, HttpServletRequest http) {
        SessionContext ctx = ctxExtractor.extract(http, null);
        return refreshToken.execute(request, ctx);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        logout.execute(userId);
    }

    @GetMapping("/sessions")
    public List<SessionResponse> sessions(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        UUID currentSessionId = null;
        String sid = jwt.getClaimAsString("sid");
        if (sid != null && !sid.isBlank()) currentSessionId = UUID.fromString(sid);
        return listSessions.execute(userId, currentSessionId);
    }

    @DeleteMapping("/sessions/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeSession(@AuthenticationPrincipal Jwt jwt, @PathVariable String sessionId) {
        UUID userId = UUID.fromString(jwt.getSubject());
        revokeSession.execute(userId, UUID.fromString(sessionId));
    }

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMe(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        deleteUser.execute(userId);
    }
}

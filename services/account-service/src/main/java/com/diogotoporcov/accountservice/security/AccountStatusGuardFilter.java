package com.diogotoporcov.accountservice.security;

import com.diogotoporcov.accountservice.application.GetMyProfileUseCase;
import com.diogotoporcov.accountservice.profile.entity.AccountStatus;
import com.diogotoporcov.accountservice.profile.entity.UserProfile;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

public class AccountStatusGuardFilter extends OncePerRequestFilter {

    private final GetMyProfileUseCase getMyProfile;
    private final ObjectMapper objectMapper;

    private final RequestMatcher skip = new OrRequestMatcher(
            PathPatternRequestMatcher.withDefaults().matcher("/actuator/**"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/account/me"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.PATCH, "/account/me"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.DELETE, "/account/me")
    );

    public AccountStatusGuardFilter(GetMyProfileUseCase getMyProfile, ObjectMapper objectMapper) {
        this.getMyProfile = getMyProfile;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return skip.matches(request);
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof Jwt jwt)) {
            filterChain.doFilter(request, response);
            return;
        }

        UUID userId;
        try {
            userId = UUID.fromString(jwt.getSubject());
        } catch (Exception e) {
            writeProblem(response, request, HttpStatus.UNAUTHORIZED, "Invalid token subject");
            return;
        }

        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            writeProblem(response, request, HttpStatus.UNAUTHORIZED, "Invalid token: missing email claim");
            return;
        }

        UserProfile profile = getMyProfile.execute(userId, email);

        if (profile.getStatus() == AccountStatus.INACTIVE) {
            writeProblem(response, request, HttpStatus.FORBIDDEN, "Account is inactive");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void writeProblem(HttpServletResponse response, HttpServletRequest request, HttpStatus status, String detail) throws IOException {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(status.getReasonPhrase());
        pd.setProperty("timestamp", Instant.now().toString());
        pd.setProperty("path", request.getRequestURI());

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), pd);
    }
}

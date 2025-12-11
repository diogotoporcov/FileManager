package com.diogotoporcov.accountservice.security;

import com.diogotoporcov.accountservice.application.GetMyProfileUseCase;
import com.diogotoporcov.accountservice.error.AccountInactiveException;
import com.diogotoporcov.accountservice.profile.entity.AccountStatus;
import com.diogotoporcov.accountservice.profile.entity.UserProfile;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.util.List;
import java.util.UUID;

public class AccountStatusGuardFilter extends OncePerRequestFilter {

    private final GetMyProfileUseCase getMyProfile;
    private final List<HandlerMapping> handlerMappings;
    private final HandlerExceptionResolver exceptionResolver;

    public AccountStatusGuardFilter(GetMyProfileUseCase getMyProfile,
                                    List<HandlerMapping> handlerMappings,
                                    HandlerExceptionResolver exceptionResolver) {
        this.getMyProfile = getMyProfile;
        this.handlerMappings = handlerMappings;
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {

        if (request.getRequestURI().startsWith("/actuator/")) {
            return true;
        }

        HandlerExecutionChain handler;
        try {
            handler = getHandler(request);
        } catch (Exception e) {
            return false;
        }

        if (handler == null) {
            return false;
        }

        Object handlerObject = handler.getHandler();

        if (handlerObject instanceof HandlerMethod hm) {
            return hm.hasMethodAnnotation(AllowInactive.class)
                    || hm.getBeanType().isAnnotationPresent(AllowInactive.class);
        }

        return false;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) {

        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();

            if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof Jwt jwt)) {
                filterChain.doFilter(request, response);
                return;
            }

            UUID userId;
            try {
                userId = UUID.fromString(jwt.getSubject());
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid token subject");
            }

            String email = jwt.getClaimAsString("email");
            if (email == null || email.isBlank()) {
                throw new IllegalArgumentException("Invalid token: missing email claim");
            }

            UserProfile profile = getMyProfile.execute(userId, email);

            if (profile.getStatus() == AccountStatus.INACTIVE) {
                throw new AccountInactiveException("Account is inactive");
            }

            filterChain.doFilter(request, response);

        } catch (Exception ex) {
            exceptionResolver.resolveException(request, response, null, ex);
        }
    }

    private HandlerExecutionChain getHandler(HttpServletRequest request) throws Exception {
        for (HandlerMapping mapping : handlerMappings) {
            HandlerExecutionChain handler = mapping.getHandler(request);
            if (handler != null) {
                return handler;
            }
        }
        return null;
    }
}

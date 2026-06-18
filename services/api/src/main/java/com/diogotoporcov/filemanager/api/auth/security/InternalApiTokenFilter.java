package com.diogotoporcov.filemanager.api.auth.security;

import com.diogotoporcov.filemanager.api.config.InternalApiProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NullMarked;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

@NullMarked
public class InternalApiTokenFilter extends OncePerRequestFilter {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private final InternalApiProperties internalApiProperties;

    public InternalApiTokenFilter(InternalApiProperties internalApiProperties) {
        this.internalApiProperties = internalApiProperties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authHeader = request.getHeader(AUTH_HEADER);

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            rejectUnauthorized(response);

            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length());
        String configuredToken = internalApiProperties.getApiToken();

        if (token.isBlank() || configuredToken == null || configuredToken.isBlank()
                || !MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8), configuredToken.getBytes(StandardCharsets.UTF_8))) {
            rejectUnauthorized(response);

            return;
        }

        authenticateInternalService();
        filterChain.doFilter(request, response);
    }

    private void rejectUnauthorized(HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    private void authenticateInternalService() {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                "internal-service",
                null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_INTERNAL_SERVICE"))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}

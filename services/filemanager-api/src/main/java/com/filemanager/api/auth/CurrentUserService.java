package com.filemanager.api.auth;

import com.filemanager.api.entity.User;
import com.filemanager.api.service.IdentityResolutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CurrentUserService {

    private final IdentityResolutionService identityResolutionService;

    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new AuthenticationCredentialsNotFoundException("No authenticated JWT principal found");
        }
        return identityResolutionService.resolveUser(jwt);
    }

    public UUID getCurrentUserId() {
        return getCurrentUser().getId();
    }
}

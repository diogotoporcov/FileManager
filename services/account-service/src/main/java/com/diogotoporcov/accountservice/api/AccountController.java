package com.diogotoporcov.accountservice.api;

import com.diogotoporcov.accountservice.api.dto.MyProfileResponse;
import com.diogotoporcov.accountservice.api.dto.UpdateMyProfileRequest;
import com.diogotoporcov.accountservice.application.GetMyProfileUseCase;
import com.diogotoporcov.accountservice.application.RequestDeletionUseCase;
import com.diogotoporcov.accountservice.application.UpdateMyProfileUseCase;
import com.diogotoporcov.accountservice.profile.entity.UserProfile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/account")
public class AccountController {

    private final GetMyProfileUseCase getMyProfile;
    private final UpdateMyProfileUseCase updateMyProfile;
    private final RequestDeletionUseCase requestDeletion;

    public AccountController(GetMyProfileUseCase getMyProfile, UpdateMyProfileUseCase updateMyProfile, RequestDeletionUseCase requestDeletion) {
        this.getMyProfile = getMyProfile;
        this.updateMyProfile = updateMyProfile;
        this.requestDeletion = requestDeletion;
    }

    @GetMapping("/me")
    public MyProfileResponse me(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        String email = jwt.getClaimAsString("email");

        UserProfile profile = getMyProfile.execute(userId, email);
        return toResponse(profile);
    }

    @PatchMapping("/me")
    public MyProfileResponse update(@AuthenticationPrincipal Jwt jwt, @RequestBody UpdateMyProfileRequest req) {
        UUID userId = UUID.fromString(jwt.getSubject());
        String email = jwt.getClaimAsString("email");

        UserProfile profile = updateMyProfile.execute(userId, email, req);
        return toResponse(profile);
    }

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMe(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        requestDeletion.execute(userId);
    }

    private static MyProfileResponse toResponse(UserProfile profile) {
        return new MyProfileResponse(
                profile.getUserId().toString(),
                profile.getEmail(),
                profile.getFullName(),
                profile.getUsername(),
                profile.getLocale(),
                profile.getTimezone(),
                profile.getStatus().name()
        );
    }
}

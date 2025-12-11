package com.diogotoporcov.accountservice.api;

import com.diogotoporcov.accountservice.api.dto.MyProfileResponse;
import com.diogotoporcov.accountservice.api.dto.UpdateMyProfileRequest;
import com.diogotoporcov.accountservice.application.GetMyProfileUseCase;
import com.diogotoporcov.accountservice.application.UpdateMyProfileUseCase;
import com.diogotoporcov.accountservice.profile.entity.UserProfile;
import com.diogotoporcov.accountservice.security.AllowInactive;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/account")
public class AccountController {

    private final GetMyProfileUseCase getMyProfile;
    private final UpdateMyProfileUseCase updateMyProfile;

    public AccountController(GetMyProfileUseCase getMyProfile, UpdateMyProfileUseCase updateMyProfile) {
        this.getMyProfile = getMyProfile;
        this.updateMyProfile = updateMyProfile;
    }

    @AllowInactive
    @GetMapping("/me")
    public MyProfileResponse me(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        String email = jwt.getClaimAsString("email");

        UserProfile profile = getMyProfile.execute(userId, email);
        return toResponse(profile);
    }

    @AllowInactive
    @PatchMapping("/me")
    public MyProfileResponse update(@AuthenticationPrincipal Jwt jwt, @RequestBody UpdateMyProfileRequest req) {
        UUID userId = UUID.fromString(jwt.getSubject());
        String email = jwt.getClaimAsString("email");

        UserProfile profile = updateMyProfile.execute(userId, email, req);
        return toResponse(profile);
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

package com.diogotoporcov.accountservice.application;

import com.diogotoporcov.accountservice.api.dto.UpdateMyProfileRequest;
import com.diogotoporcov.accountservice.error.InvalidUsernameException;
import com.diogotoporcov.accountservice.error.UsernameAlreadyInUseException;
import com.diogotoporcov.accountservice.profile.LocaleUtil;
import com.diogotoporcov.accountservice.profile.TimezoneUtil;
import com.diogotoporcov.accountservice.profile.entity.UserProfile;
import com.diogotoporcov.accountservice.profile.repository.UserProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class UpdateMyProfileUseCase {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{3,32}$");

    private final UserProfileRepository profiles;
    private final GetMyProfileUseCase getMyProfile;

    public UpdateMyProfileUseCase(UserProfileRepository profiles, GetMyProfileUseCase getMyProfile) {
        this.profiles = profiles;
        this.getMyProfile = getMyProfile;
    }

    @Transactional
    public UserProfile execute(UUID userId, String email, UpdateMyProfileRequest req) {
        UserProfile profile = getMyProfile.execute(userId, email);

        if (req.fullName() != null) {
            String v = req.fullName().trim();
            profile.setFullName(v.isBlank() ? null : v);
        }

        if (req.username() != null) {
            String candidate = req.username().trim();
            if (!USERNAME_PATTERN.matcher(candidate).matches()) {
                throw new InvalidUsernameException("Username must match ^[A-Za-z0-9_-]{3,32}$");
            }
            if (profiles.existsByUsernameAndUserIdNot(candidate, userId)) {
                throw new UsernameAlreadyInUseException(candidate);
            }
            profile.setUsername(candidate);
        }

        if (req.locale() != null) {
            profile.setLocale(LocaleUtil.normalizeOrThrow(req.locale()));
        }

        if (req.timezone() != null) {
            profile.setTimezone(TimezoneUtil.normalizeOrThrow(req.timezone()));
        }

        return profiles.save(profile);
    }
}

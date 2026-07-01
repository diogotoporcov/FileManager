package com.diogotoporcov.filemanager.api.auth.application;

import com.diogotoporcov.filemanager.api.identity.domain.User;
import java.util.UUID;

public interface CurrentUserProvider {
    User getCurrentUser();

    default UUID getCurrentUserId() {
        return getCurrentUser().getId();
    }
}

package com.filemanager.api.adapter;

import java.util.UUID;

final class NativeOwnerScopeSql {
    private NativeOwnerScopeSql() {
    }

    static String singleFilePredicate(UUID ownerUserId) {
        return ownerUserId != null
                ? "f.owner_user_id = :ownerId"
                : "f.owner_organization_id = :ownerId";
    }

    static String pairFilePredicate(UUID ownerUserId) {
        return ownerUserId != null
                ? "source_file.owner_user_id = :ownerId AND candidate_file.owner_user_id = :ownerId"
                : "source_file.owner_organization_id = :ownerId AND candidate_file.owner_organization_id = :ownerId";
    }

    static UUID ownerId(UUID ownerUserId, UUID ownerOrganizationId) {
        return ownerUserId != null ? ownerUserId : ownerOrganizationId;
    }
}

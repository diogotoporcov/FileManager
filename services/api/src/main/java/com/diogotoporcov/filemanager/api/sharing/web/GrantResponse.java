package com.diogotoporcov.filemanager.api.sharing.web;

import com.diogotoporcov.filemanager.api.auth.domain.Permission;
import com.diogotoporcov.filemanager.api.sharing.domain.FolderGrantScope;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class GrantResponse {
    UUID id;
    String resourceType;
    UUID resourceId;
    UUID granteeUserId;
    Permission permission;
    FolderGrantScope scope;
    UUID createdByUserId;
    OffsetDateTime createdAt;
    OffsetDateTime revokedAt;
}

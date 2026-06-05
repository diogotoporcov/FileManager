package com.filemanager.api.sharing.web;

import com.filemanager.api.auth.domain.Permission;
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
    UUID createdByUserId;
    OffsetDateTime createdAt;
    OffsetDateTime revokedAt;
}

package com.filemanager.api.dto;

import com.filemanager.api.entity.TagScopeType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.time.OffsetDateTime;
import java.util.UUID;

@Value
@Builder
@Schema(description = "Reusable classification tag")
public class TagResponse {
    UUID id;
    String displayName;
    String normalizedName;
    TagScopeType scopeType;
    UUID scopeFolderId;
    UUID ownerUserId;
    UUID ownerOrganizationId;
    UUID createdByUserId;
    OffsetDateTime createdAt;
    OffsetDateTime updatedAt;
}

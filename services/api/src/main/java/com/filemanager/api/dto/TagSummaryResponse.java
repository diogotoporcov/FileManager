package com.filemanager.api.dto;

import com.filemanager.api.entity.TagScopeType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.util.UUID;

@Value
@Builder
@Schema(description = "Compact reusable classification tag")
public class TagSummaryResponse {
    UUID id;
    String displayName;
    String normalizedName;
    TagScopeType scopeType;
    UUID scopeFolderId;
    UUID ownerUserId;
    UUID ownerOrganizationId;
}

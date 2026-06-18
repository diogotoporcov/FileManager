package com.diogotoporcov.filemanager.api.tag.web;

import com.diogotoporcov.filemanager.api.tag.domain.TagScopeType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Value;

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
    UUID createdByUserId;
    OffsetDateTime createdAt;
    OffsetDateTime updatedAt;
}
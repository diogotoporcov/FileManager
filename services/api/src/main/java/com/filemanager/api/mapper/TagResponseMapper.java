package com.filemanager.api.mapper;

import com.filemanager.api.dto.TagResponse;
import com.filemanager.api.dto.TagSummaryResponse;
import com.filemanager.api.entity.TagEntity;
import org.springframework.stereotype.Component;

@Component
public class TagResponseMapper {
    public TagResponse toResponse(TagEntity tag) {
        return TagResponse.builder()
                .id(tag.getId())
                .displayName(tag.getDisplayName())
                .normalizedName(tag.getNormalizedName())
                .scopeType(tag.getScopeType())
                .scopeFolderId(tag.getScopeFolder() != null ? tag.getScopeFolder().getId() : null)
                .ownerUserId(tag.getOwnerUser() != null ? tag.getOwnerUser().getId() : null)
                .ownerOrganizationId(tag.getOwnerOrganization() != null ? tag.getOwnerOrganization().getId() : null)
                .createdByUserId(tag.getCreatedByUser().getId())
                .createdAt(tag.getCreatedAt())
                .updatedAt(tag.getUpdatedAt())
                .build();
    }

    public TagSummaryResponse toSummary(TagEntity tag) {
        return TagSummaryResponse.builder()
                .id(tag.getId())
                .displayName(tag.getDisplayName())
                .normalizedName(tag.getNormalizedName())
                .scopeType(tag.getScopeType())
                .scopeFolderId(tag.getScopeFolder() != null ? tag.getScopeFolder().getId() : null)
                .ownerUserId(tag.getOwnerUser() != null ? tag.getOwnerUser().getId() : null)
                .ownerOrganizationId(tag.getOwnerOrganization() != null ? tag.getOwnerOrganization().getId() : null)
                .build();
    }
}

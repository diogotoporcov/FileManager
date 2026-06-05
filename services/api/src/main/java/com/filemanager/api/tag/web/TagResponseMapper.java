package com.filemanager.api.tag.web;

import org.springframework.stereotype.Component;
import com.filemanager.api.tag.domain.TagEntity;

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
                .createdByUserId(tag.getCreatedByUser().getId())
                .createdAt(tag.getCreatedAt())
                .updatedAt(tag.getUpdatedAt())
                .build();
    }
}

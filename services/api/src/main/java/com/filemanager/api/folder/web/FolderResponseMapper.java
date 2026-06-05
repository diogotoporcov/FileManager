package com.filemanager.api.folder.web;

import org.springframework.stereotype.Component;
import com.filemanager.api.folder.domain.FolderEntity;

@Component
public class FolderResponseMapper {
    public FolderResponse toResponse(FolderEntity folder) {
        return FolderResponse.builder()
                .id(folder.getId())
                .name(folder.getName())
                .parentFolderId(folder.getParentFolder() != null ? folder.getParentFolder().getId() : null)
                .ownerUserId(folder.getOwnerUser() != null ? folder.getOwnerUser().getId() : null)
                .createdByUserId(folder.getCreatedByUser().getId())
                .createdAt(folder.getCreatedAt())
                .updatedAt(folder.getUpdatedAt())
                .build();
    }

    public FolderSummaryResponse toSummary(FolderEntity folder) {
        return FolderSummaryResponse.builder()
                .id(folder.getId())
                .name(folder.getName())
                .parentFolderId(folder.getParentFolder() != null ? folder.getParentFolder().getId() : null)
                .ownerUserId(folder.getOwnerUser() != null ? folder.getOwnerUser().getId() : null)
                .createdByUserId(folder.getCreatedByUser().getId())
                .createdAt(folder.getCreatedAt())
                .updatedAt(folder.getUpdatedAt())
                .build();
    }
}

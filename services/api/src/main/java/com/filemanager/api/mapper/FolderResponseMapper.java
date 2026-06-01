package com.filemanager.api.mapper;

import com.filemanager.api.dto.FolderResponse;
import com.filemanager.api.dto.FolderSummaryResponse;
import com.filemanager.api.entity.FolderEntity;
import org.springframework.stereotype.Component;

@Component
public class FolderResponseMapper {
    public FolderResponse toResponse(FolderEntity folder) {
        return FolderResponse.builder()
                .id(folder.getId())
                .name(folder.getName())
                .parentFolderId(folder.getParentFolder() != null ? folder.getParentFolder().getId() : null)
                .ownerUserId(folder.getOwnerUser() != null ? folder.getOwnerUser().getId() : null)
                .ownerOrganizationId(folder.getOwnerOrganization() != null ? folder.getOwnerOrganization().getId() : null)
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
                .ownerOrganizationId(folder.getOwnerOrganization() != null ? folder.getOwnerOrganization().getId() : null)
                .createdByUserId(folder.getCreatedByUser().getId())
                .createdAt(folder.getCreatedAt())
                .updatedAt(folder.getUpdatedAt())
                .build();
    }
}

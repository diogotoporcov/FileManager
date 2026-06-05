package com.filemanager.api.sharing.web;

import com.filemanager.api.sharing.domain.FileGrantEntity;
import com.filemanager.api.sharing.domain.FolderGrantEntity;
import org.springframework.stereotype.Component;

@Component
public class GrantResponseMapper {
    public GrantResponse toResponse(FileGrantEntity grant) {
        return GrantResponse.builder()
                .id(grant.getId())
                .resourceType("FILE")
                .resourceId(grant.getFile().getId())
                .granteeUserId(grant.getGranteeUser().getId())
                .permission(grant.getPermission())
                .createdByUserId(grant.getCreatedByUser().getId())
                .createdAt(grant.getCreatedAt())
                .revokedAt(grant.getRevokedAt())
                .build();
    }

    public GrantResponse toResponse(FolderGrantEntity grant) {
        return GrantResponse.builder()
                .id(grant.getId())
                .resourceType("FOLDER")
                .resourceId(grant.getFolder().getId())
                .granteeUserId(grant.getGranteeUser().getId())
                .permission(grant.getPermission())
                .createdByUserId(grant.getCreatedByUser().getId())
                .createdAt(grant.getCreatedAt())
                .revokedAt(grant.getRevokedAt())
                .build();
    }
}

package com.filemanager.api.mapper;

import com.filemanager.api.dto.FileResponse;
import com.filemanager.api.entity.FileEntity;
import org.springframework.stereotype.Component;

@Component
public class FileResponseMapper {
    public FileResponse toResponse(FileEntity file) {
        return FileResponse.builder()
                .id(file.getId())
                .name(file.getName())
                .mimeType(file.getMimeType())
                .size(file.getSize())
                .ownerUserId(file.getOwnerUser() != null ? file.getOwnerUser().getId() : null)
                .ownerOrganizationId(file.getOwnerOrganization() != null ? file.getOwnerOrganization().getId() : null)
                .createdAt(file.getCreatedAt())
                .updatedAt(file.getUpdatedAt())
                .build();
    }
}

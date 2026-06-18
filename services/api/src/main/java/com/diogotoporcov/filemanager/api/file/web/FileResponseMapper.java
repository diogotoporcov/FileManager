package com.diogotoporcov.filemanager.api.file.web;

import org.springframework.stereotype.Component;
import com.diogotoporcov.filemanager.api.file.domain.FileEntity;

@Component
public class FileResponseMapper {
    public FileResponse toResponse(FileEntity file) {
        return FileResponse.builder()
                .id(file.getId())
                .name(file.getName())
                .mimeType(file.getMimeType())
                .size(file.getSize())
                .ownerUserId(file.getOwnerUser() != null ? file.getOwnerUser().getId() : null)
                .folderId(file.getFolder() != null ? file.getFolder().getId() : null)
                .createdByUserId(file.getCreatedByUser() != null ? file.getCreatedByUser().getId() : null)
                .createdAt(file.getCreatedAt())
                .updatedAt(file.getUpdatedAt())
                .build();
    }
}

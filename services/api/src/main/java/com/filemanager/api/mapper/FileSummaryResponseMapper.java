package com.filemanager.api.mapper;

import com.filemanager.api.dto.FileSummaryResponse;
import com.filemanager.api.entity.FileEntity;
import org.springframework.stereotype.Component;

@Component
public class FileSummaryResponseMapper {
    public FileSummaryResponse toSummary(FileEntity file) {
        return FileSummaryResponse.builder()
                .id(file.getId())
                .name(file.getName())
                .mimeType(file.getMimeType())
                .size(file.getSize())
                .createdAt(file.getCreatedAt())
                .updatedAt(file.getUpdatedAt())
                .build();
    }
}

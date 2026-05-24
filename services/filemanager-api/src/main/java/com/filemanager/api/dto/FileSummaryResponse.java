package com.filemanager.api.dto;

import lombok.Builder;
import lombok.Value;

import java.time.OffsetDateTime;
import java.util.UUID;

@Value
@Builder
public class FileSummaryResponse {
    UUID id;
    String name;
    String mimeType;
    Long size;
    OffsetDateTime createdAt;
    OffsetDateTime updatedAt;
}

package com.filemanager.api.dto;

import lombok.Builder;
import lombok.Value;

import java.time.OffsetDateTime;
import java.util.UUID;

@Value
@Builder
public class FileResponse {
    UUID id;
    String name;
    String mimeType;
    Long size;
    UUID ownerUserId;
    UUID ownerOrganizationId;
    OffsetDateTime createdAt;
    OffsetDateTime updatedAt;
}

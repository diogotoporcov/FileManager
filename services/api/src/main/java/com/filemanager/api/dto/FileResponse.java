package com.filemanager.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.time.OffsetDateTime;
import java.util.UUID;

@Value
@Builder
@Schema(description = "Detailed information about a file")
public class FileResponse {
    @Schema(description = "Unique identifier of the file")
    UUID id;
    @Schema(description = "Name of the file")
    String name;
    @Schema(description = "MIME type of the file")
    String mimeType;
    @Schema(description = "Size of the file in bytes")
    Long size;
    @Schema(description = "User ID of the owner")
    UUID ownerUserId;
    @Schema(description = "Organization ID of the owner")
    UUID ownerOrganizationId;
    @Schema(description = "Timestamp when the file was created")
    OffsetDateTime createdAt;
    @Schema(description = "Timestamp when the file was last updated")
    OffsetDateTime updatedAt;
}

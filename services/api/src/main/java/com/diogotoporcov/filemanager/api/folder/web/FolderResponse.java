package com.diogotoporcov.filemanager.api.folder.web;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@Schema(description = "Detailed information about a folder")
public class FolderResponse {
    @Schema(description = "Unique identifier of the folder")
    UUID id;
    @Schema(description = "Folder name")
    String name;
    @Schema(description = "Parent folder ID, or null for a root folder")
    UUID parentFolderId;
    @Schema(description = "User ID of the owner")
    UUID ownerUserId;
    @Schema(description = "User ID that created the folder")
    UUID createdByUserId;
    @Schema(description = "Timestamp when the folder was created")
    OffsetDateTime createdAt;
    @Schema(description = "Timestamp when the folder was last updated")
    OffsetDateTime updatedAt;
}
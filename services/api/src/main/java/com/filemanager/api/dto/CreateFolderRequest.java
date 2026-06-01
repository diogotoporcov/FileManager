package com.filemanager.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Schema(description = "Request to create a folder")
public class CreateFolderRequest {
    @NotBlank
    @Schema(description = "Folder name. Path separators and control characters are not allowed.")
    private String name;

    @Schema(description = "Parent folder ID. Omit for a root folder.")
    private UUID parentFolderId;

    @Schema(description = "Owner user ID. Exactly one owner scope is required for root folders.")
    private UUID ownerUserId;

    @Schema(description = "Owner organization ID. Exactly one owner scope is required for root folders.")
    private UUID ownerOrganizationId;
}

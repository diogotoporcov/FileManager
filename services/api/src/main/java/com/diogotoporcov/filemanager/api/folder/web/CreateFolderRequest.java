package com.diogotoporcov.filemanager.api.folder.web;

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

    @Schema(description = "Parent folder ID. Omit for a root folder. Root folders are owned by the authenticated user.")
    private UUID parentFolderId;
}
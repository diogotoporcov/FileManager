package com.filemanager.api.dto;

import com.filemanager.api.entity.TagScopeType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Schema(description = "Request to create or get a reusable tag classification")
public class CreateTagRequest {
    @NotBlank
    @Size(max = 100)
    @Schema(description = "Display name for the tag. Names are normalized for uniqueness inside the selected scope.")
    private String name;

    @NotNull
    @Schema(description = "Tag scope. OWNER tags are owned by the authenticated user; FOLDER tags are scoped to one folder.")
    private TagScopeType scopeType;

    @Schema(description = "Folder scope ID for FOLDER-scoped tags.")
    private UUID scopeFolderId;
}
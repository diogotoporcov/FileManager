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
    @Schema(description = "Tag scope. OWNER tags are available across an owner context; FOLDER tags are available in one folder context.")
    private TagScopeType scopeType;

    @Schema(description = "Owner user ID for OWNER-scoped tags. Exactly one owner is required for OWNER scope.")
    private UUID ownerUserId;

    @Schema(description = "Owner organization ID for OWNER-scoped tags. Exactly one owner is required for OWNER scope.")
    private UUID ownerOrganizationId;

    @Schema(description = "Folder scope ID for FOLDER-scoped tags.")
    private UUID scopeFolderId;
}

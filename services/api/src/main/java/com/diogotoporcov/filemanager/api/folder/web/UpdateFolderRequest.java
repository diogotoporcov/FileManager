package com.diogotoporcov.filemanager.api.folder.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Request to rename a folder")
public class UpdateFolderRequest {
    @NotBlank
    @Schema(description = "New folder name. Path separators and control characters are not allowed.")
    private String name;
}

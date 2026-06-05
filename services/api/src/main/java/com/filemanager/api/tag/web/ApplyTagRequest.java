package com.filemanager.api.tag.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Schema(description = "Request to apply an existing tag to a file or folder")
public class ApplyTagRequest {
    @NotNull
    @Schema(description = "Tag ID to apply")
    private UUID tagId;
}

package com.filemanager.api.search.file;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Schema(description = "Allowlisted file list/search query parameters")
public class FileSearchQuery {
    @Schema(description = "Owner user ID. Must match the authenticated user.")
    private UUID ownerUserId;

    @Schema(description = "Owner organization ID. Authenticated user must have file view permission.")
    private UUID ownerOrganizationId;

    @Schema(description = "Folder ID. When set, lists files directly inside that folder only.")
    private UUID folderId;

    @Schema(description = "Tag ID. When set, filters files through file tag assignments.")
    private UUID tagId;

    @Schema(description = "Inclusive created-at lower bound, ISO-8601 offset date-time.")
    private String createdAtFrom;

    @Schema(description = "Exclusive created-at upper bound, ISO-8601 offset date-time.")
    private String createdAtTo;

    @Schema(description = "Inclusive updated-at lower bound, ISO-8601 offset date-time.")
    private String updatedAtFrom;

    @Schema(description = "Exclusive updated-at upper bound, ISO-8601 offset date-time.")
    private String updatedAtTo;

    @Schema(description = "Inclusive minimum file size in bytes.")
    private Long sizeMin;

    @Schema(description = "Inclusive maximum file size in bytes.")
    private Long sizeMax;

    @Schema(description = "Exact MIME type filter. Repeat the parameter to match any listed MIME type.")
    private List<String> mimeType;

    @Schema(description = "Sort syntax: field,direction. Allowed fields: createdAt, updatedAt, name, size. Directions: asc, desc. Default: createdAt,desc.")
    private String sort;

    @Schema(description = "Maximum items to return. Default 50, maximum 200.")
    private Integer size;

    @Schema(description = "Alias for size. Default 50, maximum 200.")
    private Integer limit;

    @Schema(description = "Cursor returned by the previous page.")
    private String cursor;
}

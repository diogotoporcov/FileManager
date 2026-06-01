package com.filemanager.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Duplicate detection method to run during on-demand duplicate search")
public enum DuplicateSearchMethod {
    SHA256,
    PHASH,
    EMBEDDING
}

package com.filemanager.api.file.application.search;

import org.springframework.data.domain.Sort;

public record SortSpec(String field, Sort.Direction direction) {
    public SortSpec {
        if (field == null || field.isBlank()) {
            throw new SearchValidationException("Sort field is required");
        }

        if (direction == null) {
            throw new SearchValidationException("Sort direction is required");
        }
    }
}

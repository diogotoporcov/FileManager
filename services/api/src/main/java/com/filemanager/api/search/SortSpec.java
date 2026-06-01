package com.filemanager.api.search;

public record SortSpec(String field, SortDirection direction) {
    public SortSpec {
        if (field == null || field.isBlank()) {
            throw new SearchValidationException("Sort field is required");
        }
        if (direction == null) {
            throw new SearchValidationException("Sort direction is required");
        }
    }
}

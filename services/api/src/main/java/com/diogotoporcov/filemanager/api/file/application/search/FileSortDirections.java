package com.diogotoporcov.filemanager.api.file.application.search;

import org.springframework.data.domain.Sort;

final class FileSortDirections {
    private FileSortDirections() {
    }

    static Sort.Direction parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new SearchValidationException("Sort direction is required");
        }

        try {
            return Sort.Direction.fromString(raw);
        } catch (IllegalArgumentException ex) {
            throw new SearchValidationException("Unsupported sort direction: " + raw, ex);
        }
    }
}

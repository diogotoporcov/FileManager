package com.filemanager.api.search.file;

import com.filemanager.api.search.SearchValidationException;
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

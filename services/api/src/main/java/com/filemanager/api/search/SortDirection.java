package com.filemanager.api.search;

import java.util.Locale;

public enum SortDirection {
    ASC,
    DESC;

    public static SortDirection parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new SearchValidationException("Sort direction is required");
        }

        try {
            return SortDirection.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new SearchValidationException("Unsupported sort direction: " + raw, ex);
        }
    }
}

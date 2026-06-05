package com.filemanager.api.file.application.search;


import java.time.OffsetDateTime;

public record DateTimeRange(OffsetDateTime from, OffsetDateTime to) {
    public DateTimeRange {
        if (from != null && to != null && from.isAfter(to)) {
            throw new SearchValidationException("Date range start must not be after date range end");
        }
    }

    public boolean hasBounds() {
        return from != null || to != null;
    }
}

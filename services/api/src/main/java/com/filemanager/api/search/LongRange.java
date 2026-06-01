package com.filemanager.api.search;

public record LongRange(Long min, Long max) {
    public LongRange {
        if (min != null && min < 0) {
            throw new SearchValidationException("Minimum size must not be negative");
        }
        if (max != null && max < 0) {
            throw new SearchValidationException("Maximum size must not be negative");
        }
        if (min != null && max != null && min > max) {
            throw new SearchValidationException("Minimum size must not be greater than maximum size");
        }
    }

    public boolean hasBounds() {
        return min != null || max != null;
    }
}

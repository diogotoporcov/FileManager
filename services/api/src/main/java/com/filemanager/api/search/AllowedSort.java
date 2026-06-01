package com.filemanager.api.search;

public record AllowedSort(String publicName, String entityProperty) {
    public AllowedSort {
        if (publicName == null || publicName.isBlank()) {
            throw new SearchValidationException("Sort field is required");
        }
        if (entityProperty == null || entityProperty.isBlank()) {
            throw new SearchValidationException("Sort mapping is required");
        }
    }
}

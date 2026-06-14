package com.filemanager.api.duplicate.web;

public record DuplicateSearchPageRequest(
        Integer pageSize,
        String cursor
) {
    public static DuplicateSearchPageRequest defaults() {
        return new DuplicateSearchPageRequest(null, null);
    }
}

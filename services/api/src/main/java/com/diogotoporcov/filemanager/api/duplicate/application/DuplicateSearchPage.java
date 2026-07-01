package com.diogotoporcov.filemanager.api.duplicate.application;

public record DuplicateSearchPage(Integer pageSize, String cursor) {
    public static DuplicateSearchPage defaults() {
        return new DuplicateSearchPage(null, null);
    }
}

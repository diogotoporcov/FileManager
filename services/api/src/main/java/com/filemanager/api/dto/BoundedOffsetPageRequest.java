package com.filemanager.api.dto;

public record BoundedOffsetPageRequest(int page, int size) {
    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 50;
    public static final int MAX_SIZE = 200;
    public static final int MAX_PAGE = 10_000;

    public static BoundedOffsetPageRequest of(Integer requestedPage, Integer requestedSize) {
        int effectivePage = requestedPage == null ? DEFAULT_PAGE : requestedPage;
        int effectiveSize = requestedSize == null ? DEFAULT_SIZE : requestedSize;
        if (effectivePage < 0) {
            throw new IllegalArgumentException("Page index must not be negative");
        }
        if (effectivePage > MAX_PAGE) {
            throw new IllegalArgumentException("Page index is too deep");
        }
        if (effectiveSize < 1) {
            throw new IllegalArgumentException("Page size must be positive");
        }
        if (effectiveSize > MAX_SIZE) {
            throw new IllegalArgumentException("Page size must not exceed " + MAX_SIZE);
        }
        return new BoundedOffsetPageRequest(effectivePage, effectiveSize);
    }
}

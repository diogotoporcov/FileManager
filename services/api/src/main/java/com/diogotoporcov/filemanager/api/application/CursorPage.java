package com.diogotoporcov.filemanager.api.application;

import java.util.List;

public record CursorPage<T>(
        List<T> items,
        String nextCursor,
        boolean hasMore,
        int pageSize) {
}

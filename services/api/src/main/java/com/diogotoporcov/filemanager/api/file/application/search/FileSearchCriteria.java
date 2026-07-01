package com.diogotoporcov.filemanager.api.file.application.search;

import java.util.List;
import java.util.UUID;

public record FileSearchCriteria(
        UUID actorUserId,
        UUID folderId,
        UUID tagId,
        DateTimeRange createdAt,
        DateTimeRange updatedAt,
        LongRange size,
        List<String> mimeTypes,
        SortSpec sort,
        int pageSize,
        FileSearchCursor cursor
) {
    public FileSearchCriteria {
        mimeTypes = List.copyOf(mimeTypes);
    }
}

package com.filemanager.api.file.application.search;

import com.filemanager.api.web.BoundedPageRequest;

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
        BoundedPageRequest pageRequest,
        FileSearchCursor cursor
) {
    public FileSearchCriteria {
        mimeTypes = List.copyOf(mimeTypes);
    }
}

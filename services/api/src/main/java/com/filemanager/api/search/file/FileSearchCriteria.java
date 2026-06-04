package com.filemanager.api.search.file;

import com.filemanager.api.dto.BoundedPageRequest;

import java.util.List;
import java.util.UUID;

public record FileSearchCriteria(
        UUID ownerUserId,
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
package com.filemanager.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
@Schema(description = "Cursor-paginated response")
public class CursorPageResponse<T> {
    @Schema(description = "Items in this page")
    List<T> items;
    @Schema(description = "Cursor for the next page, if more items are available")
    String nextCursor;
    @Schema(description = "Whether another page is available")
    boolean hasMore;
    @Schema(description = "Effective server-bounded page size")
    int pageSize;
}

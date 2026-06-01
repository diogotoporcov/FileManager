package com.filemanager.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
@Schema(description = "Offset-paginated response")
public class PageResponse<T> {
    @Schema(description = "Items in this page")
    List<T> items;
    @Schema(description = "Current page index")
    int page;
    @Schema(description = "Effective server-bounded page size")
    int pageSize;
    @Schema(description = "Whether another page is available")
    boolean hasMore;
    @Schema(description = "Total number of matching items")
    long totalItems;
    @Schema(description = "Total number of pages")
    int totalPages;
}

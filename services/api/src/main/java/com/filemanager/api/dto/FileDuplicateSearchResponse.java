package com.filemanager.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
@Schema(description = "On-demand duplicate search result for one file")
public class FileDuplicateSearchResponse {
    @Schema(description = "Requested file used as the duplicate search context")
    FileSummaryResponse originalFile;
    @Schema(description = "Duplicate files matched for the requested file")
    List<DuplicateMatchResponse> matches;
    @Schema(description = "Cursor for the next page, if more matches are available")
    String nextCursor;
    @Schema(description = "Whether another page is available")
    boolean hasMore;
    @Schema(description = "Effective server-bounded page size")
    int pageSize;
}

package com.filemanager.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
@Schema(description = "One duplicate file with all method evidence that matched it")
public class DuplicateMatchResponse {
    @Schema(description = "Duplicate file")
    FileSummaryResponse file;
    @Schema(description = "Strongest method that matched this file")
    DuplicateSearchMethod bestMethod;
    @Schema(description = "All detection method evidence for this duplicate file")
    List<DuplicateMethodMatchResponse> matches;
}

package com.filemanager.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@Schema(description = "Evidence that a duplicate file matched through one detection method")
public class DuplicateMethodMatchResponse {
    @Schema(description = "Detection method that produced this match")
    DuplicateSearchMethod method;
    @Schema(description = "Distance reported by the method, where lower is closer")
    Double distance;
    @Schema(description = "Confidence score normalized to 0.0 through 1.0")
    Double confidenceScore;
}

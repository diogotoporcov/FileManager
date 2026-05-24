package com.filemanager.api.dto;

import com.filemanager.api.entity.DuplicateCandidate.CandidateStatus;
import com.filemanager.api.entity.DuplicateCandidate.DetectionMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.time.OffsetDateTime;
import java.util.UUID;

@Value
@Builder
@Schema(description = "Information about a potential duplicate file")
public class DuplicateCandidateResponse {
    @Schema(description = "Unique identifier of the duplicate candidate")
    UUID id;
    @Schema(description = "The original file")
    FileSummaryResponse sourceFile;
    @Schema(description = "The potential duplicate file")
    FileSummaryResponse candidateFile;
    @Schema(description = "Method used to detect the duplicate")
    DetectionMethod detectionMethod;
    @Schema(description = "Distance metric between files (lower is more similar)")
    Double distance;
    @Schema(description = "Confidence score of the match (0.0 to 1.0)")
    Double confidenceScore;
    @Schema(description = "Current status of the candidate")
    CandidateStatus status;
    @Schema(description = "Timestamp when the candidate was detected")
    OffsetDateTime createdAt;
}

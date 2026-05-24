package com.filemanager.api.dto;

import com.filemanager.api.entity.DuplicateCandidate.CandidateStatus;
import com.filemanager.api.entity.DuplicateCandidate.DetectionMethod;
import lombok.Builder;
import lombok.Value;

import java.time.OffsetDateTime;
import java.util.UUID;

@Value
@Builder
public class DuplicateCandidateResponse {
    UUID id;
    FileSummaryResponse sourceFile;
    FileSummaryResponse candidateFile;
    DetectionMethod detectionMethod;
    Double distance;
    Double confidenceScore;
    CandidateStatus status;
    OffsetDateTime createdAt;
}

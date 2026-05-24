package com.filemanager.api.dto;

import com.filemanager.api.entity.DuplicateCandidate.CandidateStatus;
import com.filemanager.api.entity.DuplicateCandidate.DetectionMethod;
import lombok.Builder;
import lombok.Value;

import java.time.OffsetDateTime;
import java.util.UUID;

@Value
@Builder
public class FileDuplicateResponse {
    UUID id;
    FileSummaryResponse requestedFile;
    FileSummaryResponse duplicateFile;
    DetectionMethod detectionMethod;
    Double distance;
    Double confidenceScore;
    CandidateStatus status;
    OffsetDateTime createdAt;
}

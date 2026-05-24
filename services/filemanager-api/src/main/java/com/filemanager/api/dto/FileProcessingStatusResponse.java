package com.filemanager.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileProcessingStatusResponse {
    private UUID fileId;
    private AggregateStatus overallStatus;
    private List<ProcessingJobResponse> jobs;
    private long totalDuplicateCandidates;
    private Map<String, Long> duplicateCandidatesByDetectionMethod;
    private Map<String, Long> duplicateCandidatesByStatus;

    public enum AggregateStatus {
        NOT_STARTED,
        PROCESSING,
        COMPLETED,
        FAILED,
        PARTIAL_FAILURE
    }
}

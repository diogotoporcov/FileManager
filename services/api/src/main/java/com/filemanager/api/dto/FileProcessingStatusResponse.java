package com.filemanager.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Aggregated processing status and results for a file")
public class FileProcessingStatusResponse {
    @Schema(description = "ID of the file")
    private UUID fileId;
    @Schema(description = "Overall processing status derived from all jobs")
    private AggregateStatus overallStatus;
    @Schema(description = "List of all processing jobs for this file")
    private List<ProcessingJobResponse> jobs;
    @Schema(description = "Total number of duplicate candidates found")
    private long totalDuplicateCandidates;
    @Schema(description = "Count of duplicate candidates grouped by detection method")
    private Map<String, Long> duplicateCandidatesByDetectionMethod;
    @Schema(description = "Count of duplicate candidates grouped by status")
    private Map<String, Long> duplicateCandidatesByStatus;

    public enum AggregateStatus {
        NOT_STARTED,
        PROCESSING,
        COMPLETED,
        FAILED,
        PARTIAL_FAILURE
    }
}

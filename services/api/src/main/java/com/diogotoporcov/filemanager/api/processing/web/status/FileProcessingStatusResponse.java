package com.diogotoporcov.filemanager.api.processing.web.status;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
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

    public enum AggregateStatus {
        NOT_STARTED,
        PROCESSING,
        COMPLETED,
        FAILED,
        PARTIAL_FAILURE
    }
}

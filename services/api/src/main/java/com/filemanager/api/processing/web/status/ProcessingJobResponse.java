package com.filemanager.api.processing.web.status;

import com.filemanager.api.processing.domain.ProcessingJob;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Information about a file processing job")
public class ProcessingJobResponse {
    @Schema(description = "Unique identifier of the processing job")
    private UUID id;
    @Schema(description = "ID of the file being processed")
    private UUID fileId;
    @Schema(description = "Type of processing being performed")
    private ProcessingJob.JobType jobType;
    @Schema(description = "Current status of the job")
    private ProcessingJob.JobStatus status;
    @Schema(description = "Error message if the job failed")
    private String errorMessage;
    @Schema(description = "Timestamp when the job was created")
    private OffsetDateTime createdAt;
    @Schema(description = "Timestamp when the job was last updated")
    private OffsetDateTime updatedAt;
}

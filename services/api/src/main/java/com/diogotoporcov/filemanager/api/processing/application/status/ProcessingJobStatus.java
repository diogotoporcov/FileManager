package com.diogotoporcov.filemanager.api.processing.application.status;

import com.diogotoporcov.filemanager.api.processing.domain.ProcessingJob;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Builder;

@Builder
public record ProcessingJobStatus(
        UUID id,
        UUID fileId,
        ProcessingJob.JobType jobType,
        ProcessingJob.JobStatus status,
        String errorMessage,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}

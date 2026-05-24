package com.filemanager.api.dto;

import com.filemanager.api.entity.ProcessingJob;
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
public class ProcessingJobResponse {
    private UUID id;
    private UUID fileId;
    private ProcessingJob.JobType jobType;
    private ProcessingJob.JobStatus status;
    private String errorMessage;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}

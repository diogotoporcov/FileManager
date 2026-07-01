package com.diogotoporcov.filemanager.api.processing.application.status;

import java.util.List;
import java.util.UUID;
import lombok.Builder;

@Builder
public record FileProcessingStatus(
        UUID fileId,
        AggregateStatus overallStatus,
        List<ProcessingJobStatus> jobs) {

    public enum AggregateStatus {
        NOT_STARTED,
        PROCESSING,
        COMPLETED,
        FAILED,
        PARTIAL_FAILURE
    }
}

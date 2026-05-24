package com.filemanager.api.service;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class FileManagerMetrics {
    private final MeterRegistry registry;

    public FileManagerMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordFileUpload(long bytes, String ownerType) {
        registry.counter("filemanager.files.uploaded", "owner_type", ownerType).increment();
        registry.summary("filemanager.files.upload.bytes", "owner_type", ownerType).record(bytes);
    }

    public void recordFileDownload() {
        registry.counter("filemanager.files.downloaded").increment();
    }

    public void recordJobCreated(String jobType) {
        registry.counter("filemanager.processing.jobs.created", "job_type", jobType).increment();
    }

    public void recordJobCompleted(String jobType) {
        registry.counter("filemanager.processing.jobs.completed", "job_type", jobType).increment();
    }

    public void recordJobFailed(String jobType) {
        registry.counter("filemanager.processing.jobs.failed", "job_type", jobType).increment();
    }

    public void recordDuplicateCandidateCreated(String detectionMethod) {
        registry.counter("filemanager.duplicates.candidates.created", "detection_method", detectionMethod).increment();
    }
}

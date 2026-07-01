package com.diogotoporcov.filemanager.api.observability.application;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
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

    public void recordDuplicateSearchRequested() {
        registry.counter("filemanager.duplicate.search.requested").increment();
    }

    public void recordDuplicateSearchMethodCompleted(String method) {
        registry.counter("filemanager.duplicate.search.method.completed", "method", method).increment();
    }

    public void recordDuplicateSearchMethodNotReady(String method) {
        registry.counter("filemanager.duplicate.search.method.not_ready", "method", method).increment();
    }

    public void recordDuplicateSearchMethodDisabled(String method) {
        registry.counter("filemanager.duplicate.search.method.disabled", "method", method).increment();
    }

    public void recordDuplicateMatchesReturned(String method, int count) {
        registry.summary("filemanager.duplicate.search.matches_returned", "method", method).record(count);
    }

    public void recordDuplicateGroupsRequested() {
        registry.counter("filemanager.duplicate.groups.requested").increment();
    }

    public void recordDuplicateGroupsReturned(String method, int count) {
        registry.summary("filemanager.duplicate.groups.returned", "method", method).record(count);
    }

    public void recordOperationDuration(String operation, String status, Duration duration) {
        registry.timer("filemanager.operation.duration", "operation", operation, "status", status).record(duration);
    }
}

package com.filemanager.api.observability.adapter;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import com.filemanager.api.observability.port.ApplicationMetricsPort;

@Component
public class MicrometerApplicationMetricsAdapter implements ApplicationMetricsPort {
    private final MeterRegistry registry;

    public MicrometerApplicationMetricsAdapter(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void recordFileUpload(long bytes, String ownerType) {
        registry.counter("filemanager.files.uploaded", "owner_type", ownerType).increment();
        registry.summary("filemanager.files.upload.bytes", "owner_type", ownerType).record(bytes);
    }

    @Override
    public void recordFileDownload() {
        registry.counter("filemanager.files.downloaded").increment();
    }

    @Override
    public void recordJobCreated(String jobType) {
        registry.counter("filemanager.processing.jobs.created", "job_type", jobType).increment();
    }

    @Override
    public void recordJobCompleted(String jobType) {
        registry.counter("filemanager.processing.jobs.completed", "job_type", jobType).increment();
    }

    @Override
    public void recordJobFailed(String jobType) {
        registry.counter("filemanager.processing.jobs.failed", "job_type", jobType).increment();
    }

    @Override
    public void recordDuplicateSearchRequested() {
        registry.counter("filemanager.duplicate.search.requested").increment();
    }

    @Override
    public void recordDuplicateSearchMethodCompleted(String method) {
        registry.counter("filemanager.duplicate.search.method.completed", "method", method).increment();
    }

    @Override
    public void recordDuplicateSearchMethodNotReady(String method) {
        registry.counter("filemanager.duplicate.search.method.not_ready", "method", method).increment();
    }

    @Override
    public void recordDuplicateSearchMethodDisabled(String method) {
        registry.counter("filemanager.duplicate.search.method.disabled", "method", method).increment();
    }

    @Override
    public void recordDuplicateMatchesReturned(String method, int count) {
        registry.summary("filemanager.duplicate.search.matches_returned", "method", method).record(count);
    }

    @Override
    public void recordDuplicateGroupsRequested() {
        registry.counter("filemanager.duplicate.groups.requested").increment();
    }

    @Override
    public void recordDuplicateGroupsReturned(String method, int count) {
        registry.summary("filemanager.duplicate.groups.returned", "method", method).record(count);
    }
}

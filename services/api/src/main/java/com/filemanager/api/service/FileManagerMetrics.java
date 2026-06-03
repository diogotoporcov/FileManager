package com.filemanager.api.service;

import com.filemanager.api.port.ApplicationMetricsPort;
import io.micrometer.core.instrument.MeterRegistry;

public class FileManagerMetrics implements ApplicationMetricsPort {
    private final MeterRegistry registry;

    public FileManagerMetrics(MeterRegistry registry) {
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
}

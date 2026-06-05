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
}

package com.filemanager.api.observability.application;

import com.filemanager.api.observability.port.ApplicationMetricsPort;
import org.springframework.stereotype.Component;

@Component
public class FileManagerMetrics {
    private final ApplicationMetricsPort applicationMetricsPort;

    public FileManagerMetrics(ApplicationMetricsPort applicationMetricsPort) {
        this.applicationMetricsPort = applicationMetricsPort;
    }

    public void recordFileUpload(long bytes, String ownerType) {
        applicationMetricsPort.recordFileUpload(bytes, ownerType);
    }

    public void recordFileDownload() {
        applicationMetricsPort.recordFileDownload();
    }

    public void recordJobCreated(String jobType) {
        applicationMetricsPort.recordJobCreated(jobType);
    }

    public void recordJobCompleted(String jobType) {
        applicationMetricsPort.recordJobCompleted(jobType);
    }

    public void recordJobFailed(String jobType) {
        applicationMetricsPort.recordJobFailed(jobType);
    }
}

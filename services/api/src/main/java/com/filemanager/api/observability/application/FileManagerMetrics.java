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

    public void recordDuplicateSearchRequested() {
        applicationMetricsPort.recordDuplicateSearchRequested();
    }

    public void recordDuplicateSearchMethodCompleted(String method) {
        applicationMetricsPort.recordDuplicateSearchMethodCompleted(method);
    }

    public void recordDuplicateSearchMethodNotReady(String method) {
        applicationMetricsPort.recordDuplicateSearchMethodNotReady(method);
    }

    public void recordDuplicateSearchMethodDisabled(String method) {
        applicationMetricsPort.recordDuplicateSearchMethodDisabled(method);
    }

    public void recordDuplicateMatchesReturned(String method, int count) {
        applicationMetricsPort.recordDuplicateMatchesReturned(method, count);
    }

    public void recordDuplicateGroupsRequested() {
        applicationMetricsPort.recordDuplicateGroupsRequested();
    }

    public void recordDuplicateGroupsReturned(String method, int count) {
        applicationMetricsPort.recordDuplicateGroupsReturned(method, count);
    }
}

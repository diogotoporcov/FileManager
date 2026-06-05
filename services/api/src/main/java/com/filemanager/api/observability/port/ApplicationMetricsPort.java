package com.filemanager.api.observability.port;

public interface ApplicationMetricsPort {
    void recordFileUpload(long bytes, String ownerType);

    void recordFileDownload();

    void recordJobCreated(String jobType);

    void recordJobCompleted(String jobType);

    void recordJobFailed(String jobType);
}

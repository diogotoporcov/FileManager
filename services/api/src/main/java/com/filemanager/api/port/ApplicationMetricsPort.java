package com.filemanager.api.port;

public interface ApplicationMetricsPort {
    void recordFileUpload(long bytes, String ownerType);

    void recordFileDownload();

    void recordJobCreated(String jobType);

    void recordJobCompleted(String jobType);

    void recordJobFailed(String jobType);

    void recordDuplicateCandidateCreated(String detectionMethod);
}

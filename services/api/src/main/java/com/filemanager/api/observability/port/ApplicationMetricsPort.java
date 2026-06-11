package com.filemanager.api.observability.port;

public interface ApplicationMetricsPort {
    void recordFileUpload(long bytes, String ownerType);

    void recordFileDownload();

    void recordJobCreated(String jobType);

    void recordJobCompleted(String jobType);

    void recordJobFailed(String jobType);

    void recordDuplicateSearchRequested();

    void recordDuplicateSearchMethodCompleted(String method);

    void recordDuplicateSearchMethodNotReady(String method);

    void recordDuplicateSearchMethodDisabled(String method);

    void recordDuplicateMatchesReturned(String method, int count);

    void recordDuplicateGroupsRequested();

    void recordDuplicateGroupsReturned(String method, int count);
}

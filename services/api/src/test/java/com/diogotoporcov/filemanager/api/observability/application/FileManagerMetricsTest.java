package com.diogotoporcov.filemanager.api.observability.application;

import com.diogotoporcov.filemanager.api.observability.port.ApplicationMetricsPort;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FileManagerMetricsTest {

    @Mock
    private ApplicationMetricsPort applicationMetricsPort;

    private FileManagerMetrics metrics;

    @BeforeEach
    void setUp() {
        metrics = new FileManagerMetrics(applicationMetricsPort);
    }

    @Test
    void recordFileUpload() {
        metrics.recordFileUpload(100L, "USER");

        verify(applicationMetricsPort).recordFileUpload(100L, "USER");
    }

    @Test
    void recordFileDownload() {
        metrics.recordFileDownload();

        verify(applicationMetricsPort).recordFileDownload();
    }

    @Test
    void recordJobCreated() {
        metrics.recordJobCreated("CHECKSUM");

        verify(applicationMetricsPort).recordJobCreated("CHECKSUM");
    }

    @Test
    void recordJobCompleted() {
        metrics.recordJobCompleted("PHASH");

        verify(applicationMetricsPort).recordJobCompleted("PHASH");
    }

    @Test
    void recordJobFailed() {
        metrics.recordJobFailed("CHECKSUM");

        verify(applicationMetricsPort).recordJobFailed("CHECKSUM");
    }

    @Test
    void recordDuplicateSearchMetrics() {
        metrics.recordDuplicateSearchRequested();
        metrics.recordDuplicateSearchMethodCompleted("EXACT");
        metrics.recordDuplicateSearchMethodNotReady("IMAGE_PHASH");
        metrics.recordDuplicateSearchMethodDisabled("IMAGE_EMBEDDING");
        metrics.recordDuplicateMatchesReturned("EXACT", 2);

        verify(applicationMetricsPort).recordDuplicateSearchRequested();
        verify(applicationMetricsPort).recordDuplicateSearchMethodCompleted("EXACT");
        verify(applicationMetricsPort).recordDuplicateSearchMethodNotReady("IMAGE_PHASH");
        verify(applicationMetricsPort).recordDuplicateSearchMethodDisabled("IMAGE_EMBEDDING");
        verify(applicationMetricsPort).recordDuplicateMatchesReturned("EXACT", 2);
    }

    @Test
    void recordDuplicateGroupMetrics() {
        metrics.recordDuplicateGroupsRequested();
        metrics.recordDuplicateGroupsReturned("EXACT", 1);

        verify(applicationMetricsPort).recordDuplicateGroupsRequested();
        verify(applicationMetricsPort).recordDuplicateGroupsReturned("EXACT", 1);
    }

    @Test
    void recordOperationDuration() {
        Duration duration = Duration.ofMillis(12);

        metrics.recordOperationDuration("duplicate.search", "success", duration);

        verify(applicationMetricsPort).recordOperationDuration("duplicate.search", "success", duration);
    }
}

package com.filemanager.api.observability.application;

import com.filemanager.api.observability.port.ApplicationMetricsPort;
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
}

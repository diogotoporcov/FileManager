package com.filemanager.api.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FileManagerMetricsTest {

    private MeterRegistry registry;
    private FileManagerMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new FileManagerMetrics(registry);
    }

    @Test
    void recordFileUpload() {
        metrics.recordFileUpload(100L, "USER");
        
        Counter counter = registry.find("filemanager.files.uploaded").tag("owner_type", "USER").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
        
        DistributionSummary summary = registry.find("filemanager.files.upload.bytes").tag("owner_type", "USER").summary();
        assertThat(summary).isNotNull();
        assertThat(summary.totalAmount()).isEqualTo(100.0);
    }

    @Test
    void recordFileDownload() {
        metrics.recordFileDownload();
        Counter counter = registry.find("filemanager.files.downloaded").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void recordJobCreated() {
        metrics.recordJobCreated("CHECKSUM");
        Counter counter = registry.find("filemanager.processing.jobs.created").tag("job_type", "CHECKSUM").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void recordJobCompleted() {
        metrics.recordJobCompleted("PHASH");
        Counter counter = registry.find("filemanager.processing.jobs.completed").tag("job_type", "PHASH").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void recordJobFailed() {
        metrics.recordJobFailed("CHECKSUM");
        Counter counter = registry.find("filemanager.processing.jobs.failed").tag("job_type", "CHECKSUM").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }
}

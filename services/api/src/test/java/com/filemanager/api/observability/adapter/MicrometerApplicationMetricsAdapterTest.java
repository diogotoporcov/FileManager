package com.filemanager.api.observability.adapter;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MicrometerApplicationMetricsAdapterTest {

    private MeterRegistry registry;
    private MicrometerApplicationMetricsAdapter metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new MicrometerApplicationMetricsAdapter(registry);
    }

    @Test
    void recordFileUpload() {
        metrics.recordFileUpload(100L, "USER");

        Counter counter = counter("filemanager.files.uploaded", "owner_type", "USER");
        assertThat(counter.count()).isEqualTo(1.0);

        DistributionSummary summary = summary("filemanager.files.upload.bytes", "owner_type", "USER");
        assertThat(summary.totalAmount()).isEqualTo(100.0);
    }

    @Test
    void recordFileDownload() {
        metrics.recordFileDownload();

        Counter counter = counter("filemanager.files.downloaded");
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void recordJobCreated() {
        metrics.recordJobCreated("CHECKSUM");

        Counter counter = counter("filemanager.processing.jobs.created", "job_type", "CHECKSUM");
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void recordJobCompleted() {
        metrics.recordJobCompleted("PHASH");

        Counter counter = counter("filemanager.processing.jobs.completed", "job_type", "PHASH");
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void recordJobFailed() {
        metrics.recordJobFailed("CHECKSUM");

        Counter counter = counter("filemanager.processing.jobs.failed", "job_type", "CHECKSUM");
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void recordDuplicateSearchMetrics() {
        metrics.recordDuplicateSearchRequested();
        metrics.recordDuplicateSearchMethodCompleted("EXACT");
        metrics.recordDuplicateSearchMethodNotReady("IMAGE_PHASH");
        metrics.recordDuplicateSearchMethodDisabled("IMAGE_EMBEDDING");
        metrics.recordDuplicateMatchesReturned("EXACT", 3);

        assertThat(counter("filemanager.duplicate.search.requested").count()).isEqualTo(1.0);
        assertThat(counter("filemanager.duplicate.search.method.completed", "method", "EXACT").count()).isEqualTo(1.0);
        assertThat(counter("filemanager.duplicate.search.method.not_ready", "method", "IMAGE_PHASH").count())
                .isEqualTo(1.0);
        assertThat(counter("filemanager.duplicate.search.method.disabled", "method", "IMAGE_EMBEDDING").count())
                .isEqualTo(1.0);
        assertThat(summary("filemanager.duplicate.search.matches_returned", "method", "EXACT").totalAmount())
                .isEqualTo(3.0);
    }

    @Test
    void recordDuplicateGroupMetrics() {
        metrics.recordDuplicateGroupsRequested();
        metrics.recordDuplicateGroupsReturned("EXACT", 2);

        assertThat(counter("filemanager.duplicate.groups.requested").count()).isEqualTo(1.0);
        assertThat(summary("filemanager.duplicate.groups.returned", "method", "EXACT").totalAmount())
                .isEqualTo(2.0);
    }

    private Counter counter(String name) {
        return Objects.requireNonNull(registry.find(name).counter());
    }

    private Counter counter(String name, String tagKey, String tagValue) {
        return Objects.requireNonNull(registry.find(name).tag(tagKey, tagValue).counter());
    }

    private DistributionSummary summary(String name, String tagKey, String tagValue) {
        return Objects.requireNonNull(registry.find(name).tag(tagKey, tagValue).summary());
    }
}

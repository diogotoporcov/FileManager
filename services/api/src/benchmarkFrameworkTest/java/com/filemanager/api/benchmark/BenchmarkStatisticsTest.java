package com.filemanager.api.benchmark;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class BenchmarkStatisticsTest {
    @Test
    void rejectsEmptySamples() {
        assertThatThrownBy(() -> BenchmarkStatistics.fromSamples(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be empty");
    }

    @Test
    void calculatesOneSample() {
        BenchmarkStatistics statistics = BenchmarkStatistics.fromDurations(List.of(12.0));

        assertThat(statistics.minMs()).isEqualTo(12.0);
        assertThat(statistics.maxMs()).isEqualTo(12.0);
        assertThat(statistics.meanMs()).isEqualTo(12.0);
        assertThat(statistics.p50Ms()).isEqualTo(12.0);
        assertThat(statistics.standardDeviationMs()).isEqualTo(0.0);
    }

    @Test
    void calculatesPercentilesFromUnsortedInput() {
        BenchmarkStatistics statistics = BenchmarkStatistics.fromDurations(List.of(10.0, 1.0, 8.0, 3.0, 5.0));

        assertThat(statistics.p50Ms()).isEqualTo(5.0);
        assertThat(statistics.p90Ms()).isEqualTo(10.0);
        assertThat(statistics.p95Ms()).isEqualTo(10.0);
    }

    @Test
    void doesNotPublishP99BelowOneHundredSamples() {
        List<Double> samples = java.util.stream.IntStream.rangeClosed(1, 99)
                .mapToDouble(value -> (double) value)
                .boxed()
                .toList();

        BenchmarkStatistics statistics = BenchmarkStatistics.fromDurations(samples);

        assertThat(statistics.p99Ms()).isNull();
        assertThat(statistics.p99Status()).isEqualTo("INSUFFICIENT_SAMPLES");
    }

    @Test
    void publishesP99AtOneHundredSamples() {
        List<Double> samples = java.util.stream.IntStream.rangeClosed(1, 100)
                .mapToDouble(value -> (double) value)
                .boxed()
                .toList();

        BenchmarkStatistics statistics = BenchmarkStatistics.fromDurations(samples);

        assertThat(statistics.p99Ms()).isEqualTo(99.0);
        assertThat(statistics.p99Status()).isEqualTo("AVAILABLE");
    }

    @Test
    void usesPopulationStandardDeviation() {
        BenchmarkStatistics statistics = BenchmarkStatistics.fromDurations(List.of(2.0, 4.0, 4.0, 4.0, 5.0, 5.0, 7.0, 9.0));

        assertThat(BenchmarkStatistics.STANDARD_DEVIATION_METHOD).isEqualTo("population");
        assertThat(statistics.standardDeviationMs()).isEqualTo(2.0);
    }

    @Test
    void failedSamplesCountButDoNotAffectLatencyStatistics() {
        BenchmarkStatistics statistics = BenchmarkStatistics.fromSamples(List.of(
                new BenchmarkSample(true, 10.0),
                new BenchmarkSample(false, 1_000.0),
                new BenchmarkSample(true, 20.0)));

        assertThat(statistics.sampleCount()).isEqualTo(2);
        assertThat(statistics.successCount()).isEqualTo(2);
        assertThat(statistics.failureCount()).isEqualTo(1);
        assertThat(statistics.maxMs()).isEqualTo(20.0);
    }
}

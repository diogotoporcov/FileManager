package com.filemanager.api.benchmark;

import java.util.ArrayList;
import java.util.DoubleSummaryStatistics;
import java.util.List;

final class BenchmarkStatistics {
    static final String PERCENTILE_METHOD = "nearest-rank";
    static final String STANDARD_DEVIATION_METHOD = "population";

    private final int sampleCount;
    private final int successCount;
    private final int failureCount;
    private final double minMs;
    private final double maxMs;
    private final double meanMs;
    private final double p50Ms;
    private final double p90Ms;
    private final double p95Ms;
    private final Double p99Ms;
    private final String p99Status;
    private final double standardDeviationMs;

    private BenchmarkStatistics(
            int sampleCount,
            int successCount,
            int failureCount,
            double minMs,
            double maxMs,
            double meanMs,
            double p50Ms,
            double p90Ms,
            double p95Ms,
            Double p99Ms,
            String p99Status,
            double standardDeviationMs) {
        this.sampleCount = sampleCount;
        this.successCount = successCount;
        this.failureCount = failureCount;
        this.minMs = minMs;
        this.maxMs = maxMs;
        this.meanMs = meanMs;
        this.p50Ms = p50Ms;
        this.p90Ms = p90Ms;
        this.p95Ms = p95Ms;
        this.p99Ms = p99Ms;
        this.p99Status = p99Status;
        this.standardDeviationMs = standardDeviationMs;
    }

    static ResultCountStatistics resultCounts(List<BenchmarkSample> samples) {
        List<Integer> successfulResultCounts = samples.stream()
                .filter(BenchmarkSample::success)
                .map(BenchmarkSample::resultCount)
                .sorted()
                .toList();

        if (successfulResultCounts.isEmpty()) {
            throw new IllegalArgumentException("Benchmark samples must contain at least one success");
        }

        return new ResultCountStatistics(
                successfulResultCounts.getFirst(),
                intPercentile(successfulResultCounts, 50),
                intPercentile(successfulResultCounts, 95),
                successfulResultCounts.getLast());
    }

    static BenchmarkStatistics fromSamples(List<BenchmarkSample> samples) {
        if (samples.isEmpty()) {
            throw new IllegalArgumentException("Benchmark samples must not be empty");
        }

        List<Double> successfulDurations = samples.stream()
                .filter(BenchmarkSample::success)
                .map(BenchmarkSample::durationMs)
                .sorted()
                .toList();

        if (successfulDurations.isEmpty()) {
            throw new IllegalArgumentException("Benchmark samples must contain at least one success");
        }

        DoubleSummaryStatistics summary = successfulDurations.stream()
                .mapToDouble(Double::doubleValue)
                .summaryStatistics();
        double mean = summary.getAverage();

        double variance = successfulDurations.stream()
                .mapToDouble(value -> Math.pow(value - mean, 2))
                .sum() / successfulDurations.size();
        Double p99 = successfulDurations.size() < 100 ? null : percentile(successfulDurations, 99);

        return new BenchmarkStatistics(
                successfulDurations.size(),
                successfulDurations.size(),
                samples.size() - successfulDurations.size(),
                summary.getMin(),
                summary.getMax(),
                mean,
                percentile(successfulDurations, 50),
                percentile(successfulDurations, 90),
                percentile(successfulDurations, 95),
                p99,
                p99 == null ? "INSUFFICIENT_SAMPLES" : "AVAILABLE",
                Math.sqrt(variance));
    }

    static BenchmarkStatistics fromDurations(List<Double> durations) {
        if (durations.isEmpty()) {
            throw new IllegalArgumentException("Benchmark durations must not be empty");
        }

        List<BenchmarkSample> samples = new ArrayList<>(durations.size());

        for (Double duration : durations) {
            samples.add(new BenchmarkSample(true, duration));
        }

        return fromSamples(samples);
    }

    private static double percentile(List<Double> sorted, int percentile) {
        int index = (int) Math.ceil((percentile / 100.0) * sorted.size()) - 1;

        return sorted.get(Math.clamp(index, 0, sorted.size() - 1));
    }

    private static int intPercentile(List<Integer> sorted, int percentile) {
        int index = (int) Math.ceil((percentile / 100.0) * sorted.size()) - 1;

        return sorted.get(Math.clamp(index, 0, sorted.size() - 1));
    }

    int sampleCount() {
        return sampleCount;
    }

    int successCount() {
        return successCount;
    }

    int failureCount() {
        return failureCount;
    }

    double minMs() {
        return minMs;
    }

    double maxMs() {
        return maxMs;
    }

    double meanMs() {
        return meanMs;
    }

    double p50Ms() {
        return p50Ms;
    }

    double p90Ms() {
        return p90Ms;
    }

    double p95Ms() {
        return p95Ms;
    }

    Double p99Ms() {
        return p99Ms;
    }

    String p99Status() {
        return p99Status;
    }

    double standardDeviationMs() {
        return standardDeviationMs;
    }

}

record ResultCountStatistics(int min, int p50, int p95, int max) {
}

record BenchmarkSample(boolean success, double durationMs, int resultCount) {
    BenchmarkSample(boolean success, double durationMs) {
        this(success, durationMs, 0);
    }
}

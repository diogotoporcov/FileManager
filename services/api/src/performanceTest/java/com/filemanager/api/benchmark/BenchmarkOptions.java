package com.filemanager.api.benchmark;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

record BenchmarkOptions(
        int records,
        long seed,
        int warmupIterations,
        int measuredIterations,
        String concurrency,
        String duplicateDistribution,
        String benchmarkProfile,
        BenchmarkProfileSupport.BaselineMetadata baselineMetadata,
        String runId,
        String instrumentationMode,
        Path reportsDir,
        Path resultsDir,
        String pythonExecutable,
        String pythonExecutableSource,
        String pythonFallbacksAttempted,
        Map<String, BenchmarkConfigurationResolver.ResolvedValue<?>> resolvedConfiguration) {

    static BenchmarkOptions fromSystemProperties() {
        BenchmarkOptions options = new BenchmarkConfigurationResolver().resolve();

        options.validate();

        return options;
    }

    String scaleLabel() {
        return switch (records) {
            case 10_000 -> "10k";
            case 100_000 -> "100k";
            case 1_000_000 -> "1m";
            default -> Integer.toString(records);
        };
    }

    void validate() {
        if (records <= 0) {
            throw new IllegalArgumentException("benchmark.records must be greater than zero");
        }

        if (warmupIterations < 0) {
            throw new IllegalArgumentException("benchmark.warmup-iterations must be zero or greater");
        }

        if (measuredIterations <= 0) {
            throw new IllegalArgumentException("benchmark.measured-iterations must be greater than zero");
        }

        for (String value : concurrency.split(",")) {
            if (Integer.parseInt(value.trim()) <= 0) {
                throw new IllegalArgumentException("benchmark.concurrency values must be positive integers");
            }
        }

        if (!Set.of("metrics", "tracing").contains(instrumentationMode)) {
            throw new IllegalArgumentException("benchmark.instrumentation-mode must be metrics or tracing");
        }

        BenchmarkProfileSupport.validateProfile(benchmarkProfile);
        BenchmarkProfileSupport.validateBaselineMetadata(benchmarkProfile, baselineMetadata);

        if (runId == null || runId.isBlank()) {
            throw new IllegalArgumentException("benchmark.run-id is required");
        }

        if (pythonExecutable == null || pythonExecutable.isBlank()) {
            throw new IllegalArgumentException("benchmark.python-executable is required");
        }

        if (pythonExecutableSource == null || pythonExecutableSource.isBlank()) {
            throw new IllegalArgumentException("benchmark.python-executable.source is required");
        }
    }
}

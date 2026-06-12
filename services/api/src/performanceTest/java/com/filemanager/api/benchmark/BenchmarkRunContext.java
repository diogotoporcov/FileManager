package com.filemanager.api.benchmark;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

record BenchmarkRunContext(
        String runId,
        String scale,
        int recordCount,
        Path scaleDir,
        Path resultsScaleDir,
        BenchmarkOptions options,
        String gitCommitSha,
        int schemaVersion) {
    static BenchmarkRunContext create(BenchmarkOptions options) throws Exception {
        Path scaleDir = options.reportsDir().resolve(options.runId()).resolve(options.scaleLabel());

        if (Files.exists(scaleDir)) {
            deleteGeneratedScaleDir(scaleDir);
        }

        Files.createDirectories(scaleDir);

        Path resultsScaleDir = options.resultsDir().resolve(options.runId()).resolve(options.scaleLabel());
        Files.createDirectories(resultsScaleDir);

        return new BenchmarkRunContext(
                options.runId(),
                options.scaleLabel(),
                options.records(),
                scaleDir,
                resultsScaleDir,
                options,
                BenchmarkSupport.commandOutput("git", "rev-parse", "HEAD"),
                BenchmarkSupport.SCHEMA_VERSION);
    }

    private static void deleteGeneratedScaleDir(Path scaleDir) throws Exception {
        try (var paths = Files.walk(scaleDir)) {
            paths.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (Exception ex) {
                            throw new IllegalStateException("Failed to clean benchmark output: " + path, ex);
                        }
                    });
        }
    }
}

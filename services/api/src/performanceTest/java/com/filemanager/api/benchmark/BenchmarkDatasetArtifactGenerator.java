package com.filemanager.api.benchmark;

import java.nio.file.Path;

final class BenchmarkDatasetArtifactGenerator {
    private BenchmarkDatasetArtifactGenerator() {
    }

    static void main() throws Exception {
        BenchmarkOptions options = BenchmarkOptions.fromSystemProperties();
        BenchmarkDatasetPlan dataset = new BenchmarkDatasetGenerator(options).generate();
        Path generatedRoot = Path.of(System.getProperty(
                "benchmark.datasets-dir",
                "benchmarks/datasets/generated"));
        Path datasetDir = new BenchmarkDatasetArtifactStore().writeGeneratedDataset(dataset, generatedRoot);

        System.out.println("Generated benchmark dataset: " + datasetDir);
    }
}

package com.filemanager.api.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BenchmarkDatasetGeneratorTest {
    @Test
    void generationIsDeterministicForSameOptions() {
        BenchmarkOptions options = options(10_000);

        BenchmarkDatasetPlan first = new BenchmarkDatasetGenerator(options).generate();
        BenchmarkDatasetPlan second = new BenchmarkDatasetGenerator(options).generate();

        assertThat(first.toManifest()).isEqualTo(second.toManifest());
        assertThat(first.expectedTableCounts()).isEqualTo(second.expectedTableCounts());
    }

    @Test
    void extractedGeneratorPreservesDatasetRowCounts() {
        BenchmarkDatasetPlan dataset = new BenchmarkDatasetGenerator(options(10_000)).generate();

        assertThat(dataset.expectedTableCounts()).containsExactly(
                Map.entry("users", 5L),
                Map.entry("folders", 2L),
                Map.entry("folder_closure", 2L),
                Map.entry("files", 10_000L),
                Map.entry("file_fingerprints", 9_976L),
                Map.entry("exact_duplicate_groups", 9_953L),
                Map.entry("image_fingerprints", 6L),
                Map.entry("file_embeddings", 6L),
                Map.entry("audio_fingerprints", 7L),
                Map.entry("video_embeddings", 4L),
                Map.entry("duplicate_candidates", 7L),
                Map.entry("duplicate_candidate_refreshes", 4L),
                Map.entry("file_grants", 1L),
                Map.entry("folder_grants", 0L),
                Map.entry("processing_jobs", 3L));
    }

    @Test
    void manifestReportsMediaEvidenceAndReadModelCounts() {
        BenchmarkDatasetPlan dataset = new BenchmarkDatasetGenerator(options(10_000)).generate();

        assertThat(dataset.toManifest())
                .containsEntry("readModelRows", Map.of(
                        "exact_duplicate_groups", 9_953,
                        "duplicate_candidates", 7,
                        "duplicate_candidate_refreshes", 4));
        assertThat(dataset.toManifest()).containsKey("recordsByFingerprintType");
    }

    private BenchmarkOptions options(int records) {
        return new BenchmarkOptions(
                records,
                20260611L,
                1,
                1,
                "1",
                "default",
                "default",
                new BenchmarkProfileSupport.BaselineMetadata("", "", "", ""),
                "run",
                "metrics",
                Path.of("reports"),
                Path.of("results"),
                "python",
                "FALLBACK",
                "python",
                Map.of());
    }
}

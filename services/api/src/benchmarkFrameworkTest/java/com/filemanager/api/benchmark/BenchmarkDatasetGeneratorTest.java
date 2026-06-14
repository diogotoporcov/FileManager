package com.filemanager.api.benchmark;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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

        assertThat(dataset.expectedTableCounts()).containsKeys(
                "users",
                "folders",
                "folder_closure",
                "files",
                "file_fingerprints",
                "exact_duplicate_groups",
                "image_fingerprints",
                "file_embeddings",
                "audio_fingerprints");
        assertThat(dataset.expectedTableCounts()).doesNotContainKeys(
                "video_embeddings",
                "duplicate_candidates",
                "duplicate_candidate_refreshes",
                "file_grants",
                "folder_grants",
                "processing_jobs");
        assertThat(dataset.expectedTableCounts()).contains(
                Map.entry("users", 5L),
                Map.entry("folders", 2L),
                Map.entry("folder_closure", 2L),
                Map.entry("files", 10_000L),
                Map.entry("image_fingerprints", 6L),
                Map.entry("file_embeddings", 6L));
        assertThat(dataset.expectedTableCounts().get("audio_fingerprints")).isGreaterThan(0);
    }

    @Test
    void manifestReportsMediaEvidenceAndReadModelCounts() {
        BenchmarkDatasetPlan dataset = new BenchmarkDatasetGenerator(options(10_000)).generate();

        assertThat(dataset.toManifest())
                .containsEntry("readModelRows", Map.of(
                        "exact_duplicate_groups", dataset.exactDuplicateGroups.size()));
        assertThat(dataset.toManifest()).containsKey("recordsByFingerprintType");
    }

    @Test
    void primaryPerformanceOperationNamesAreGeneral() {
        BenchmarkDatasetPlan dataset = new BenchmarkDatasetGenerator(options(10_000, "default", 20260611L)).generate();

        assertThat(dataset.registry().operations().keySet()).containsExactly(
                "duplicate.search.EXACT",
                "duplicate.search.IMAGE_PHASH",
                "duplicate.search.IMAGE_EMBEDDING",
                "duplicate.search.AUDIO_FINGERPRINT",
                "duplicate.groups.EXACT");
        assertThat(dataset.registry().operations().keySet())
                .noneMatch(name -> name.contains("one-match") || name.contains("no-match") || name.contains("threshold"));
    }

    @Test
    void methodHeavyDistributionsScaleRelevantEvidenceTables() {
        assertEvidenceCount("exact-heavy", "file_fingerprints", 10_000L);
        assertEvidenceCount("image-phash-heavy", "image_fingerprints", 10_000L);
        assertEvidenceCount("image-embedding-heavy", "file_embeddings", 10_000L);
        assertEvidenceCount("audio-fingerprint-heavy", "audio_fingerprints", 10_000L);
    }

    @Test
    void defaultDistributionDoesNotClaimLargeMediaEvidenceScale() {
        BenchmarkDatasetPlan dataset = new BenchmarkDatasetGenerator(options(10_000, "default", 20260611L)).generate();

        assertThat(dataset.expectedTableCounts().get("files")).isEqualTo(10_000L);
        assertThat(dataset.expectedTableCounts().get("image_fingerprints")).isLessThan(100L);
        assertThat(dataset.expectedTableCounts().get("file_embeddings")).isLessThan(100L);
        assertThat(dataset.expectedTableCounts().get("audio_fingerprints")).isLessThan(100L);
    }

    @Test
    void registryIsDeterministicForSameConfigAndSeed() {
        BenchmarkDatasetPlan first = new BenchmarkDatasetGenerator(options(10_000, "image-embedding-heavy", 20260611L))
                .generate();
        BenchmarkDatasetPlan second = new BenchmarkDatasetGenerator(options(10_000, "image-embedding-heavy", 20260611L))
                .generate();

        assertThat(first.registry().toManifest()).isEqualTo(second.registry().toManifest());
    }

    @Test
    void differentSeedProducesDifferentRegistrySources() {
        BenchmarkDatasetPlan first = new BenchmarkDatasetGenerator(options(10_000, "image-embedding-heavy", 20260611L))
                .generate();
        BenchmarkDatasetPlan second = new BenchmarkDatasetGenerator(options(10_000, "image-embedding-heavy", 20260612L))
                .generate();

        assertThat(first.registry().operations().get("duplicate.search.IMAGE_EMBEDDING").sourceFileIds())
                .isNotEqualTo(second.registry().operations().get("duplicate.search.IMAGE_EMBEDDING").sourceFileIds());
    }

    @Test
    void registrySourceIdsReferenceEligibleEvidenceRows() {
        BenchmarkDatasetPlan dataset = new BenchmarkDatasetGenerator(options(10_000, "mixed-image-audio-heavy", 20260611L))
                .generate();
        Set<UUID> fileIds = dataset.files.stream().map(FileRow::id).collect(Collectors.toSet());
        Set<UUID> imageFingerprints = dataset.imageFingerprints.stream()
                .map(ImageFingerprintRow::fileId)
                .collect(Collectors.toSet());
        Set<UUID> fileEmbeddings = dataset.fileEmbeddings.stream()
                .map(FileEmbeddingRow::fileId)
                .collect(Collectors.toSet());
        Set<UUID> audioFingerprints = dataset.audioFingerprints.stream()
                .map(AudioFingerprintRow::fileId)
                .collect(Collectors.toSet());

        assertThat(dataset.registry().operations().get("duplicate.search.IMAGE_PHASH").sourceFileIds())
                .allSatisfy(id -> {
                    assertThat(fileIds).contains(id);
                    assertThat(imageFingerprints).contains(id);
                });
        assertThat(dataset.registry().operations().get("duplicate.search.IMAGE_EMBEDDING").sourceFileIds())
                .allSatisfy(id -> {
                    assertThat(fileIds).contains(id);
                    assertThat(fileEmbeddings).contains(id);
                });
        assertThat(dataset.registry().operations().get("duplicate.search.AUDIO_FINGERPRINT").sourceFileIds())
                .allSatisfy(id -> {
                    assertThat(fileIds).contains(id);
                    assertThat(audioFingerprints).contains(id);
                });
    }

    @Test
    void precomputedDatasetArtifactValidatesAndExcludesRemovedTables(@TempDir Path tempDir) throws Exception {
        BenchmarkDatasetPlan dataset = new BenchmarkDatasetGenerator(options(100, "image-phash-heavy", 20260611L))
                .generate();
        Path datasetDir = new BenchmarkDatasetArtifactStore().writeGeneratedDataset(dataset, tempDir);

        new BenchmarkDatasetArtifactStore().validateDatasetPath(datasetDir, dataset);

        try (var paths = Files.list(datasetDir)) {
            assertThat(paths.map(path -> path.getFileName().toString()).toList())
                    .contains(
                            "dataset-manifest.json",
                            "benchmark-registry.json",
                            "users.csv",
                            "folders.csv",
                            "folder_closure.csv",
                            "files.csv",
                            "file_fingerprints.csv",
                            "image_fingerprints.csv",
                            "file_embeddings.csv",
                            "audio_fingerprints.csv",
                            "exact_duplicate_groups.csv")
                    .doesNotContain(
                            "video_embeddings.csv",
                            "duplicate_candidates.csv",
                            "duplicate_candidate_refreshes.csv",
                            "file_grants.csv",
                            "folder_grants.csv",
                            "processing_jobs.csv");
        }
    }

    @Test
    void precomputedDatasetMismatchFailsClearly(@TempDir Path tempDir) throws Exception {
        BenchmarkDatasetPlan dataset = new BenchmarkDatasetGenerator(options(100, "image-phash-heavy", 20260611L))
                .generate();
        Path datasetDir = new BenchmarkDatasetArtifactStore().writeGeneratedDataset(dataset, tempDir);
        BenchmarkDatasetPlan mismatch = new BenchmarkDatasetGenerator(options(100, "image-phash-heavy", 20260612L))
                .generate();

        assertThatThrownBy(() -> new BenchmarkDatasetArtifactStore().validateDatasetPath(datasetDir, mismatch))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Precomputed dataset mismatch");
    }

    @Test
    void sourceTreeDoesNotContainK6Benchmarks() {
        Path root = repositoryRoot();

        assertThat(root.resolve("benchmarks").resolve("k" + "6")).doesNotExist();
    }

    @Test
    void pgbenchDirectoryContainsOnlyDuplicateEngineScripts() throws Exception {
        Path pgbench = repositoryRoot().resolve("benchmarks/pgbench");

        try (var paths = Files.list(pgbench)) {
            assertThat(paths.map(path -> path.getFileName().toString()).sorted().toList())
                    .containsExactly(
                            "duplicate-audio.sql",
                            "duplicate-exact.sql",
                            "duplicate-groups-exact.sql",
                            "duplicate-image-embedding.sql",
                            "duplicate-image-phash.sql");
        }
    }

    @Test
    void analyzeSqlExcludesRemovedBenchmarkTables() throws Exception {
        String analyzeSql = Files.readString(repositoryRoot().resolve("benchmarks/sql/seed/analyze.sql"));

        assertThat(analyzeSql).contains(
                "ANALYZE users;",
                "ANALYZE folders;",
                "ANALYZE folder_closure;",
                "ANALYZE files;",
                "ANALYZE file_fingerprints;",
                "ANALYZE image_fingerprints;",
                "ANALYZE file_embeddings;",
                "ANALYZE audio_fingerprints;",
                "ANALYZE exact_duplicate_groups;");
        assertThat(analyzeSql).doesNotContain(
                "file_grants",
                "folder_grants",
                "processing_jobs",
                "video_embeddings",
                "duplicate_candidates",
                "duplicate_candidate_refreshes");
    }

    @Test
    void generatedReportAndResultRootsContainOnlyPlaceholders() throws Exception {
        Path root = repositoryRoot();

        assertOnlyGitkeep(root.resolve("benchmarks/reports"));
        assertOnlyGitkeep(root.resolve("benchmarks/results"));
    }

    private BenchmarkOptions options(int records) {
        return options(records, "default", 20260611L);
    }

    private BenchmarkOptions options(int records, String duplicateDistribution, long seed) {
        return new BenchmarkOptions(
                records,
                seed,
                1,
                1,
                "1",
                duplicateDistribution,
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

    private void assertEvidenceCount(String distribution, String evidenceTable, long records) {
        BenchmarkDatasetPlan dataset = new BenchmarkDatasetGenerator(options((int) records, distribution, 20260611L))
                .generate();

        assertThat(dataset.expectedTableCounts().get("files")).isEqualTo(records);
        assertThat(dataset.expectedTableCounts().get(evidenceTable)).isBetween(records - 100L, records + 100L);
    }

    private Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();

        if (current.endsWith(Path.of("services", "api"))) {
            return current.getParent().getParent();
        }

        return current;
    }

    private void assertOnlyGitkeep(Path directory) throws Exception {
        try (var paths = Files.list(directory)) {
            List<String> names = paths.map(path -> path.getFileName().toString()).sorted().toList();

            assertThat(names).containsExactly(".gitkeep");
        }
    }
}

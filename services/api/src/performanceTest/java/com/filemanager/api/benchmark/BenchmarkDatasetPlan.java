package com.filemanager.api.benchmark;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class BenchmarkDatasetPlan {
    private final BenchmarkOptions options;
    private final UUID actorUserId;
    final List<UserRow> users = new ArrayList<>();
    final List<FolderRow> folders = new ArrayList<>();
    final List<FolderClosureRow> folderClosures = new ArrayList<>();
    final List<FileRow> files = new ArrayList<>();
    final List<FileFingerprintRow> fileFingerprints = new ArrayList<>();
    final List<ExactDuplicateGroupRow> exactDuplicateGroups = new ArrayList<>();
    final List<ImageFingerprintRow> imageFingerprints = new ArrayList<>();
    final List<FileEmbeddingRow> fileEmbeddings = new ArrayList<>();
    final List<AudioFingerprintRow> audioFingerprints = new ArrayList<>();
    final List<BenchmarkCase> cases = new ArrayList<>();
    final Map<String, UUID> sources = new LinkedHashMap<>();
    final Map<String, String> hashes = new LinkedHashMap<>();
    private Map<String, Long> actualLoadedCounts = Map.of();
    private BenchmarkRegistry registry;
    private String datasetMode = "inline";
    private String datasetPath;
    private long databaseBytes;
    private long indexBytes;

    BenchmarkDatasetPlan(BenchmarkOptions options, UUID actorUserId) {
        this.options = options;
        this.actorUserId = actorUserId;
    }

    UUID actorUserId() {
        return actorUserId;
    }

    int recordCount() {
        return options.records();
    }

    long seed() {
        return options.seed();
    }

    String duplicateDistribution() {
        return options.duplicateDistribution();
    }

    String datasetId() {
        return options.datasetId();
    }

    String configFingerprint() {
        return options.datasetFingerprint();
    }

    List<BenchmarkCase> cases() {
        return cases;
    }

    UUID source(String name) {
        return sources.get(name);
    }

    String hash(String name) {
        return hashes.get(name);
    }

    BenchmarkRegistry registry() {
        return registry;
    }

    void setRegistry(BenchmarkRegistry registry) {
        this.registry = registry;
    }

    void setDatasetMode(String datasetMode) {
        this.datasetMode = datasetMode;
    }

    void setDatasetPath(String datasetPath) {
        this.datasetPath = datasetPath;
    }

    void setDatabaseBytes(long databaseBytes) {
        this.databaseBytes = databaseBytes;
    }

    void setIndexBytes(long indexBytes) {
        this.indexBytes = indexBytes;
    }

    void setActualLoadedCounts(Map<String, Long> actualLoadedCounts) {
        this.actualLoadedCounts = new LinkedHashMap<>(actualLoadedCounts);
    }

    Map<String, Long> expectedTableCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("users", (long) users.size());
        counts.put("folders", (long) folders.size());
        counts.put("folder_closure", (long) folderClosures.size());
        counts.put("files", (long) files.size());
        counts.put("file_fingerprints", (long) fileFingerprints.size());
        counts.put("exact_duplicate_groups", (long) exactDuplicateGroups.size());
        counts.put("image_fingerprints", (long) imageFingerprints.size());
        counts.put("file_embeddings", (long) fileEmbeddings.size());
        counts.put("audio_fingerprints", (long) audioFingerprints.size());

        return counts;
    }

    Map<String, Object> toManifest() {
        Map<String, Object> manifest = new LinkedHashMap<>();

        manifest.put("schemaVersion", BenchmarkSupport.SCHEMA_VERSION);
        manifest.put("datasetId", options.datasetId());
        manifest.put("datasetMode", datasetMode);
        manifest.put("datasetPath", datasetPath);
        manifest.put("configFingerprint", options.datasetFingerprint());
        manifest.put("seed", options.seed());
        manifest.put("benchmarkScaleLabel", options.scaleLabel());
        manifest.put("recordCount", options.records());
        manifest.put("duplicateDistribution", options.duplicateDistribution());
        manifest.put("tableCounts", expectedTableCounts());
        manifest.put("actualLoadedTableCounts", actualLoadedCounts);
        manifest.put("actualEvidenceTableCounts", actualEvidenceTableCounts());
        manifest.put("recordsByMimeFamily", recordsByMimeFamily());
        manifest.put("recordsByOwner", recordsByOwner());

        manifest.put("recordsByFingerprintType", Map.of(
                "EXACT", fileFingerprints.size(),
                "IMAGE_PHASH", imageFingerprints.size(),
                "IMAGE_EMBEDDING", fileEmbeddings.size(),
                "AUDIO_FINGERPRINT", audioFingerprints.size()));
        manifest.put("readModelRows", Map.of(
                "exact_duplicate_groups", exactDuplicateGroups.size()));

        manifest.put("duplicateGroupsByMethod", Map.of(
                "EXACT", 2));

        manifest.put("selectedBenchmarkSources", sources);
        manifest.put("benchmarkRegistry", registry.toManifest());
        manifest.put("sourceRegistrySampleSizes", sourceRegistrySampleSizes());
        manifest.put("expectedMatchesBySourceAndMethod", expectedMatchesBySourceAndMethod());
        manifest.put("deletedFiles", files.stream().filter(file -> file.deletedAt() != null).count());
        manifest.put("deletedFolders", folders.stream().filter(folder -> folder.deletedAt() != null).count());
        manifest.put("foreignOwnerMatches", 4);
        manifest.put("sharedForeignOwnerMatches", 0);
        manifest.put("embeddingModels", Map.of(BenchmarkDatasetGenerator.MODEL_NAME, BenchmarkDatasetGenerator.MODEL_VERSION));
        manifest.put("embeddingDimension", 768);
        manifest.put("embeddingModelName", BenchmarkDatasetGenerator.MODEL_NAME);
        manifest.put("embeddingModelVersion", BenchmarkDatasetGenerator.MODEL_VERSION);
        manifest.put("audioFingerprintAlgorithm", "chromaprint");
        manifest.put("audioFingerprintVersion", "fpcalc-v1");
        manifest.put("databaseBytes", databaseBytes);
        manifest.put("indexBytes", indexBytes);

        return manifest;
    }

    boolean activeFolder(UUID folderId) {
        if (folderId == null) {
            return true;
        }

        for (FolderRow folder : folders) {
            if (folder.id().equals(folderId)) {
                return folder.deletedAt() == null;
            }
        }

        return true;
    }

    private Map<String, Long> actualEvidenceTableCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();

        counts.put("files", actualLoadedCounts.getOrDefault("files", (long) files.size()));
        counts.put("file_fingerprints", actualLoadedCounts.getOrDefault("file_fingerprints", (long) fileFingerprints.size()));
        counts.put("image_fingerprints", actualLoadedCounts.getOrDefault("image_fingerprints", (long) imageFingerprints.size()));
        counts.put("file_embeddings", actualLoadedCounts.getOrDefault("file_embeddings", (long) fileEmbeddings.size()));
        counts.put("audio_fingerprints", actualLoadedCounts.getOrDefault("audio_fingerprints", (long) audioFingerprints.size()));
        counts.put("exact_duplicate_groups", actualLoadedCounts.getOrDefault(
                "exact_duplicate_groups",
                (long) exactDuplicateGroups.size()));

        return counts;
    }

    private Map<String, Integer> sourceRegistrySampleSizes() {
        Map<String, Integer> sampleSizes = new LinkedHashMap<>();

        for (BenchmarkRegistryOperation operation : registry.operations().values()) {
            sampleSizes.put(operation.operation(), operation.sampleSize());
        }

        return sampleSizes;
    }

    private Map<String, Long> recordsByMimeFamily() {
        Map<String, Long> counts = new LinkedHashMap<>();

        for (FileRow file : files) {
            String family = file.mimeType().split("/")[0];

            counts.merge(family, 1L, Long::sum);
        }

        return counts;
    }

    private Map<String, Long> recordsByOwner() {
        Map<String, Long> counts = new LinkedHashMap<>();

        for (FileRow file : files) {
            counts.merge(file.ownerUserId().toString(), 1L, Long::sum);
        }

        return counts;
    }

    private Map<String, Map<String, Object>> expectedMatchesBySourceAndMethod() {
        Map<String, Map<String, Object>> expected = new LinkedHashMap<>();

        for (BenchmarkCase benchmarkCase : cases) {
            expected.put(benchmarkCase.name(), Map.of(
                    "method", benchmarkCase.method().name(),
                    "sourceFileId", benchmarkCase.sourceFileId(),
                    "expectedMatches", benchmarkCase.expectedMatches()));
        }

        return expected;
    }
}

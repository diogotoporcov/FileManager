package com.filemanager.api.benchmark;

import com.filemanager.api.duplicate.domain.DuplicateSearchMethod;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

final class BenchmarkRegistry {
    private static final int SOURCE_SAMPLE_SIZE = 100;
    private final BenchmarkOptions options;
    private final Map<String, Long> evidenceTableCounts;
    private final Map<String, BenchmarkRegistryOperation> operations;

    private BenchmarkRegistry(
            BenchmarkOptions options,
            Map<String, Long> evidenceTableCounts,
            Map<String, BenchmarkRegistryOperation> operations) {
        this.options = options;
        this.evidenceTableCounts = evidenceTableCounts;
        this.operations = operations;
    }

    static BenchmarkRegistry generate(BenchmarkOptions options, BenchmarkDatasetPlan dataset) {
        Map<String, BenchmarkRegistryOperation> operations = new LinkedHashMap<>();
        Set<UUID> exactFileIds = dataset.fileFingerprints.stream()
                .map(FileFingerprintRow::fileId)
                .collect(Collectors.toSet());
        Set<UUID> phashFileIds = dataset.imageFingerprints.stream()
                .map(ImageFingerprintRow::fileId)
                .collect(Collectors.toSet());
        Set<UUID> embeddingFileIds = dataset.fileEmbeddings.stream()
                .map(FileEmbeddingRow::fileId)
                .collect(Collectors.toSet());
        Set<UUID> audioFileIds = dataset.audioFingerprints.stream()
                .map(AudioFingerprintRow::fileId)
                .collect(Collectors.toSet());

        if (includesSearch(options.duplicateDistribution(), DuplicateSearchMethod.EXACT)) {
            addSearchOperation(operations, options, dataset, DuplicateSearchMethod.EXACT, "file_fingerprints", file ->
                    exactFileIds.contains(file.id()));
        }

        if (includesSearch(options.duplicateDistribution(), DuplicateSearchMethod.IMAGE_PHASH)) {
            addSearchOperation(operations, options, dataset, DuplicateSearchMethod.IMAGE_PHASH, "image_fingerprints", file ->
                    file.mimeType().startsWith("image/") && phashFileIds.contains(file.id()));
        }

        if (includesSearch(options.duplicateDistribution(), DuplicateSearchMethod.IMAGE_EMBEDDING)) {
            addSearchOperation(operations, options, dataset, DuplicateSearchMethod.IMAGE_EMBEDDING, "file_embeddings", file ->
                    file.mimeType().startsWith("image/") && embeddingFileIds.contains(file.id()));
        }

        if (includesSearch(options.duplicateDistribution(), DuplicateSearchMethod.AUDIO_FINGERPRINT)) {
            addSearchOperation(operations, options, dataset, DuplicateSearchMethod.AUDIO_FINGERPRINT, "audio_fingerprints", file ->
                    file.mimeType().startsWith("audio/") && audioFileIds.contains(file.id()));
        }

        operations.put("duplicate.groups.EXACT", new BenchmarkRegistryOperation(
                "duplicate.groups.EXACT",
                null,
                "exact_duplicate_groups",
                List.of(),
                1));

        return new BenchmarkRegistry(options, evidenceTableCounts(dataset), operations);
    }

    private static Map<String, Long> evidenceTableCounts(BenchmarkDatasetPlan dataset) {
        Map<String, Long> counts = new LinkedHashMap<>();

        counts.put("files", (long) dataset.files.size());
        counts.put("file_fingerprints", (long) dataset.fileFingerprints.size());
        counts.put("image_fingerprints", (long) dataset.imageFingerprints.size());
        counts.put("file_embeddings", (long) dataset.fileEmbeddings.size());
        counts.put("audio_fingerprints", (long) dataset.audioFingerprints.size());
        counts.put("exact_duplicate_groups", (long) dataset.exactDuplicateGroups.size());

        return counts;
    }

    private static boolean includesSearch(String distribution, DuplicateSearchMethod method) {
        return switch (distribution) {
            case "default" -> true;
            case "exact-heavy" -> method == DuplicateSearchMethod.EXACT;
            case "image-phash-heavy" -> method == DuplicateSearchMethod.IMAGE_PHASH;
            case "image-embedding-heavy" -> method == DuplicateSearchMethod.IMAGE_EMBEDDING;
            case "audio-fingerprint-heavy" -> method == DuplicateSearchMethod.AUDIO_FINGERPRINT;
            case "mixed-image-audio-heavy" -> method == DuplicateSearchMethod.IMAGE_PHASH
                    || method == DuplicateSearchMethod.IMAGE_EMBEDDING
                    || method == DuplicateSearchMethod.AUDIO_FINGERPRINT;
            default -> throw new IllegalArgumentException("Unsupported duplicate distribution: " + distribution);
        };
    }

    private static void addSearchOperation(
            Map<String, BenchmarkRegistryOperation> operations,
            BenchmarkOptions options,
            BenchmarkDatasetPlan dataset,
            DuplicateSearchMethod method,
            String evidenceTable,
            FileSelector selector) {
        String operation = "duplicate.search." + method.name();
        List<UUID> sourceFileIds = dataset.files.stream()
                .filter(file -> file.ownerUserId().equals(dataset.actorUserId()))
                .filter(file -> file.deletedAt() == null)
                .filter(file -> dataset.activeFolder(file.folderId()))
                .filter(selector::matches)
                .sorted(Comparator.comparing(file -> BenchmarkSupport.sha256(
                        options.seed() + ":" + operation + ":" + file.id())))
                .limit(SOURCE_SAMPLE_SIZE)
                .map(FileRow::id)
                .toList();

        if (sourceFileIds.isEmpty()) {
            return;
        }

        operations.put(operation, new BenchmarkRegistryOperation(
                operation,
                method,
                evidenceTable,
                sourceFileIds,
                sourceFileIds.size()));
    }

    Map<String, BenchmarkRegistryOperation> operations() {
        return operations;
    }

    Map<String, Object> toManifest() {
        Map<String, Object> manifest = new LinkedHashMap<>();
        Map<String, Object> operationManifests = new LinkedHashMap<>();

        for (BenchmarkRegistryOperation operation : operations.values()) {
            operationManifests.put(operation.operation(), operation.toManifest());
        }

        manifest.put("schemaVersion", BenchmarkSupport.SCHEMA_VERSION);
        manifest.put("datasetId", options.datasetId());
        manifest.put("configFingerprint", options.datasetFingerprint());
        manifest.put("seed", options.seed());
        manifest.put("records", options.records());
        manifest.put("duplicateDistribution", options.duplicateDistribution());
        manifest.put("evidenceTableCounts", evidenceTableCounts);
        manifest.put("operations", operationManifests);

        return manifest;
    }

    private interface FileSelector {
        boolean matches(FileRow file);
    }
}

record BenchmarkRegistryOperation(
        String operation,
        DuplicateSearchMethod method,
        String evidenceTable,
        List<UUID> sourceFileIds,
        int sampleSize) {
    Map<String, Object> toManifest() {
        Map<String, Object> manifest = new LinkedHashMap<>();

        manifest.put("sourceFileIds", new ArrayList<>(sourceFileIds));
        manifest.put("sampleSize", sampleSize);
        manifest.put("evidenceTable", evidenceTable);

        if (method != null) {
            manifest.put("method", method.name());
        }

        return manifest;
    }
}

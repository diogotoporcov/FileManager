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
    final List<ImageFingerprintRow> imageFingerprints = new ArrayList<>();
    final List<FileEmbeddingRow> fileEmbeddings = new ArrayList<>();
    final List<AudioFingerprintRow> audioFingerprints = new ArrayList<>();
    final List<VideoEmbeddingRow> videoEmbeddings = new ArrayList<>();
    final List<FileGrantRow> fileGrants = new ArrayList<>();
    final List<FolderGrantRow> folderGrants = new ArrayList<>();
    final List<ProcessingJobRow> processingJobs = new ArrayList<>();
    final List<BenchmarkCase> cases = new ArrayList<>();
    final Map<String, UUID> sources = new LinkedHashMap<>();
    final Map<String, String> hashes = new LinkedHashMap<>();
    private Map<String, Long> actualLoadedCounts = Map.of();
    private long databaseBytes;
    private long indexBytes;

    BenchmarkDatasetPlan(BenchmarkOptions options, UUID actorUserId) {
        this.options = options;
        this.actorUserId = actorUserId;
    }

    UUID actorUserId() {
        return actorUserId;
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
        counts.put("image_fingerprints", (long) imageFingerprints.size());
        counts.put("file_embeddings", (long) fileEmbeddings.size());
        counts.put("audio_fingerprints", (long) audioFingerprints.size());
        counts.put("video_embeddings", (long) videoEmbeddings.size());
        counts.put("file_grants", (long) fileGrants.size());
        counts.put("folder_grants", (long) folderGrants.size());
        counts.put("processing_jobs", (long) processingJobs.size());

        return counts;
    }

    Map<String, Object> toManifest() {
        Map<String, Object> manifest = new LinkedHashMap<>();

        manifest.put("schemaVersion", BenchmarkSupport.SCHEMA_VERSION);
        manifest.put("seed", options.seed());
        manifest.put("recordCount", options.records());
        manifest.put("actualLoadedTableCounts", actualLoadedCounts);
        manifest.put("recordsByMimeFamily", recordsByMimeFamily());
        manifest.put("recordsByOwner", recordsByOwner());

        manifest.put("recordsByFingerprintType", Map.of(
                "EXACT", fileFingerprints.size(),
                "IMAGE_PHASH", imageFingerprints.size(),
                "IMAGE_EMBEDDING", fileEmbeddings.size(),
                "AUDIO_FINGERPRINT", audioFingerprints.size(),
                "VIDEO_EMBEDDING", videoEmbeddings.size()));

        manifest.put("duplicateGroupsByMethod", Map.of(
                "EXACT", 2,
                "IMAGE_PHASH", 1,
                "IMAGE_EMBEDDING", 1,
                "AUDIO_FINGERPRINT", 2,
                "VIDEO_EMBEDDING", 1));

        manifest.put("selectedBenchmarkSources", sources);
        manifest.put("expectedMatchesBySourceAndMethod", expectedMatchesBySourceAndMethod());
        manifest.put("deletedFiles", files.stream().filter(file -> file.deletedAt() != null).count());
        manifest.put("deletedFolders", folders.stream().filter(folder -> folder.deletedAt() != null).count());
        manifest.put("foreignOwnerMatches", 5);
        manifest.put("sharedForeignOwnerMatches", 1);
        manifest.put("embeddingModels", Map.of(BenchmarkDatasetGenerator.MODEL_NAME, BenchmarkDatasetGenerator.MODEL_VERSION));
        manifest.put("databaseBytes", databaseBytes);
        manifest.put("indexBytes", indexBytes);

        return manifest;
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

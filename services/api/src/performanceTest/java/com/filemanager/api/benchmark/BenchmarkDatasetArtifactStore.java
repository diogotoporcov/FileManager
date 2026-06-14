package com.filemanager.api.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class BenchmarkDatasetArtifactStore {
    private static final List<TableArtifact> REQUIRED_TABLES = List.of(
            new TableArtifact("users", "users.csv"),
            new TableArtifact("folders", "folders.csv"),
            new TableArtifact("folder_closure", "folder_closure.csv"),
            new TableArtifact("files", "files.csv"),
            new TableArtifact("file_fingerprints", "file_fingerprints.csv"),
            new TableArtifact("image_fingerprints", "image_fingerprints.csv"),
            new TableArtifact("file_embeddings", "file_embeddings.csv"),
            new TableArtifact("audio_fingerprints", "audio_fingerprints.csv"),
            new TableArtifact("exact_duplicate_groups", "exact_duplicate_groups.csv"));
    private static final List<String> REMOVED_ARTIFACTS = List.of(
            "video_embeddings.csv",
            "video_fingerprints.csv",
            "video_frame_fingerprints.csv",
            "video_frame_embeddings.csv",
            "duplicate_candidates.csv",
            "duplicate_candidate_refreshes.csv",
            "file_" + "grants.csv",
            "folder_" + "grants.csv",
            "processing_" + "jobs.csv");
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    Path writeGeneratedDataset(BenchmarkDatasetPlan dataset, Path generatedRoot) throws Exception {
        Path datasetDir = generatedRoot.resolve(dataset.datasetId());

        if (Files.exists(datasetDir)) {
            throw new IllegalStateException("Precomputed dataset already exists: " + datasetDir);
        }

        Files.createDirectories(datasetDir);
        dataset.setDatasetMode("precomputed");
        writeJson(datasetDir.resolve("dataset-manifest.json"), dataset.toManifest());
        writeJson(datasetDir.resolve("benchmark-registry.json"), dataset.registry().toManifest());
        writeRows(datasetDir.resolve("users.csv"), dataset.users);
        writeRows(datasetDir.resolve("folders.csv"), dataset.folders);
        writeRows(datasetDir.resolve("folder_closure.csv"), dataset.folderClosures);
        writeRows(datasetDir.resolve("files.csv"), dataset.files);
        writeRows(datasetDir.resolve("file_fingerprints.csv"), dataset.fileFingerprints);
        writeRows(datasetDir.resolve("image_fingerprints.csv"), dataset.imageFingerprints);
        writeRows(datasetDir.resolve("file_embeddings.csv"), dataset.fileEmbeddings);
        writeRows(datasetDir.resolve("audio_fingerprints.csv"), dataset.audioFingerprints);
        writeRows(datasetDir.resolve("exact_duplicate_groups.csv"), dataset.exactDuplicateGroups);

        return datasetDir;
    }

    void validateDatasetPath(Path datasetPath, BenchmarkDatasetPlan expectedDataset) throws Exception {
        if (!Files.isDirectory(datasetPath)) {
            throw new IllegalStateException("benchmark.datasetPath does not exist or is not a directory: " + datasetPath);
        }

        JsonNode manifest = readObject(datasetPath.resolve("dataset-manifest.json"));
        JsonNode registry = readObject(datasetPath.resolve("benchmark-registry.json"));

        requireEqual("schemaVersion", BenchmarkSupport.SCHEMA_VERSION, manifest.path("schemaVersion").asInt(-1));
        requireEqual("recordCount", expectedDataset.recordCount(), manifest.path("recordCount").asInt(-1));
        requireEqual("duplicateDistribution", expectedDataset.duplicateDistribution(), manifest.path("duplicateDistribution").asText());
        requireEqual("seed", expectedDataset.seed(), manifest.path("seed").asLong(Long.MIN_VALUE));
        requireEqual("configFingerprint", expectedDataset.configFingerprint(), manifest.path("configFingerprint").asText());
        requireEqual("datasetId", expectedDataset.datasetId(), manifest.path("datasetId").asText());

        requireEqual("registry.schemaVersion", BenchmarkSupport.SCHEMA_VERSION, registry.path("schemaVersion").asInt(-1));
        requireEqual("registry.configFingerprint", expectedDataset.configFingerprint(), registry.path("configFingerprint").asText());

        JsonNode tableCounts = manifest.path("tableCounts");
        if (!tableCounts.isObject()) {
            throw new IllegalStateException("dataset-manifest.json must contain tableCounts");
        }

        for (TableArtifact table : REQUIRED_TABLES) {
            Path csv = datasetPath.resolve(table.fileName());
            if (!Files.isRegularFile(csv)) {
                throw new IllegalStateException("Required precomputed dataset file is missing: " + csv);
            }

            long expectedRows = expectedDataset.expectedTableCounts().get(table.tableName());
            requireEqual("tableCounts." + table.tableName(), expectedRows, tableCounts.path(table.tableName()).asLong(-1));

            long actualRows = countRows(csv);
            if (actualRows != expectedRows) {
                throw new IllegalStateException(
                        "Precomputed dataset row count mismatch for " + table.fileName()
                                + ": expected " + expectedRows + ", actual " + actualRows);
            }
        }

        for (String removed : REMOVED_ARTIFACTS) {
            Path path = datasetPath.resolve(removed);
            if (Files.exists(path)) {
                throw new IllegalStateException("Removed benchmark artifact must not exist in new dataset: " + path);
            }
        }
    }

    Map<String, Path> requiredCsvFiles(Path datasetPath) {
        Map<String, Path> files = new LinkedHashMap<>();

        for (TableArtifact table : REQUIRED_TABLES) {
            files.put(table.tableName(), datasetPath.resolve(table.fileName()));
        }

        return files;
    }

    private void writeRows(Path path, List<? extends CsvWritable> rows) throws Exception {
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            for (CsvWritable row : rows) {
                writer.write(row.toCsv());
            }
        }
    }

    private void writeJson(Path path, Object value) throws Exception {
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), value);
    }

    private JsonNode readObject(Path path) throws Exception {
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("Required precomputed dataset artifact is missing: " + path);
        }

        JsonNode node = objectMapper.readTree(path.toFile());
        if (!node.isObject()) {
            throw new IllegalStateException("Expected JSON object: " + path);
        }

        return node;
    }

    private long countRows(Path path) throws Exception {
        try (var lines = Files.lines(path, StandardCharsets.UTF_8)) {
            return lines.count();
        }
    }

    private void requireEqual(String field, Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new IllegalStateException(
                    "Precomputed dataset mismatch for " + field + ": expected " + expected + ", actual " + actual);
        }
    }

    private record TableArtifact(String tableName, String fileName) {
    }
}

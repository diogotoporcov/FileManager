package com.filemanager.api.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;

final class BenchmarkQueryPlanCollector {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    BenchmarkQueryPlanCollector(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    void collect(Path scaleDir, BenchmarkDatasetPlan dataset) throws Exception {
        Path queryPlanDir = scaleDir.resolve("query-plans");
        Files.createDirectories(queryPlanDir);

        Path path = queryPlanDir.resolve("duplicate-exact.json");
        String sql = """
                EXPLAIN (ANALYZE, BUFFERS, WAL, SETTINGS, FORMAT JSON)
                SELECT f.id
                FROM file_fingerprints fp
                JOIN files f ON f.id = fp.file_id
                LEFT JOIN folders folder ON folder.id = f.folder_id
                WHERE fp.algorithm = 'SHA256'
                  AND fp.hash_value = ?
                  AND f.owner_user_id = ?
                  AND f.id <> ?
                  AND f.deleted_at IS NULL
                  AND (f.folder_id IS NULL OR folder.deleted_at IS NULL)
                ORDER BY f.created_at DESC, f.id
                LIMIT 100
                """;
        String plan = jdbcTemplate.queryForObject(
                sql,
                String.class,
                dataset.hash("exact-one-match"),
                dataset.actorUserId(),
                dataset.source("exact-one-match"));

        Files.writeString(path, plan + "\n", StandardCharsets.UTF_8);

        Path summaryPath = queryPlanDir.resolve("duplicate-groups-exact-summary.json");
        String summarySql = """
                EXPLAIN (ANALYZE, BUFFERS, WAL, SETTINGS, FORMAT JSON)
                SELECT algorithm, hash_value, active_file_count
                FROM exact_duplicate_groups
                WHERE owner_user_id = ? AND active_file_count > 1
                ORDER BY active_file_count DESC, algorithm, hash_value
                LIMIT 50
                """;
        String summaryPlan = jdbcTemplate.queryForObject(summarySql, String.class, dataset.actorUserId());

        Files.writeString(summaryPath, summaryPlan + "\n", StandardCharsets.UTF_8);

        writeManifest(scaleDir.resolve("query-plan-manifest.json"));
    }

    private void writeManifest(Path path) throws Exception {
        Files.createDirectories(path.getParent());

        objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), Map.of(
                "schemaVersion", BenchmarkSupport.SCHEMA_VERSION,
                "captured", List.of(
                        Map.of(
                                "operation", "duplicate.search.EXACT.one-match",
                                "scope", "DATABASE_DIRECT",
                                "file", "query-plans/duplicate-exact.json",
                                "status", "COMPLETED"),
                        Map.of(
                                "operation", "duplicate.groups.EXACT.summary.first-50",
                                "scope", "DATABASE_DIRECT",
                                "file", "query-plans/duplicate-groups-exact-summary.json",
                                "status", "COMPLETED")),
                "notCaptured", List.of(
                        notImplemented("duplicate.groups.EXACT.files.first-50"),
                        notImplemented("duplicate.search.IMAGE_PHASH.threshold"),
                        notImplemented("duplicate.search.IMAGE_EMBEDDING.threshold"),
                        notImplemented("duplicate.search.AUDIO_FINGERPRINT.one-match"),
                        notImplemented("duplicate.search.VIDEO_EMBEDDING.one-match"))));
    }

    private Map<String, String> notImplemented(String operation) {
        return Map.of(
                "operation", operation,
                "scope", "DATABASE_DIRECT",
                "status", "NOT_IMPLEMENTED");
    }
}

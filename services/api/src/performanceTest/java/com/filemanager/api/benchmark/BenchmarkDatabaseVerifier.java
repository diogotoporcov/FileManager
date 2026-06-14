package com.filemanager.api.benchmark;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;

final class BenchmarkDatabaseVerifier {
    private final JdbcTemplate jdbcTemplate;

    BenchmarkDatabaseVerifier(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void analyzeTables() {
        for (String table : BenchmarkTables.TABLES) {
            jdbcTemplate.execute("ANALYZE " + table);
        }
    }

    void verifyLoadedCounts(BenchmarkDatasetPlan dataset) {
        Map<String, Long> actualCounts = new LinkedHashMap<>();

        for (String table : BenchmarkTables.TABLES) {
            actualCounts.put(table, count(table));
        }

        dataset.setActualLoadedCounts(actualCounts);

        Map<String, Long> expectedCounts = dataset.expectedTableCounts();

        for (Map.Entry<String, Long> expected : expectedCounts.entrySet()) {
            Long actual = actualCounts.get(expected.getKey());

            if (!Objects.equals(expected.getValue(), actual)) {
                throw new IllegalStateException(
                        "Loaded row count mismatch for " + expected.getKey()
                                + ": expected " + expected.getValue() + ", actual " + actual);
            }
        }
    }

    void enrichDatasetSizes(BenchmarkDatasetPlan dataset) {
        Long databaseBytes = jdbcTemplate.queryForObject(
                "SELECT pg_database_size(current_database())",
                Long.class);
        Long indexBytes = jdbcTemplate.queryForObject(
                """
                SELECT COALESCE(sum(pg_relation_size(indexrelid)), 0)
                FROM pg_index
                WHERE indrelid IN (
                    'files'::regclass,
                    'folders'::regclass,
                    'file_fingerprints'::regclass,
                    'exact_duplicate_groups'::regclass,
                    'image_fingerprints'::regclass,
                    'file_embeddings'::regclass,
                    'audio_fingerprints'::regclass,
                    'folder_closure'::regclass
                )
                """,
                Long.class);

        dataset.setDatabaseBytes(Objects.requireNonNullElse(databaseBytes, 0L));
        dataset.setIndexBytes(Objects.requireNonNullElse(indexBytes, 0L));
    }

    private long count(String tableName) {
        Long count = jdbcTemplate.queryForObject("SELECT count(*) FROM " + tableName, Long.class);

        return Objects.requireNonNullElse(count, 0L);
    }
}

final class BenchmarkTables {
    static final java.util.List<String> TABLES = java.util.List.of(
            "users",
            "folders",
            "folder_closure",
            "files",
            "file_fingerprints",
            "exact_duplicate_groups",
            "image_fingerprints",
            "file_embeddings",
            "audio_fingerprints");

    private BenchmarkTables() {
    }
}

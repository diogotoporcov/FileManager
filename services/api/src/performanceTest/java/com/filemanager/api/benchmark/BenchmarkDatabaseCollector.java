package com.filemanager.api.benchmark;

import java.io.StringWriter;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.jdbc.core.JdbcTemplate;

final class BenchmarkDatabaseCollector {
    private final JdbcTemplate jdbcTemplate;
    private final BenchmarkArtifactWriter artifactWriter;
    private final PgStatStatementsStatus pgStatStatementsStatus = new PgStatStatementsStatus();

    BenchmarkDatabaseCollector(JdbcTemplate jdbcTemplate, BenchmarkArtifactWriter artifactWriter) {
        this.jdbcTemplate = jdbcTemplate;
        this.artifactWriter = artifactWriter;
    }

    void initializePgStatStatements() {
        pgStatStatementsStatus.extensionAttempted = true;

        try {
            jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS pg_stat_statements");
            pgStatStatementsStatus.extensionAvailable = true;
            pgStatStatementsStatus.extensionStatus = ComponentExecutionStatus.COMPLETED.name();
        } catch (RuntimeException ex) {
            pgStatStatementsStatus.status = ComponentExecutionStatus.TOOL_UNAVAILABLE.name();
            pgStatStatementsStatus.extensionStatus = ComponentExecutionStatus.TOOL_UNAVAILABLE.name();
            pgStatStatementsStatus.reason = "pg_stat_statements extension is unavailable.";
        }
    }

    void resetStatementStats() {
        if (!pgStatStatementsStatus.extensionAvailable) {
            return;
        }

        pgStatStatementsStatus.resetAttempted = true;

        try {
            jdbcTemplate.execute("SELECT pg_stat_statements_reset()");
            pgStatStatementsStatus.resetStatus = ComponentExecutionStatus.COMPLETED.name();
        } catch (RuntimeException ex) {
            pgStatStatementsStatus.resetStatus = ComponentExecutionStatus.FAILED.name();
            pgStatStatementsStatus.status = ComponentExecutionStatus.FAILED.name();
            pgStatStatementsStatus.reason = "pg_stat_statements reset failed: " + ex.getClass().getSimpleName();
            throw ex;
        }
    }

    void writeDatabaseStats(Path path) throws Exception {
        if (!pgStatStatementsStatus.extensionAvailable) {
            return;
        }

        pgStatStatementsStatus.collectionAttempted = true;

        var rows = jdbcTemplate.queryForList(
                """
                SELECT query, calls, mean_exec_time, min_exec_time, max_exec_time, total_exec_time, rows
                FROM pg_stat_statements
                ORDER BY total_exec_time DESC
                LIMIT 100
                """);
        pgStatStatementsStatus.collectionStatus = ComponentExecutionStatus.COMPLETED.name();

        StringWriter writer = new StringWriter();

        try (CSVPrinter printer = CSVFormat.DEFAULT.builder()
                .setHeader("query", "calls", "mean_exec_time", "min_exec_time", "max_exec_time", "total_exec_time", "rows")
                .setRecordSeparator('\n')
                .get()
                .print(writer)) {
            for (Map<String, Object> row : rows) {
                printer.printRecord(
                        row.get("query"),
                        row.get("calls"),
                        row.get("mean_exec_time"),
                        row.get("min_exec_time"),
                        row.get("max_exec_time"),
                        row.get("total_exec_time"),
                        row.get("rows"));
            }
        } catch (RuntimeException ex) {
            pgStatStatementsStatus.collectionStatus = ComponentExecutionStatus.FAILED.name();
            pgStatStatementsStatus.status = ComponentExecutionStatus.FAILED.name();
            pgStatStatementsStatus.reason = "pg_stat_statements collection failed: " + ex.getClass().getSimpleName();
            throw ex;
        }

        artifactWriter.writeText(path, writer.toString());
    }

    Map<String, Object> resourceUsage() {
        Runtime runtime = Runtime.getRuntime();
        Map<String, Object> usage = new LinkedHashMap<>();

        usage.put("schemaVersion", BenchmarkSupport.SCHEMA_VERSION);
        usage.put("scope", "JVM_PROCESS");
        usage.put("status", ComponentExecutionStatus.COMPLETED.name());
        usage.put("metrics", Map.of(
                "heapMaxBytes", runtime.maxMemory(),
                "heapTotalBytes", runtime.totalMemory(),
                "heapFreeBytes", runtime.freeMemory(),
                "heapUsedBytes", runtime.totalMemory() - runtime.freeMemory()));

        return usage;
    }

    Map<String, Object> componentStatus() {
        return Map.of(
                "schemaVersion", BenchmarkSupport.SCHEMA_VERSION,
                "components", Map.of(
                        "serviceRepositoryBenchmark", Map.of("status", ComponentExecutionStatus.COMPLETED.name()),
                        "pgbench", Map.of(
                                "status", ComponentExecutionStatus.NOT_REQUESTED.name(),
                                "reason", "Repository benchmark tasks do not invoke pgbench."),
                        "pgStatStatements", pgStatStatementsStatus.toManifest(),
                        "queryPlan", Map.of(
                                "status", ComponentExecutionStatus.COMPLETED.name(),
                                "scope", "PARTIAL"),
                        "resourceUsage", Map.of(
                                "status", ComponentExecutionStatus.COMPLETED.name(),
                                "scope", "JVM_PROCESS")));
    }

    private enum ComponentExecutionStatus {
        COMPLETED,
        NOT_REQUESTED,
        TOOL_UNAVAILABLE,
        FAILED
    }

    private static final class PgStatStatementsStatus {
        private String status = ComponentExecutionStatus.COMPLETED.name();
        private boolean extensionAttempted;
        private boolean extensionAvailable;
        private String extensionStatus = ComponentExecutionStatus.NOT_REQUESTED.name();
        private boolean resetAttempted;
        private String resetStatus = ComponentExecutionStatus.NOT_REQUESTED.name();
        private boolean collectionAttempted;
        private String collectionStatus = ComponentExecutionStatus.NOT_REQUESTED.name();
        private String reason;

        Map<String, Object> toManifest() {
            Map<String, Object> manifest = new LinkedHashMap<>();
            manifest.put("status", status);
            manifest.put("extensionAttempted", extensionAttempted);
            manifest.put("extensionAvailable", extensionAvailable);
            manifest.put("extensionStatus", extensionStatus);
            manifest.put("resetAttempted", resetAttempted);
            manifest.put("resetStatus", resetStatus);
            manifest.put("collectionAttempted", collectionAttempted);
            manifest.put("collectionStatus", collectionStatus);

            if (reason != null) {
                manifest.put("reason", reason);
            }

            return manifest;
        }
    }
}

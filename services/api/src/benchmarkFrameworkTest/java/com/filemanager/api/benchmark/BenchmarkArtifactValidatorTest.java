package com.filemanager.api.benchmark;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BenchmarkArtifactValidatorTest {
    @TempDir
    private Path directory;

    @Test
    void rawValidatorAcceptsCompleteRepositoryBenchmarkArtifactSet() throws Exception {
        writeCompleteRawArtifacts();

        assertThatCode(() -> new BenchmarkRawArtifactValidator(directory).validate()).doesNotThrowAnyException();
    }

    @Test
    void rawValidatorRejectsMalformedJson() throws Exception {
        writeCompleteRawArtifacts();
        Files.writeString(directory.resolve("environment.json"), "{", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> new BenchmarkRawArtifactValidator(directory).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid JSON artifact");
    }

    @Test
    void rawValidatorRejectsUnsupportedSchemaVersion() throws Exception {
        writeCompleteRawArtifacts();
        Files.writeString(directory.resolve("environment.json"), "{\"schemaVersion\":1}", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> new BenchmarkRawArtifactValidator(directory).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unsupported schemaVersion");
    }

    @Test
    void rawValidatorRejectsCorrectnessFailure() throws Exception {
        writeCompleteRawArtifacts();
        Files.writeString(directory.resolve("correctness-results.json"), """
                {
                  "schemaVersion": 3,
                  "passed": false,
                  "caseCount": 1,
                  "passedCount": 0,
                  "failedCount": 1,
                  "cases": [{"name": "case", "passed": false}]
                }
                """, StandardCharsets.UTF_8);

        assertThatThrownBy(() -> new BenchmarkRawArtifactValidator(directory).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("failed correctness");
    }

    @Test
    void rawValidatorRejectsMissingOptionalArtifactForCompletedComponent() throws Exception {
        writeCompleteRawArtifacts();
        Files.delete(directory.resolve("pg-stat-statements.csv"));

        assertThatThrownBy(() -> new BenchmarkRawArtifactValidator(directory).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Required benchmark artifact is missing");
    }

    @Test
    void rawValidatorRejectsCompletedPgStatStatementsWhenResetFailed() throws Exception {
        writeCompleteRawArtifacts();
        Files.writeString(directory.resolve("component-status.json"), componentStatus("""
                    "pgStatStatements": {
                      "status": "COMPLETED",
                      "extensionAvailable": true,
                      "resetStatus": "FAILED",
                      "collectionStatus": "COMPLETED"
                    }
                """), StandardCharsets.UTF_8);

        assertThatThrownBy(() -> new BenchmarkRawArtifactValidator(directory).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pgStatStatements completed status is inconsistent");
    }

    @Test
    void rawValidatorRejectsCompletedPgStatStatementsWhenCollectionFailed() throws Exception {
        writeCompleteRawArtifacts();
        Files.writeString(directory.resolve("component-status.json"), componentStatus("""
                    "pgStatStatements": {
                      "status": "COMPLETED",
                      "extensionAvailable": true,
                      "resetStatus": "COMPLETED",
                      "collectionStatus": "FAILED"
                    }
                """), StandardCharsets.UTF_8);

        assertThatThrownBy(() -> new BenchmarkRawArtifactValidator(directory).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pgStatStatements completed status is inconsistent");
    }

    @Test
    void rawValidatorAcceptsUnavailablePgStatStatementsWithoutArtifact() throws Exception {
        writeCompleteRawArtifacts();
        Files.delete(directory.resolve("pg-stat-statements.csv"));
        Files.writeString(directory.resolve("component-status.json"), componentStatus("""
                    "pgStatStatements": {
                      "status": "TOOL_UNAVAILABLE",
                      "extensionAvailable": false,
                      "resetStatus": "NOT_REQUESTED",
                      "collectionStatus": "NOT_REQUESTED"
                    }
                """), StandardCharsets.UTF_8);

        assertThatCode(() -> new BenchmarkRawArtifactValidator(directory).validate()).doesNotThrowAnyException();
    }

    @Test
    void rawValidatorRejectsUnexpectedArtifactForNotRequestedComponent() throws Exception {
        writeCompleteRawArtifacts();
        Files.writeString(directory.resolve("pgbench-summary.json"), "{}\n", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> new BenchmarkRawArtifactValidator(directory).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unexpected placeholder benchmark artifact exists");
    }

    @Test
    void rawValidatorRejectsRemovedBenchmarkTableDimension() throws Exception {
        writeCompleteRawArtifacts();
        Files.writeString(directory.resolve("dataset-manifest.json"), """
                {
                  "schemaVersion": 3,
                  "datasetId": "default-10000-20260611-test",
                  "datasetMode": "inline",
                  "configFingerprint": "fingerprint",
                  "recordCount": 10000,
                  "seed": 20260611,
                  "duplicateDistribution": "default",
                  "tableCounts": {"file_grants": 1},
                  "actualLoadedTableCounts": {},
                  "actualEvidenceTableCounts": {},
                  "sourceRegistrySampleSizes": {}
                }
                """, StandardCharsets.UTF_8);

        assertThatThrownBy(() -> new BenchmarkRawArtifactValidator(directory).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Removed benchmark dimension");
    }

    @Test
    void rawValidatorRejectsRemovedBenchmarkOperation() throws Exception {
        writeCompleteRawArtifacts();
        Files.writeString(directory.resolve("repository-latency.json"), """
                {
                  "schemaVersion": 3,
                  "measurements": [{
                    "operation": "%s",
                    "scope": "SPRING_SERVICE_REPOSITORY",
                    "sampleCount": 100,
                    "sourceSampleCount": 100,
                    "warmupCount": 20,
                    "successCount": 100,
                    "failureCount": 0,
                    "coldOrWarm": "WARM",
                    "p50Ms": 1.0,
                    "p95Ms": 2.0,
                    "standardDeviationMethod": "population",
                    "resultCountMin": 0,
                    "resultCountP50": 1,
                    "resultCountP95": 2,
                    "resultCountMax": 3
                  }]
                }
                """.formatted("file." + "search.owner-first-page"), StandardCharsets.UTF_8);

        assertThatThrownBy(() -> new BenchmarkRawArtifactValidator(directory).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unsupported benchmark operation");
    }

    @Test
    void rawValidatorRejectsPresentationArtifactsBeforeReportGeneration() throws Exception {
        writeCompleteRawArtifacts();
        Files.writeString(directory.resolve("report.md"), "# Benchmark Report\n", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> new BenchmarkRawArtifactValidator(directory).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Presentation artifact exists before report generation");
    }

    private void writeCompleteRawArtifacts() throws Exception {
        Files.writeString(directory.resolve("environment.json"), """
                {
                  "schemaVersion": 3,
                  "benchmarkRunId": "run",
                  "benchmarkProfile": "default",
                  "baselineQuality": "informational",
                  "environmentFingerprint": "fingerprint",
                  "executionEnvironment": {
                    "memoryBytes": 17179869184
                  },
                  "runtime": {
                    "pythonExecutable": "python",
                    "pythonExecutableSource": "FALLBACK",
                    "pythonVersion": "Python 3.11"
                  }
                }
                """, StandardCharsets.UTF_8);

        Files.writeString(directory.resolve("dataset-manifest.json"), """
                {
                  "schemaVersion": 3,
                  "datasetId": "default-10000-20260611-test",
                  "datasetMode": "inline",
                  "configFingerprint": "fingerprint",
                  "recordCount": 10000,
                  "seed": 20260611,
                  "duplicateDistribution": "default",
                  "tableCounts": {},
                  "actualLoadedTableCounts": {},
                  "actualEvidenceTableCounts": {},
                  "sourceRegistrySampleSizes": {}
                }
                """, StandardCharsets.UTF_8);

        Files.writeString(directory.resolve("benchmark-registry.json"), """
                {
                  "schemaVersion": 3,
                  "datasetId": "default-10000-20260611-test",
                  "configFingerprint": "fingerprint",
                  "records": 10000,
                  "seed": 20260611,
                  "duplicateDistribution": "default",
                  "operations": {
                    "duplicate.search.EXACT": {
                      "sourceFileIds": ["00000000-0000-0000-0000-000000000000"],
                      "sampleSize": 1,
                      "evidenceTable": "file_fingerprints"
                    }
                  }
                }
                """, StandardCharsets.UTF_8);

        Files.writeString(directory.resolve("correctness-results.json"), """
                {
                  "schemaVersion": 3,
                  "passed": true,
                  "caseCount": 1,
                  "passedCount": 1,
                  "failedCount": 0,
                  "cases": [{"name": "case", "passed": true}]
                }
                """, StandardCharsets.UTF_8);

        Files.writeString(directory.resolve("setup-timings.json"), """
                {"schemaVersion": 3, "timingsMs": {}}
                """, StandardCharsets.UTF_8);

        Files.writeString(directory.resolve("repository-latency.json"), """
                {
                  "schemaVersion": 3,
                  "measurements": [{
                    "operation": "duplicate.search.EXACT",
                    "scope": "SPRING_SERVICE_REPOSITORY",
                    "sampleCount": 100,
                    "sourceSampleCount": 100,
                    "warmupCount": 20,
                    "successCount": 100,
                    "failureCount": 0,
                    "coldOrWarm": "WARM",
                    "p50Ms": 1.0,
                    "p95Ms": 2.0,
                    "standardDeviationMethod": "population",
                    "resultCountMin": 0,
                    "resultCountP50": 1,
                    "resultCountP95": 2,
                    "resultCountMax": 3
                  }]
                }
                """, StandardCharsets.UTF_8);

        Files.writeString(directory.resolve("component-status.json"), """
                {
                  "schemaVersion": 3,
                  "components": {
                    "serviceRepositoryBenchmark": {"status": "COMPLETED"},
                    "pgbench": {"status": "NOT_REQUESTED"},
                    "pgStatStatements": {
                      "status": "COMPLETED",
                      "extensionAvailable": true,
                      "resetStatus": "COMPLETED",
                      "collectionStatus": "COMPLETED"
                    },
                    "queryPlan": {"status": "COMPLETED", "scope": "PARTIAL"},
                    "resourceUsage": {"status": "COMPLETED", "scope": "JVM_PROCESS"}
                  }
                }
                """, StandardCharsets.UTF_8);

        Files.writeString(directory.resolve("pg-stat-statements.csv"), "query,calls\nselect 1,1\n", StandardCharsets.UTF_8);

        Files.writeString(directory.resolve("jvm-resource-usage.json"), """
                {
                  "schemaVersion": 3,
                  "scope": "JVM_PROCESS",
                  "metrics": {
                    "heapMaxBytes": 1,
                    "heapTotalBytes": 1,
                    "heapFreeBytes": 1,
                    "heapUsedBytes": 1
                  }
                }
                """, StandardCharsets.UTF_8);

        Path queryPlans = directory.resolve("query-plans");
        Files.createDirectories(queryPlans);

        Files.writeString(queryPlans.resolve("duplicate-exact.json"), "[{}]\n", StandardCharsets.UTF_8);
        Files.writeString(queryPlans.resolve("duplicate-groups-exact-summary.json"), "[{}]\n", StandardCharsets.UTF_8);

        Files.writeString(directory.resolve("query-plan-manifest.json"), """
                {
                  "schemaVersion": 3,
                  "captured": [{
                    "operation": "duplicate.search.EXACT",
                    "scope": "DATABASE_DIRECT",
                    "file": "query-plans/duplicate-exact.json",
                    "status": "COMPLETED"
                  }, {
                    "operation": "duplicate.groups.EXACT",
                    "scope": "DATABASE_DIRECT",
                    "file": "query-plans/duplicate-groups-exact-summary.json",
                    "status": "COMPLETED"
                  }],
                  "notCaptured": [{"operation": "duplicate.search.IMAGE_PHASH", "scope": "DATABASE_DIRECT", "status": "NOT_IMPLEMENTED"}]
                }
                """, StandardCharsets.UTF_8);
    }

    private String componentStatus(String pgStatStatements) {
        return """
                {
                  "schemaVersion": 3,
                  "components": {
                    "serviceRepositoryBenchmark": {"status": "COMPLETED"},
                    "pgbench": {"status": "NOT_REQUESTED"},
                %s,
                    "queryPlan": {"status": "COMPLETED", "scope": "PARTIAL"},
                    "resourceUsage": {"status": "COMPLETED", "scope": "JVM_PROCESS"}
                  }
                }
                """.formatted(pgStatStatements);
    }
}

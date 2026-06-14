package com.filemanager.api.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class BenchmarkRawArtifactValidator {
    private static final int SUPPORTED_SCHEMA_VERSION = 3;
    private static final Set<String> ALLOWED_OPERATIONS = Set.of(
            "duplicate.search.EXACT",
            "duplicate.search.IMAGE_PHASH",
            "duplicate.search.IMAGE_EMBEDDING",
            "duplicate.search.AUDIO_FINGERPRINT",
            "duplicate.groups.EXACT");
    private static final Set<String> REMOVED_DIMENSIONS = Set.of(
            "video_embeddings",
            "duplicate_candidates",
            "duplicate_candidate_refreshes",
            "file_grants",
            "folder_grants",
            "processing_jobs",
            "VIDEO_" + "EMBEDDING",
            "candidate-" + "refresh",
            "permission." + "evaluate",
            "processing." + "status",
            "sharing." + "list",
            "folder." + "list",
            "file." + "search",
            "download-" + "control-plane");
    private static final Set<String> VALID_COMPONENT_STATUSES = Set.of(
            "COMPLETED",
            "NOT_REQUESTED",
            "NOT_CONFIGURED",
            "TOOL_UNAVAILABLE",
            "TARGET_UNAVAILABLE",
            "FAILED");
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final Path scaleDir;

    BenchmarkRawArtifactValidator(Path scaleDir) {
        this.scaleDir = scaleDir;
    }

    void validate() {
        JsonNode environment = readObject("environment.json");
        JsonNode correctness = readObject("correctness-results.json");
        JsonNode latency = readObject("repository-latency.json");
        JsonNode componentStatus = readObject("component-status.json");
        JsonNode datasetManifest = readObject("dataset-manifest.json");
        JsonNode registry = readObject("benchmark-registry.json");
        readObject("setup-timings.json");

        validateEnvironment(environment);
        validateDatasetManifest(datasetManifest);
        validateRegistry(registry);
        validateCorrectness(correctness);
        validateLatency(latency);
        validateComponentStatus(componentStatus);

        rejectPresentationArtifact("summary.csv");
        rejectPresentationArtifact("report.md");
        rejectPresentationArtifact("scale-comparison.csv");
        rejectPresentationArtifact("scale-comparison.md");

        rejectPlaceholder("pgbench-summary.txt");
        rejectPlaceholder("pgbench-summary.json");
        rejectPlaceholder("resource-usage.json");
    }

    private void validateDatasetManifest(JsonNode datasetManifest) {
        requireText(datasetManifest, "datasetId");
        requireText(datasetManifest, "datasetMode");
        requireText(datasetManifest, "configFingerprint");
        requireText(datasetManifest, "duplicateDistribution");
        requireInteger(datasetManifest, "recordCount");
        requireNumber(datasetManifest, "seed");
        requireObject(datasetManifest, "tableCounts");
        requireObject(datasetManifest, "actualLoadedTableCounts");
        requireObject(datasetManifest, "actualEvidenceTableCounts");
        requireObject(datasetManifest, "sourceRegistrySampleSizes");
        rejectRemovedDimensions(datasetManifest, "dataset-manifest.json");
    }

    private void validateRegistry(JsonNode registry) {
        requireText(registry, "datasetId");
        requireText(registry, "configFingerprint");
        requireText(registry, "duplicateDistribution");
        requireInteger(registry, "records");
        requireNumber(registry, "seed");
        JsonNode operations = requireObject(registry, "operations");

        operations.fieldNames().forEachRemaining(operationName -> {
            if (!ALLOWED_OPERATIONS.contains(operationName)) {
                throw new IllegalStateException("Unsupported benchmark operation in registry: " + operationName);
            }
        });

        for (JsonNode operation : operations) {
            requireArray(operation, "sourceFileIds");
            requireInteger(operation, "sampleSize");
            requireText(operation, "evidenceTable");
        }

        rejectRemovedDimensions(registry, "benchmark-registry.json");
    }

    private void validateEnvironment(JsonNode environment) {
        requireText(environment, "benchmarkRunId");
        requireText(environment, "benchmarkProfile");
        requireText(environment, "baselineQuality");
        requireText(environment, "environmentFingerprint");
        requireObject(environment, "executionEnvironment");
        JsonNode runtime = requireObject(environment, "runtime");

        requireText(runtime, "pythonExecutable");
        requireText(runtime, "pythonExecutableSource");
        requireText(runtime, "pythonVersion");

        String profile = environment.get("benchmarkProfile").asText();
        if (!Set.of("default", "baseline").contains(profile)) {
            throw new IllegalStateException("Unsupported benchmarkProfile in environment.json: " + profile);
        }

        String expectedQuality = "baseline".equals(profile) ? "stable" : "informational";
        if (!expectedQuality.equals(environment.get("baselineQuality").asText())) {
            throw new IllegalStateException("baselineQuality does not match benchmarkProfile");
        }

        JsonNode memoryBytes = environment.get("executionEnvironment").get("memoryBytes");
        if (memoryBytes != null && !memoryBytes.isNull() && !memoryBytes.isNumber()) {
            throw new IllegalStateException("executionEnvironment.memoryBytes must be numeric or null");
        }
    }

    private void validateCorrectness(JsonNode correctness) {
        requireBoolean(correctness, "passed");
        requireInteger(correctness, "caseCount");
        requireInteger(correctness, "passedCount");
        requireInteger(correctness, "failedCount");
        JsonNode cases = requireArray(correctness, "cases");

        int caseCount = correctness.get("caseCount").asInt();
        int passedCount = correctness.get("passedCount").asInt();
        int failedCount = correctness.get("failedCount").asInt();

        if (caseCount != cases.size() || passedCount + failedCount != caseCount) {
            throw new IllegalStateException("correctness-results.json counts are inconsistent");
        }

        if (!correctness.get("passed").asBoolean()) {
            throw new IllegalStateException("correctness-results.json reports failed correctness");
        }
    }

    private void validateLatency(JsonNode latency) {
        JsonNode measurements = requireArray(latency, "measurements");
        if (measurements.isEmpty()) {
            throw new IllegalStateException("repository-latency.json has no measurements");
        }

        Set<String> identities = new HashSet<>();

        for (JsonNode measurement : measurements) {
            String operation = requireText(measurement, "operation");
            String scope = requireText(measurement, "scope");
            String coldOrWarm = requireText(measurement, "coldOrWarm");

            if (!ALLOWED_OPERATIONS.contains(operation)) {
                throw new IllegalStateException("Unsupported benchmark operation in repository-latency.json: " + operation);
            }

            requireInteger(measurement, "sampleCount");
            requireInteger(measurement, "sourceSampleCount");
            requireInteger(measurement, "successCount");
            requireInteger(measurement, "failureCount");
            requireNumber(measurement, "p50Ms");
            requireNumber(measurement, "p95Ms");
            requireText(measurement, "standardDeviationMethod");
            requireInteger(measurement, "resultCountMin");
            requireInteger(measurement, "resultCountP50");
            requireInteger(measurement, "resultCountP95");
            requireInteger(measurement, "resultCountMax");

            String identity = operation + "\u0000" + scope + "\u0000" + coldOrWarm;
            if (!identities.add(identity)) {
                throw new IllegalStateException("Duplicate measurement identity in repository-latency.json: " + operation);
            }
        }
    }

    private void validateComponentStatus(JsonNode componentStatus) {
        JsonNode components = requireObject(componentStatus, "components");
        for (String component : List.of(
                "serviceRepositoryBenchmark",
                "pgbench",
                "pgStatStatements",
                "queryPlan",
                "resourceUsage")) {
            JsonNode statusNode = requireObject(components, component);
            String status = requireText(statusNode, "status");

            if (!VALID_COMPONENT_STATUSES.contains(status)) {
                throw new IllegalStateException("Invalid component status for " + component + ": " + status);
            }
        }

        validateOptionalArtifact(components, "pgStatStatements", "pg-stat-statements.csv");
        validateOptionalArtifact(components, "queryPlan", "query-plan-manifest.json");
        validateOptionalArtifact(components, "resourceUsage", "jvm-resource-usage.json");
        validatePgStatStatements(components.get("pgStatStatements"));

        if ("COMPLETED".equals(components.get("queryPlan").get("status").asText())) {
            validateQueryPlanManifest();
        }

        if ("COMPLETED".equals(components.get("resourceUsage").get("status").asText())) {
            validateResourceUsage();
        }
    }

    private void validatePgStatStatements(JsonNode statusNode) {
        if (!"COMPLETED".equals(statusNode.get("status").asText())) {
            return;
        }

        if (!statusNode.path("extensionAvailable").asBoolean(false)
                || !"COMPLETED".equals(statusNode.path("resetStatus").asText())
                || !"COMPLETED".equals(statusNode.path("collectionStatus").asText())) {
            throw new IllegalStateException("pgStatStatements completed status is inconsistent with phase statuses");
        }
    }

    private void validateOptionalArtifact(JsonNode components, String component, String artifact) {
        String status = components.get(component).get("status").asText();
        Path path = scaleDir.resolve(artifact);

        if ("COMPLETED".equals(status)) {
            requireNonEmpty(artifact);
        } else if (Files.exists(path)) {
            throw new IllegalStateException(component + " did not complete but artifact exists: " + path);
        }
    }

    private void validateQueryPlanManifest() {
        JsonNode manifest = readObject("query-plan-manifest.json");
        Set<Path> manifestFiles = new HashSet<>();

        for (JsonNode captured : requireArray(manifest, "captured")) {
            requireText(captured, "operation");
            validateOperation(requireText(captured, "operation"), "query-plan-manifest.json");
            requireText(captured, "scope");
            String status = requireText(captured, "status");

            if ("COMPLETED".equals(status)) {
                Path plan = scaleDir.resolve(requireText(captured, "file")).normalize();
                requireNonEmpty(plan);
                readJson(plan);
                manifestFiles.add(plan);
            }
        }

        for (JsonNode notCaptured : requireArray(manifest, "notCaptured")) {
            requireText(notCaptured, "operation");
            validateOperation(requireText(notCaptured, "operation"), "query-plan-manifest.json");
            requireText(notCaptured, "scope");
            String status = requireText(notCaptured, "status");

            if (!"NOT_IMPLEMENTED".equals(status)) {
                throw new IllegalStateException("Uncaptured query plans must use NOT_IMPLEMENTED status");
            }

            if (notCaptured.has("file")) {
                throw new IllegalStateException("NOT_IMPLEMENTED query plans must not declare a file");
            }
        }

        Path queryPlans = scaleDir.resolve("query-plans");
        if (Files.isDirectory(queryPlans)) {
            try (var paths = Files.list(queryPlans)) {
                List<Path> files = paths.filter(Files::isRegularFile).map(Path::normalize).toList();

                if (!manifestFiles.containsAll(files)) {
                    throw new IllegalStateException("query-plan-manifest.json does not match query-plans directory");
                }
            } catch (Exception ex) {
                if (ex instanceof IllegalStateException illegalStateException) {
                    throw illegalStateException;
                }

                throw new IllegalStateException("Failed to validate query plan directory", ex);
            }
        }
    }

    private void validateResourceUsage() {
        JsonNode usage = readObject("jvm-resource-usage.json");
        if (!"JVM_PROCESS".equals(requireText(usage, "scope"))) {
            throw new IllegalStateException("jvm-resource-usage.json must use JVM_PROCESS scope");
        }

        JsonNode metrics = requireObject(usage, "metrics");

        for (String metric : List.of("heapMaxBytes", "heapTotalBytes", "heapFreeBytes", "heapUsedBytes")) {
            requireNullableNumber(metrics, metric);
        }
    }

    private JsonNode readObject(String fileName) {
        Path path = scaleDir.resolve(fileName);
        JsonNode node = readJson(path);

        if (!node.isObject() || node.isEmpty()) {
            throw new IllegalStateException("Expected non-empty JSON object: " + path);
        }

        if (node.path("schemaVersion").asInt(-1) != SUPPORTED_SCHEMA_VERSION) {
            throw new IllegalStateException("Unsupported schemaVersion in " + path);
        }

        return node;
    }

    private JsonNode readJson(Path path) {
        requireNonEmpty(path);

        try {
            return objectMapper.readTree(path.toFile());
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid JSON artifact: " + path, ex);
        }
    }

    private String requireText(JsonNode node, String field) {
        JsonNode value = node.get(field);

        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new IllegalStateException("Required text field is missing: " + field);
        }

        return value.asText();
    }

    private void requireBoolean(JsonNode node, String field) {
        JsonNode value = node.get(field);

        if (value == null || !value.isBoolean()) {
            throw new IllegalStateException("Required boolean field is missing: " + field);
        }
    }

    private void requireInteger(JsonNode node, String field) {
        JsonNode value = node.get(field);

        if (value == null || !value.canConvertToInt()) {
            throw new IllegalStateException("Required integer field is missing: " + field);
        }
    }

    private void requireNumber(JsonNode node, String field) {
        JsonNode value = node.get(field);

        if (value == null || !value.isNumber()) {
            throw new IllegalStateException("Required numeric field is missing: " + field);
        }
    }

    private void requireNullableNumber(JsonNode node, String field) {
        JsonNode value = node.get(field);

        if (value == null || (!value.isNull() && !value.isNumber())) {
            throw new IllegalStateException("Required nullable numeric field is missing: " + field);
        }
    }

    private JsonNode requireObject(JsonNode node, String field) {
        JsonNode value = node.get(field);

        if (value == null || !value.isObject()) {
            throw new IllegalStateException("Required object field is missing: " + field);
        }

        return value;
    }

    private JsonNode requireArray(JsonNode node, String field) {
        JsonNode value = node.get(field);

        if (value == null || !value.isArray()) {
            throw new IllegalStateException("Required array field is missing: " + field);
        }

        return value;
    }

    private void requireNonEmpty(String fileName) {
        requireNonEmpty(scaleDir.resolve(fileName));
    }

    private void requireNonEmpty(Path path) {
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("Required benchmark artifact is missing: " + path);
        }

        try {
            if (Files.size(path) == 0) {
                throw new IllegalStateException("Required benchmark artifact is empty: " + path);
            }
        } catch (Exception ex) {
            if (ex instanceof IllegalStateException illegalStateException) {
                throw illegalStateException;
            }

            throw new IllegalStateException("Failed to validate benchmark artifact: " + path, ex);
        }
    }

    private void rejectPresentationArtifact(String fileName) {
        Path path = scaleDir.resolve(fileName);

        if (Files.exists(path)) {
            throw new IllegalStateException("Presentation artifact exists before report generation: " + path);
        }
    }

    private void rejectPlaceholder(String fileName) {
        Path path = scaleDir.resolve(fileName);

        if (Files.exists(path)) {
            throw new IllegalStateException("Unexpected placeholder benchmark artifact exists: " + path);
        }
    }

    private void validateOperation(String operation, String artifact) {
        if (!ALLOWED_OPERATIONS.contains(operation)) {
            throw new IllegalStateException("Unsupported benchmark operation in " + artifact + ": " + operation);
        }
    }

    private void rejectRemovedDimensions(JsonNode node, String artifact) {
        String content = node.toString();

        for (String removed : REMOVED_DIMENSIONS) {
            if (content.contains(removed)) {
                throw new IllegalStateException("Removed benchmark dimension in " + artifact + ": " + removed);
            }
        }
    }
}

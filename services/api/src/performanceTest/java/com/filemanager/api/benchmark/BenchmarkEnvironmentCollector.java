package com.filemanager.api.benchmark;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;

final class BenchmarkEnvironmentCollector {
    private final JdbcTemplate jdbcTemplate;

    BenchmarkEnvironmentCollector(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    Map<String, Object> collect(BenchmarkRunContext context) {
        BenchmarkOptions options = context.options();
        Map<String, Object> executionEnvironment = executionEnvironment(options);
        Map<String, Object> runtime = runtimeEnvironment(options);
        Map<String, Object> manifest = new LinkedHashMap<>();

        manifest.put("schemaVersion", BenchmarkSupport.SCHEMA_VERSION);
        manifest.put("benchmarkRunId", context.runId());
        manifest.put("benchmarkSchemaVersion", context.schemaVersion());
        manifest.put("gitCommitSha", context.gitCommitSha());
        manifest.put("dateTime", OffsetDateTime.now(ZoneOffset.UTC).toString());
        manifest.put("benchmarkProfile", options.benchmarkProfile());
        manifest.put("baselineQuality", BenchmarkProfileSupport.baselineQuality(options.benchmarkProfile()));
        manifest.put("benchmarkConfiguration", options.resolvedConfiguration());
        manifest.put("environmentFingerprint", BenchmarkProfileSupport.environmentFingerprint(
                executionEnvironment,
                runtime));
        manifest.put("executionEnvironment", executionEnvironment);
        manifest.put("runtime", runtime);
        manifest.put("datasetScale", context.scale());
        manifest.put("recordCount", context.recordCount());
        manifest.put("datasetSeed", options.seed());
        manifest.put("duplicateDistribution", options.duplicateDistribution());
        manifest.put("warmupIterations", options.warmupIterations());
        manifest.put("measuredIterations", options.measuredIterations());
        manifest.put("concurrency", options.concurrency());
        manifest.put("instrumentationMode", options.instrumentationMode());

        return manifest;
    }

    private Map<String, Object> executionEnvironment(BenchmarkOptions options) {
        Map<String, Object> environment = new LinkedHashMap<>();

        environment.put("operatingSystem", System.getProperty("os.name") + " " + System.getProperty("os.version"));
        environment.put("kernel", System.getProperty("os.version"));
        environment.put("architecture", System.getProperty("os.arch"));
        environment.put("hostName", BenchmarkSupport.commandOutput("hostname"));
        environment.put("cpuModel", options.baselineMetadata().cpu().isBlank() ? null : options.baselineMetadata().cpu());
        environment.put("logicalProcessors", Runtime.getRuntime().availableProcessors());
        environment.put("memoryBytes", memoryBytes(options));
        environment.put(
                "memoryDescription",
                options.baselineMetadata().memory().isBlank() ? null : options.baselineMetadata().memory());
        environment.put("storage", options.baselineMetadata().storage().isBlank() ? null : options.baselineMetadata().storage());
        environment.put("containerized", Files.exists(Path.of("/.dockerenv")));
        environment.put("dockerVersion", BenchmarkSupport.commandOutput("docker", "--version"));
        environment.put("containerResourceLimits", containerResourceLimits(options));
        environment.put("continuousIntegration", System.getenv("CI") != null);
        environment.put("continuousIntegrationProvider", continuousIntegrationProvider());

        return environment;
    }

    private Map<String, Object> runtimeEnvironment(BenchmarkOptions options) {
        Map<String, Object> runtime = new LinkedHashMap<>();

        runtime.put("javaVersion", System.getProperty("java.version"));
        runtime.put("jvmVendor", System.getProperty("java.vendor"));
        runtime.put("jvmArguments", ManagementFactory.getRuntimeMXBean().getInputArguments());
        runtime.put("gradleVersion", BenchmarkSupport.commandOutput(gradleWrapperCommand(), "--version"));
        runtime.put("pythonExecutable", options.pythonExecutable());
        runtime.put("pythonExecutableSource", options.pythonExecutableSource());
        runtime.put("pythonFallbacksAttempted", options.pythonFallbacksAttempted());
        runtime.put("pythonVersion", BenchmarkSupport.commandOutput(options.pythonExecutable(), "--version"));
        runtime.put("postgresqlVersion", jdbcTemplate.queryForObject("SHOW server_version", String.class));
        runtime.put("pgvectorVersion", jdbcTemplate.queryForObject(
                "SELECT extversion FROM pg_extension WHERE extname = 'vector'",
                String.class));

        return runtime;
    }

    private Long memoryBytes(BenchmarkOptions options) {
        if (!options.baselineMetadata().memory().isBlank()) {
            return BenchmarkProfileSupport.parseMemoryBytes(options.baselineMetadata().memory());
        }

        java.lang.management.OperatingSystemMXBean mxBean = ManagementFactory.getOperatingSystemMXBean();

        if (mxBean instanceof com.sun.management.OperatingSystemMXBean operatingSystemMXBean) {
            return operatingSystemMXBean.getTotalMemorySize();
        }

        return null;
    }

    private Map<String, Object> containerResourceLimits(BenchmarkOptions options) {
        Map<String, Object> limits = new LinkedHashMap<>();
        String configuredLimits = options.baselineMetadata().dockerResourceLimits();

        limits.put("configured", configuredLimits.isBlank() ? null : configuredLimits);
        limits.put("memoryBytes", null);

        return limits;
    }

    private String continuousIntegrationProvider() {
        if (System.getenv("GITHUB_ACTIONS") != null) {
            return "github-actions";
        }

        if (System.getenv("CI") != null) {
            return "unknown";
        }

        return null;
    }

    private String gradleWrapperCommand() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")
                ? "..\\..\\gradlew.bat"
                : "../../gradlew";
    }
}

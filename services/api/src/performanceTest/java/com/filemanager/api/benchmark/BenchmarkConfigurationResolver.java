package com.filemanager.api.benchmark;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.yaml.snakeyaml.Yaml;

final class BenchmarkConfigurationResolver {
    private static final Set<String> DEFAULT_KEYS = Set.of(
            "seed",
            "records",
            "warmup-iterations",
            "measured-iterations",
            "concurrency",
            "instrumentation-mode",
            "benchmark-profile",
            "duplicate-distribution");
    private static final Set<String> PROFILE_KEYS = Set.of("profile", "requiredEnvironmentMetadata");

    BenchmarkOptions resolve() {
        Path configDir = Path.of(System.getProperty("benchmark.config-dir", "../../benchmarks/config"));
        Map<String, Object> defaults = loadDefaults(configDir.resolve("benchmark-defaults.yml"));
        ResolvedValue<String> resolvedProfile = rawValue(
                "benchmark.profile",
                "BENCHMARK_PROFILE",
                defaults.get("benchmark-profile"),
                "default");
        String profile = resolvedProfile.value();

        Map<String, Object> profileConfig = loadProfile(configDir, profile);
        Map<String, ResolvedValue<?>> resolved = new LinkedHashMap<>();

        int records = integerValue("benchmark.records", "BENCHMARK_RECORDS", defaults.get("records"), 10_000, resolved);
        long seed = longValue("benchmark.seed", "BENCHMARK_SEED", defaults.get("seed"), 20_260_611L, resolved);
        int warmup = integerValue(
                "benchmark.warmup-iterations",
                "BENCHMARK_WARMUP_ITERATIONS",
                defaults.get("warmup-iterations"),
                20,
                resolved);
        int measured = integerValue(
                "benchmark.measured-iterations",
                "BENCHMARK_MEASURED_ITERATIONS",
                defaults.get("measured-iterations"),
                100,
                resolved);
        String concurrency = stringValue(
                "benchmark.concurrency",
                "BENCHMARK_CONCURRENCY",
                defaults.get("concurrency"),
                "1,10,25",
                resolved);
        String duplicateDistribution = stringValue(
                "benchmark.duplicate-distribution",
                "BENCHMARK_DUPLICATE_DISTRIBUTION",
                defaults.get("duplicate-distribution"),
                "default",
                resolved);
        String instrumentationMode = stringValue(
                "benchmark.instrumentation-mode",
                "BENCHMARK_INSTRUMENTATION_MODE",
                defaults.get("instrumentation-mode"),
                "metrics",
                resolved);

        resolved.put("benchmarkProfile", new ResolvedValue<>(profile, resolvedProfile.source()));

        BenchmarkProfileSupport.BaselineMetadata baselineMetadata = baselineMetadata(profileConfig, resolved);
        String runId = stringValue("benchmark.run-id", "BENCHMARK_RUN_ID", null, "", resolved);
        String pythonExecutable = stringValue(
                "benchmark.python-executable",
                "BENCHMARK_PYTHON_EXECUTABLE",
                null,
                "python",
                resolved);
        String pythonExecutableSource = System.getProperty("benchmark.python-executable.source", sourceFor(
                "benchmark.python-executable",
                "BENCHMARK_PYTHON_EXECUTABLE",
                null));
        String pythonFallbacksAttempted = System.getProperty("benchmark.python-executable.fallbacks-attempted", "");

        return new BenchmarkOptions(
                records,
                seed,
                warmup,
                measured,
                concurrency,
                duplicateDistribution,
                profile,
                baselineMetadata,
                runId,
                instrumentationMode,
                Path.of(System.getProperty("benchmark.reports-dir", "benchmarks/reports")),
                Path.of(System.getProperty("benchmark.results-dir", "benchmarks/results")),
                pythonExecutable,
                pythonExecutableSource,
                pythonFallbacksAttempted,
                Map.copyOf(resolved));
    }

    private BenchmarkProfileSupport.BaselineMetadata baselineMetadata(
            Map<String, Object> profileConfig,
            Map<String, ResolvedValue<?>> resolved) {
        Map<String, Object> required = mapValue(profileConfig.get("requiredEnvironmentMetadata"));
        String cpu = stringValue("benchmark.baseline.cpu", "BENCHMARK_BASELINE_CPU", required.get("cpu"), "", resolved);
        String memory = stringValue(
                "benchmark.baseline.memory",
                "BENCHMARK_BASELINE_MEMORY",
                required.get("memory"),
                "",
                resolved);
        String storage = stringValue(
                "benchmark.baseline.storage",
                "BENCHMARK_BASELINE_STORAGE",
                required.get("storage"),
                "",
                resolved);
        String dockerResourceLimits = stringValue(
                "benchmark.baseline.docker-resource-limits",
                "BENCHMARK_BASELINE_DOCKER_RESOURCE_LIMITS",
                required.get("dockerResourceLimits"),
                "",
                resolved);

        return new BenchmarkProfileSupport.BaselineMetadata(cpu, memory, storage, dockerResourceLimits);
    }

    private Map<String, Object> loadDefaults(Path path) {
        Map<String, Object> root = loadYaml(path);
        Map<String, Object> benchmark = mapValue(root.get("benchmark"));

        rejectUnknownKeys(path, benchmark, DEFAULT_KEYS);

        return benchmark;
    }

    private Map<String, Object> loadProfile(Path configDir, String profile) {
        BenchmarkProfileSupport.validateProfile(profile);

        Path profilePath = profilePath(configDir, profile);
        Map<String, Object> values = loadYaml(profilePath);

        rejectUnknownKeys(profilePath, values, PROFILE_KEYS);

        Object profileName = values.get("profile");
        if (!Objects.equals(profile, profileName)) {
            throw new IllegalArgumentException("Profile file " + profilePath + " must declare profile: " + profile);
        }

        return values;
    }

    private Path profilePath(Path configDir, String profile) {
        String explicit = System.getProperty("benchmark.profile-file");

        if (explicit == null || explicit.isBlank()) {
            explicit = System.getenv("BENCHMARK_PROFILE_FILE");
        }

        if (BenchmarkProfileSupport.BASELINE.equals(profile)) {
            if (explicit == null || explicit.isBlank()) {
                throw new IllegalArgumentException("benchmark.profile=baseline requires benchmark.profile-file");
            }

            return Path.of(explicit);
        }

        return explicit == null || explicit.isBlank() ? configDir.resolve("profiles/default.yml") : Path.of(explicit);
    }

    private Map<String, Object> loadYaml(Path path) {
        try {
            if (!Files.isRegularFile(path)) {
                throw new IllegalArgumentException("Benchmark configuration file is missing: " + path);
            }

            Object loaded = new Yaml().load(Files.readString(path));

            return mapValue(loaded);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read benchmark configuration: " + path, ex);
        }
    }

    private static void rejectUnknownKeys(Path path, Map<String, Object> values, Set<String> allowedKeys) {
        for (String key : values.keySet()) {
            if (!allowedKeys.contains(key)) {
                throw new IllegalArgumentException("Unknown benchmark configuration key in " + path + ": " + key);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapValue(Object value) {
        if (value == null) {
            return Map.of();
        }

        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }

        throw new IllegalArgumentException("Expected mapping in benchmark configuration");
    }

    private static int integerValue(
            String property,
            String environment,
            Object defaultValue,
            int fallback,
            Map<String, ResolvedValue<?>> resolved) {
        ResolvedValue<String> value = rawValue(property, environment, defaultValue, Integer.toString(fallback));
        int parsed = Integer.parseInt(value.value());

        resolved.put(propertyName(property), new ResolvedValue<>(parsed, value.source()));

        return parsed;
    }

    private static long longValue(
            String property,
            String environment,
            Object defaultValue,
            long fallback,
            Map<String, ResolvedValue<?>> resolved) {
        ResolvedValue<String> value = rawValue(property, environment, defaultValue, Long.toString(fallback));
        long parsed = Long.parseLong(value.value());

        resolved.put(propertyName(property), new ResolvedValue<>(parsed, value.source()));

        return parsed;
    }

    private static String stringValue(
            String property,
            String environment,
            Object defaultValue,
            String fallback,
            Map<String, ResolvedValue<?>> resolved) {
        ResolvedValue<String> value = rawValue(property, environment, defaultValue, fallback);

        resolved.put(propertyName(property), value);

        return value.value();
    }

    private static ResolvedValue<String> rawValue(
            String property,
            String environment,
            Object defaultValue,
            String fallback) {
        String systemValue = System.getProperty(property);
        if (systemValue != null && !systemValue.isBlank()) {
            return new ResolvedValue<>(systemValue, sourceOverride(property, "GRADLE_PROPERTY"));
        }

        String environmentValue = System.getenv(environment);
        if (environmentValue != null && !environmentValue.isBlank()) {
            return new ResolvedValue<>(environmentValue, "ENVIRONMENT");
        }

        if (defaultValue != null && !defaultValue.toString().isBlank()) {
            return new ResolvedValue<>(defaultValue.toString(), "YAML");
        }

        return new ResolvedValue<>(fallback, "FALLBACK");
    }

    private static String sourceFor(String property, String environment, Object defaultValue) {
        return rawValue(property, environment, defaultValue, "").source();
    }

    private static String sourceOverride(String property, String fallback) {
        return System.getProperty(property + ".source", fallback);
    }

    private static String propertyName(String property) {
        return switch (property) {
            case "benchmark.records" -> "recordCount";
            case "benchmark.seed" -> "seed";
            case "benchmark.warmup-iterations" -> "warmupIterations";
            case "benchmark.measured-iterations" -> "measuredIterations";
            case "benchmark.duplicate-distribution" -> "duplicateDistribution";
            case "benchmark.instrumentation-mode" -> "instrumentationMode";
            case "benchmark.run-id" -> "runId";
            case "benchmark.python-executable" -> "pythonExecutable";
            case "benchmark.baseline.cpu" -> "baselineCpu";
            case "benchmark.baseline.memory" -> "baselineMemory";
            case "benchmark.baseline.storage" -> "baselineStorage";
            case "benchmark.baseline.docker-resource-limits" -> "baselineDockerResourceLimits";
            default -> property.replace("benchmark.", "");
        };
    }

    record ResolvedValue<T>(T value, String source) {
    }
}

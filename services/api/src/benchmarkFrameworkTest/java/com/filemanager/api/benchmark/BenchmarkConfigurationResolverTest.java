package com.filemanager.api.benchmark;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BenchmarkConfigurationResolverTest {
    @TempDir
    private Path directory;

    @AfterEach
    void clearProperties() {
        for (String property : new String[] {
                "benchmark.config-dir",
                "benchmark.records",
                "benchmark.records.source",
                "benchmark.profile",
                "benchmark.profile-file",
                "benchmark.run-id",
                "benchmark.python-executable",
                "benchmark.python-executable.source",
                "benchmark.python-executable.fallbacks-attempted",
                "benchmark.baseline.cpu",
                "benchmark.baseline.memory",
                "benchmark.baseline.storage",
                "benchmark.baseline.docker-resource-limits"
        }) {
            System.clearProperty(property);
        }
    }

    @Test
    void loadsYamlDefaults() throws Exception {
        writeDefaultConfig(10_000);
        writeDefaultProfile();
        System.setProperty("benchmark.config-dir", directory.toString());
        System.setProperty("benchmark.run-id", "run");

        BenchmarkOptions options = new BenchmarkConfigurationResolver().resolve();

        assertThat(options.records()).isEqualTo(10_000);
        assertThat(options.benchmarkProfile()).isEqualTo("default");
        assertThat(options.resolvedConfiguration().get("recordCount").source()).isEqualTo("YAML");
    }

    @Test
    void systemPropertyOverridesYamlDefault() throws Exception {
        writeDefaultConfig(10_000);
        writeDefaultProfile();
        System.setProperty("benchmark.config-dir", directory.toString());
        System.setProperty("benchmark.records", "12345");
        System.setProperty("benchmark.records.source", "GRADLE_PROPERTY");
        System.setProperty("benchmark.run-id", "run");

        BenchmarkOptions options = new BenchmarkConfigurationResolver().resolve();

        assertThat(options.records()).isEqualTo(12_345);
        assertThat(options.resolvedConfiguration().get("recordCount").source()).isEqualTo("GRADLE_PROPERTY");
    }

    @Test
    void removedThresholdFileIsNotRequired() throws Exception {
        writeDefaultConfig(10_000);
        writeDefaultProfile();
        System.setProperty("benchmark.config-dir", directory.toString());
        System.setProperty("benchmark.run-id", "run");

        BenchmarkOptions options = new BenchmarkConfigurationResolver().resolve();

        assertThat(options.records()).isEqualTo(10_000);
        assertThat(directory.resolve("benchmark-thresholds.yml")).doesNotExist();
    }

    @Test
    void defaultRecordCountIsConsistent() throws Exception {
        writeDefaultConfig(10_000);
        writeDefaultProfile();
        System.setProperty("benchmark.config-dir", directory.toString());
        System.setProperty("benchmark.run-id", "run");

        assertThat(new BenchmarkConfigurationResolver().resolve().records()).isEqualTo(10_000);
    }

    @Test
    void rejectsUnknownDefaultKeys() throws Exception {
        Files.writeString(directory.resolve("benchmark-defaults.yml"), """
                benchmark:
                  records: 10000
                  unexpected: true
                """, StandardCharsets.UTF_8);
        writeDefaultProfile();
        System.setProperty("benchmark.config-dir", directory.toString());

        assertThatThrownBy(() -> new BenchmarkConfigurationResolver().resolve())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown benchmark configuration key");
    }

    @Test
    void baselineRequiresExplicitProfileFile() throws Exception {
        writeDefaultConfig(10_000);
        writeDefaultProfile();
        System.setProperty("benchmark.config-dir", directory.toString());
        System.setProperty("benchmark.profile", "baseline");

        assertThatThrownBy(() -> new BenchmarkConfigurationResolver().resolve())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("benchmark.profile-file");
    }

    @Test
    void rejectsUnknownProfileKeys() throws Exception {
        writeDefaultConfig(10_000);
        Path profiles = directory.resolve("profiles");
        Files.createDirectories(profiles);
        Files.writeString(profiles.resolve("default.yml"), """
                profile: default
                unexpected: true
                """, StandardCharsets.UTF_8);
        System.setProperty("benchmark.config-dir", directory.toString());

        assertThatThrownBy(() -> new BenchmarkConfigurationResolver().resolve())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown benchmark configuration key");
    }

    @Test
    void loadsMinimalDefaultProfile() throws Exception {
        writeDefaultConfig(10_000);
        writeDefaultProfile();
        System.setProperty("benchmark.config-dir", directory.toString());
        System.setProperty("benchmark.run-id", "run");

        BenchmarkOptions options = new BenchmarkConfigurationResolver().resolve();

        assertThat(options.benchmarkProfile()).isEqualTo("default");
        assertThat(BenchmarkProfileSupport.baselineQuality(options.benchmarkProfile())).isEqualTo("informational");
    }

    @Test
    void loadsMinimalBaselineProfileWithRequiredMetadata() throws Exception {
        writeDefaultConfig(10_000);
        Path baselineProfile = writeBaselineProfile();
        System.setProperty("benchmark.config-dir", directory.toString());
        System.setProperty("benchmark.profile", "baseline");
        System.setProperty("benchmark.profile-file", baselineProfile.toString());
        System.setProperty("benchmark.baseline.cpu", "test-cpu");
        System.setProperty("benchmark.baseline.memory", "16g");
        System.setProperty("benchmark.baseline.storage", "test-ssd");
        System.setProperty("benchmark.baseline.docker-resource-limits", "cpus=4,memory=16g");
        System.setProperty("benchmark.run-id", "run");

        BenchmarkOptions options = new BenchmarkConfigurationResolver().resolve();
        options.validate();

        assertThat(options.benchmarkProfile()).isEqualTo("baseline");
        assertThat(BenchmarkProfileSupport.baselineQuality(options.benchmarkProfile())).isEqualTo("stable");
    }

    @Test
    void rejectsRemovedProfilePolicyKeys() throws Exception {
        for (String key : new String[] {"baselineQuality", "timeouts", "failOn", "regressionGates"}) {
            writeDefaultConfig(10_000);
            Path profiles = directory.resolve("profiles");
            Files.createDirectories(profiles);
            Files.writeString(profiles.resolve("default.yml"), """
                    profile: default
                    %s: {}
                    """.formatted(key), StandardCharsets.UTF_8);
            System.setProperty("benchmark.config-dir", directory.toString());

            assertThatThrownBy(() -> new BenchmarkConfigurationResolver().resolve())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(key);

            Files.delete(profiles.resolve("default.yml"));
            Files.delete(directory.resolve("benchmark-defaults.yml"));
        }
    }

    private void writeDefaultConfig(int records) throws Exception {
        Files.writeString(directory.resolve("benchmark-defaults.yml"), """
                benchmark:
                  seed: 20260611
                  records: %d
                  warmup-iterations: 20
                  measured-iterations: 100
                  concurrency: "1,10,25"
                  instrumentation-mode: metrics
                  benchmark-profile: default
                  duplicate-distribution: default
                """.formatted(records), StandardCharsets.UTF_8);
    }

    private void writeDefaultProfile() throws Exception {
        Path profiles = directory.resolve("profiles");
        Files.createDirectories(profiles);
        Files.writeString(profiles.resolve("default.yml"), """
                profile: default
                """, StandardCharsets.UTF_8);
    }

    private Path writeBaselineProfile() throws Exception {
        Path profiles = directory.resolve("profiles");
        Files.createDirectories(profiles);
        Path baseline = profiles.resolve("baseline.yml");
        Files.writeString(baseline, """
                profile: baseline
                requiredEnvironmentMetadata:
                  cpu: ""
                  memory: ""
                  storage: ""
                  dockerResourceLimits: ""
                """, StandardCharsets.UTF_8);
        return baseline;
    }
}

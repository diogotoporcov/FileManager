package com.filemanager.api.benchmark;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BenchmarkProfileSupportTest {
    @Test
    void acceptsDefaultAndBaselineProfiles() {
        assertThat(BenchmarkProfileSupport.validateProfile("default")).isEqualTo("default");
        assertThat(BenchmarkProfileSupport.validateProfile("baseline")).isEqualTo("baseline");
    }

    @Test
    void rejectsObsoleteAndUnknownProfiles() {
        for (String profile : new String[] {"local-development", "local-stable", "github-hosted", "unknown"}) {
            assertThatThrownBy(() -> BenchmarkProfileSupport.validateProfile(profile))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("benchmark.profile must be default or baseline");
        }
    }

    @Test
    void baselineRequiresEnvironmentMetadata() {
        BenchmarkProfileSupport.BaselineMetadata metadata =
                new BenchmarkProfileSupport.BaselineMetadata("cpu", "", "ssd", "cpus=4,memory=8g");

        assertThatThrownBy(() -> BenchmarkProfileSupport.validateBaselineMetadata("baseline", metadata))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("memory");
    }

    @Test
    void baselineRejectsInvalidMemoryMetadata() {
        BenchmarkProfileSupport.BaselineMetadata metadata =
                new BenchmarkProfileSupport.BaselineMetadata("cpu", "sixteen", "ssd", "cpus=4,memory=8g");

        assertThatThrownBy(() -> BenchmarkProfileSupport.validateBaselineMetadata("baseline", metadata))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid memory metadata value");
    }

    @Test
    void parsesMemoryMetadataToBytes() {
        assertThat(BenchmarkProfileSupport.parseMemoryBytes("16g")).isEqualTo(17_179_869_184L);
        assertThat(BenchmarkProfileSupport.parseMemoryBytes("512m")).isEqualTo(536_870_912L);
    }

    @Test
    void environmentFingerprintIsDeterministicAndIgnoresVolatileFields() {
        Map<String, Object> environment = environment("cpu-a", 12, 16_000_000_000L);
        environment.put("hostName", "first-host");
        environment.put("dateTime", "first");

        Map<String, Object> runtime = runtime("25", "18.4");

        String first = BenchmarkProfileSupport.environmentFingerprint(environment, runtime);

        environment.put("hostName", "second-host");
        environment.put("dateTime", "second");

        assertThat(BenchmarkProfileSupport.environmentFingerprint(environment, runtime)).isEqualTo(first);
    }

    @Test
    void environmentFingerprintIsIndependentOfMapInsertionOrder() {
        Map<String, Object> first = environment("cpu-a", 12, 16_000_000_000L);
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("containerResourceLimits", Map.of("configured", "cpus=4,memory=8g"));
        second.put("storage", "ssd");
        second.put("memoryBytes", 16_000_000_000L);
        second.put("logicalProcessors", 12);
        second.put("cpuModel", "cpu-a");
        second.put("architecture", "amd64");
        second.put("operatingSystem", "Windows 10");

        assertThat(BenchmarkProfileSupport.environmentFingerprint(second, runtime("25", "18.4")))
                .isEqualTo(BenchmarkProfileSupport.environmentFingerprint(first, runtime("25", "18.4")));
    }

    @Test
    void environmentFingerprintChangesForMaterialEnvironmentChanges() {
        String first = BenchmarkProfileSupport.environmentFingerprint(
                environment("cpu-a", 12, 16_000_000_000L),
                runtime("25", "18.4"));

        assertThat(BenchmarkProfileSupport.environmentFingerprint(environment("cpu-b", 12, 16_000_000_000L), runtime("25", "18.4")))
                .isNotEqualTo(first);
        assertThat(BenchmarkProfileSupport.environmentFingerprint(environment("cpu-a", 12, 32_000_000_000L), runtime("25", "18.4")))
                .isNotEqualTo(first);
        assertThat(BenchmarkProfileSupport.environmentFingerprint(environment("cpu-a", 12, 16_000_000_000L), runtime("26", "18.4")))
                .isNotEqualTo(first);
        assertThat(BenchmarkProfileSupport.environmentFingerprint(environment("cpu-a", 12, 16_000_000_000L), runtime("25", "19.0")))
                .isNotEqualTo(first);
    }

    private static Map<String, Object> environment(String cpuModel, int logicalProcessors, long memoryBytes) {
        Map<String, Object> environment = new LinkedHashMap<>();
        environment.put("operatingSystem", "Windows 10");
        environment.put("architecture", "amd64");
        environment.put("cpuModel", cpuModel);
        environment.put("logicalProcessors", logicalProcessors);
        environment.put("memoryBytes", memoryBytes);
        environment.put("storage", "ssd");
        environment.put("containerResourceLimits", Map.of("configured", "cpus=4,memory=8g"));

        return environment;
    }

    private static Map<String, Object> runtime(String javaVersion, String postgresqlVersion) {
        Map<String, Object> runtime = new LinkedHashMap<>();

        runtime.put("javaVersion", javaVersion);
        runtime.put("postgresqlVersion", postgresqlVersion);
        runtime.put("pgvectorVersion", "0.8.2");

        return runtime;
    }
}

package com.filemanager.api.benchmark;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

final class BenchmarkProfileSupport {
    static final String DEFAULT = "default";
    static final String BASELINE = "baseline";

    private BenchmarkProfileSupport() {
    }

    static String validateProfile(String profile) {
        if (!Set.of(DEFAULT, BASELINE).contains(profile)) {
            throw new IllegalArgumentException("benchmark.profile must be default or baseline");
        }

        return profile;
    }

    static String baselineQuality(String profile) {
        return switch (validateProfile(profile)) {
            case BASELINE -> "stable";
            default -> "informational";
        };
    }

    static void validateBaselineMetadata(String profile, BaselineMetadata metadata) {
        if (!BASELINE.equals(profile)) {
            return;
        }

        List<String> missing = metadata.toMap().entrySet().stream()
                .filter(entry -> entry.getValue() == null || entry.getValue().isBlank())
                .map(Map.Entry::getKey)
                .toList();

        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(
                    "benchmark.profile=baseline requires metadata: " + String.join(", ", missing));
        }

        parseMemoryBytes(metadata.memory());
    }

    static String environmentFingerprint(Map<String, Object> executionEnvironment, Map<String, Object> runtime) {
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("operatingSystem", executionEnvironment.get("operatingSystem"));
        inputs.put("architecture", executionEnvironment.get("architecture"));
        inputs.put("cpuModel", executionEnvironment.get("cpuModel"));
        inputs.put("logicalProcessors", executionEnvironment.get("logicalProcessors"));
        inputs.put("memoryBytes", executionEnvironment.get("memoryBytes"));
        inputs.put("storage", executionEnvironment.get("storage"));
        inputs.put("containerResourceLimits", executionEnvironment.get("containerResourceLimits"));
        inputs.put("javaVersion", runtime.get("javaVersion"));
        inputs.put("postgresqlVersion", runtime.get("postgresqlVersion"));
        inputs.put("pgvectorVersion", runtime.get("pgvectorVersion"));
        return sha256(canonical(inputs));
    }

    static Long parseMemoryBytes(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim().toLowerCase();
        long multiplier = 1L;

        if (normalized.endsWith("gib")) {
            multiplier = 1024L * 1024L * 1024L;
            normalized = normalized.substring(0, normalized.length() - 3).trim();
        } else if (normalized.endsWith("gb")) {
            multiplier = 1_000_000_000L;
            normalized = normalized.substring(0, normalized.length() - 2).trim();
        } else if (normalized.endsWith("g")) {
            multiplier = 1024L * 1024L * 1024L;
            normalized = normalized.substring(0, normalized.length() - 1).trim();
        } else if (normalized.endsWith("mib")) {
            multiplier = 1024L * 1024L;
            normalized = normalized.substring(0, normalized.length() - 3).trim();
        } else if (normalized.endsWith("mb")) {
            multiplier = 1_000_000L;
            normalized = normalized.substring(0, normalized.length() - 2).trim();
        } else if (normalized.endsWith("m")) {
            multiplier = 1024L * 1024L;
            normalized = normalized.substring(0, normalized.length() - 1).trim();
        }

        try {
            return Math.multiplyExact(Long.parseLong(normalized), multiplier);
        } catch (ArithmeticException | NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid memory metadata value: " + value, ex);
        }
    }

    private static String canonical(Object value) {
        if (value instanceof Map<?, ?> map) {
            StringBuilder builder = new StringBuilder("{");
            boolean first = true;

            List<? extends Map.Entry<?, ?>> entries = map.entrySet().stream()
                    .sorted(Comparator.comparing(left -> String.valueOf(left.getKey())))
                    .toList();

            for (Map.Entry<?, ?> entry : entries) {
                if (!first) {
                    builder.append(',');
                }

                builder.append(entry.getKey()).append('=').append(canonical(entry.getValue()));
                first = false;
            }

            return builder.append('}').toString();
        }

        return Objects.toString(value, "null");
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 digest is unavailable", ex);
        }
    }

    record BaselineMetadata(String cpu, String memory, String storage, String dockerResourceLimits) {
        Map<String, String> toMap() {
            Map<String, String> values = new LinkedHashMap<>();
            values.put("cpu", cpu);
            values.put("memory", memory);
            values.put("storage", storage);
            values.put("dockerResourceLimits", dockerResourceLimits);

            return values;
        }
    }
}

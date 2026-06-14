package com.filemanager.api.benchmark;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

final class BenchmarkSupport {
    static final int SCHEMA_VERSION = 3;

    private BenchmarkSupport() {
    }

    static double nanosToMillis(long nanos) {
        return nanos / 1_000_000.0;
    }

    static String commandOutput(String... command) {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);

            if (!finished || process.exitValue() != 0) {
                return null;
            }

            return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (Exception ex) {
            return null;
        }
    }

    static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 digest is unavailable", ex);
        }
    }

    static String datasetFingerprint(int records, long seed, String duplicateDistribution) {
        return sha256(SCHEMA_VERSION + ":" + records + ":" + seed + ":" + duplicateDistribution);
    }

    static String datasetId(int records, long seed, String duplicateDistribution) {
        return duplicateDistribution + "-" + records + "-" + seed + "-"
                + datasetFingerprint(records, seed, duplicateDistribution).substring(0, 12);
    }

    static UUID id(long seed, String label) {
        return UUID.nameUUIDFromBytes((seed + ":" + label).getBytes(StandardCharsets.UTF_8));
    }
}

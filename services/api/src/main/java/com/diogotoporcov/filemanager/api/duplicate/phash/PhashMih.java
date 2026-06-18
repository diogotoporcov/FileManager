package com.diogotoporcov.filemanager.api.duplicate.phash;

import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

public final class PhashMih {
    public static final int CHUNK_COUNT = 3;
    public static final int SUPPORTED_THRESHOLD = 10;
    public static final int PROBE_RADIUS = 3;

    private static final int[] CHUNK_LENGTHS = {22, 21, 21};
    private static final int[] CHUNK_SHIFTS = {42, 21, 0};
    private static final long[] CHUNK_MASKS = {
            (1L << CHUNK_LENGTHS[0]) - 1,
            (1L << CHUNK_LENGTHS[1]) - 1,
            (1L << CHUNK_LENGTHS[2]) - 1
    };

    private PhashMih() {
    }

    public static String normalize(String phash) {
        if (phash == null || !phash.matches("^[a-fA-F0-9]{16}$")) {
            throw new IllegalArgumentException("Invalid pHash format");
        }

        return phash.toLowerCase(Locale.ROOT);
    }

    public static long unsignedBits(String normalizedPhash) {
        return Long.parseUnsignedLong(normalize(normalizedPhash), 16);
    }

    public static List<Chunk> chunks(String phash) {
        long bits = unsignedBits(phash);
        List<Chunk> chunks = new ArrayList<>(CHUNK_COUNT);
        for (int index = 0; index < CHUNK_COUNT; index++) {
            int value = (int) ((bits >>> CHUNK_SHIFTS[index]) & CHUNK_MASKS[index]);
            chunks.add(new Chunk(index, CHUNK_LENGTHS[index], value));
        }

        return chunks;
    }

    public static ProbeKeys probeKeys(String phash, int threshold) {
        validateSupportedThreshold(threshold);
        List<Integer> chunkIndexes = new ArrayList<>(4918);
        List<Integer> chunkValues = new ArrayList<>(4918);
        for (Chunk chunk : chunks(phash)) {
            for (int value : neighborValues(chunk.value(), chunk.length(), PROBE_RADIUS)) {
                chunkIndexes.add(chunk.index());
                chunkValues.add(value);
            }
        }

        return new ProbeKeys(chunkIndexes, chunkValues);
    }

    public static List<Integer> neighborValues(int sourceValue, int bitLength, int radius) {
        if (bitLength < 1 || bitLength > 30) {
            throw new IllegalArgumentException("bitLength must be between 1 and 30");
        }
        if (radius < 0 || radius > bitLength) {
            throw new IllegalArgumentException("radius must be between 0 and bitLength");
        }

        List<Integer> values = new ArrayList<>();
        for (int flips = 0; flips <= radius; flips++) {
            collectNeighbors(sourceValue, bitLength, flips, 0, 0, values);
        }
        return values;
    }

    public static int hammingDistance(String leftPhash, String rightPhash) {
        return Long.bitCount(unsignedBits(leftPhash) ^ unsignedBits(rightPhash));
    }

    public static void validateSupportedThreshold(int threshold) {
        if (threshold != SUPPORTED_THRESHOLD) {
            throw new IllegalStateException(
                    "Configured image pHash threshold " + threshold
                            + " is not supported by the exact 22/21/21 MIH query; expected "
                            + SUPPORTED_THRESHOLD);
        }
    }

    public static String hex(long bits) {
        return HexFormat.of().toHexDigits(bits);
    }

    private static void collectNeighbors(
            int sourceValue,
            int bitLength,
            int remainingFlips,
            int nextBit,
            int mask,
            List<Integer> values) {
        if (remainingFlips == 0) {
            values.add(sourceValue ^ mask);
            return;
        }

        for (int bit = nextBit; bit < bitLength; bit++) {
            collectNeighbors(
                    sourceValue,
                    bitLength,
                    remainingFlips - 1,
                    bit + 1,
                    mask ^ (1 << bit),
                    values);
        }
    }

    public record Chunk(int index, int length, int value) {
    }

    public record ProbeKeys(List<Integer> chunkIndexes, List<Integer> chunkValues) {
    }
}

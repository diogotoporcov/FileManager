package com.filemanager.api.duplicate.phash;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class PhashMihTest {
    @Test
    void chunks_ExtractsCanonicalLayoutForBoundaryValues() {
        assertThat(PhashMih.chunks("0000000000000000"))
                .extracting(PhashMih.Chunk::value)
                .containsExactly(0, 0, 0);
        assertThat(PhashMih.chunks("0000000000000001"))
                .extracting(PhashMih.Chunk::value)
                .containsExactly(0, 0, 1);
        assertThat(PhashMih.chunks("0000000000200000"))
                .extracting(PhashMih.Chunk::value)
                .containsExactly(0, 1, 0);
        assertThat(PhashMih.chunks("0000040000000000"))
                .extracting(PhashMih.Chunk::value)
                .containsExactly(1, 0, 0);
    }

    @Test
    void chunks_HandleLeadingZerosAllOnesAndHighBitSet() {
        assertThat(PhashMih.normalize("00000000000000AF")).isEqualTo("00000000000000af");
        assertThat(PhashMih.chunks("ffffffffffffffff"))
                .extracting(PhashMih.Chunk::value)
                .containsExactly((1 << 22) - 1, (1 << 21) - 1, (1 << 21) - 1);
        assertThat(PhashMih.chunks("8000000000000000"))
                .extracting(PhashMih.Chunk::value)
                .containsExactly(1 << 21, 0, 0);
    }

    @Test
    void neighborValues_AreUniqueWithinRadiusAndHaveExpectedCounts() {
        List<Integer> twentyOneBitNeighbors = PhashMih.neighborValues(0x155555, 21, 3);
        List<Integer> twentyTwoBitNeighbors = PhashMih.neighborValues(0x2aaaaa, 22, 3);

        assertThat(twentyOneBitNeighbors)
                .hasSize(1 + 21 + choose(21, 2) + choose(21, 3))
                .allSatisfy(value -> assertThat(Integer.bitCount(value ^ 0x155555)).isLessThanOrEqualTo(3));
        assertThat(new HashSet<>(twentyOneBitNeighbors)).hasSameSizeAs(twentyOneBitNeighbors);
        assertThat(twentyTwoBitNeighbors)
                .hasSize(1 + 22 + choose(22, 2) + choose(22, 3))
                .allSatisfy(value -> assertThat(Integer.bitCount(value ^ 0x2aaaaa)).isLessThanOrEqualTo(3));
        assertThat(new HashSet<>(twentyTwoBitNeighbors)).hasSameSizeAs(twentyTwoBitNeighbors);
        assertThat(PhashMih.probeKeys("0123456789abcdef", 10).chunkValues()).hasSize(4918);
    }

    @Test
    void probeKeys_AreDeterministicallyOrdered() {
        PhashMih.ProbeKeys first = PhashMih.probeKeys("0123456789abcdef", 10);
        PhashMih.ProbeKeys second = PhashMih.probeKeys("0123456789abcdef", 10);

        assertThat(first.chunkIndexes()).containsExactlyElementsOf(second.chunkIndexes());
        assertThat(first.chunkValues()).containsExactlyElementsOf(second.chunkValues());
    }

    @Test
    void exactMihCandidateSet_EqualsBruteForceForDeterministicHashes() {
        UUID sourceId = UUID.randomUUID();
        String source = "0123456789abcdef";
        Map<UUID, String> hashes = new LinkedHashMap<>();
        hashes.put(sourceId, source);
        hashes.put(UUID.randomUUID(), flip(source, 0));
        hashes.put(UUID.randomUUID(), flip(source, 0, 1, 2));
        hashes.put(UUID.randomUUID(), flip(source, 0, 1, 2, 42));
        hashes.put(UUID.randomUUID(), flip(source, 0, 1, 2, 3, 4, 5, 6, 7, 8));
        hashes.put(UUID.randomUUID(), flip(source, 0, 1, 2, 3, 21, 22, 23, 42, 43, 44));
        hashes.put(UUID.randomUUID(), flip(source, 0, 1, 2, 3, 21, 22, 23, 42, 43, 44, 45));
        hashes.put(UUID.randomUUID(), flip(source, 0, 3, 6, 21, 24, 27, 42, 45, 48, 51));

        Set<UUID> bruteForce = hashes.entrySet().stream()
                .filter(entry -> !entry.getKey().equals(sourceId))
                .filter(entry -> PhashMih.hammingDistance(source, entry.getValue()) <= 10)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        PhashMih.ProbeKeys probes = PhashMih.probeKeys(source, 10);
        Set<UUID> mih = hashes.entrySet().stream()
                .filter(entry -> !entry.getKey().equals(sourceId))
                .filter(entry -> hasMatchingProbe(entry.getValue(), probes))
                .filter(entry -> PhashMih.hammingDistance(source, entry.getValue()) <= 10)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        assertThat(mih).isEqualTo(bruteForce);
    }

    @Test
    void validateSupportedThreshold_RejectsUnsupportedDistance() {
        assertThatThrownBy(() -> PhashMih.validateSupportedThreshold(9))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not supported");
    }

    private static boolean hasMatchingProbe(String phash, PhashMih.ProbeKeys probes) {
        for (PhashMih.Chunk chunk : PhashMih.chunks(phash)) {
            for (int probeIndex = 0; probeIndex < probes.chunkIndexes().size(); probeIndex++) {
                if (probes.chunkIndexes().get(probeIndex) == chunk.index()
                        && probes.chunkValues().get(probeIndex) == chunk.value()) {
                    return true;
                }
            }
        }

        return false;
    }

    private static String flip(String phash, int... bitIndexes) {
        long bits = PhashMih.unsignedBits(phash);
        for (int bitIndex : bitIndexes) {
            bits ^= 1L << bitIndex;
        }

        return PhashMih.hex(bits);
    }

    private static int choose(int size, int count) {
        int result = 1;
        for (int index = 1; index <= count; index++) {
            result = result * (size - index + 1) / index;
        }

        return result;
    }
}

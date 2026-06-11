package com.filemanager.api.duplicate.persistence;

public record AudioDuplicateGroupKeyProjection(
        String fingerprintAlgorithm,
        String fingerprintVersion,
        String fingerprintHash,
        long fileCount
) {
}

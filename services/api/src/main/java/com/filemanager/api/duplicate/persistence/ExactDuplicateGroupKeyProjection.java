package com.filemanager.api.duplicate.persistence;

import com.filemanager.api.processing.domain.result.FileFingerprint.FingerprintAlgorithm;

public record ExactDuplicateGroupKeyProjection(
        FingerprintAlgorithm algorithm,
        String hashValue,
        long fileCount
) {
}
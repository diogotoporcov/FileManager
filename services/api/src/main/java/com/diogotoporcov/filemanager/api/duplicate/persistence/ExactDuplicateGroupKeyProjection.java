package com.diogotoporcov.filemanager.api.duplicate.persistence;

import com.diogotoporcov.filemanager.api.processing.domain.result.FileFingerprint.FingerprintAlgorithm;

public record ExactDuplicateGroupKeyProjection(
        FingerprintAlgorithm algorithm,
        String hashValue,
        long fileCount
) {
}
package com.filemanager.api.duplicate.persistence;

import com.filemanager.api.processing.domain.result.FileFingerprint.FingerprintAlgorithm;
import java.util.UUID;

public record ExactDuplicateCandidateProjection(
        UUID fileId,
        String name,
        String mimeType,
        Long size,
        FingerprintAlgorithm algorithm,
        String hashValue
) {
}
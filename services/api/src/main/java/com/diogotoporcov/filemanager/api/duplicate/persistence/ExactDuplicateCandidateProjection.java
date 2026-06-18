package com.diogotoporcov.filemanager.api.duplicate.persistence;

import com.diogotoporcov.filemanager.api.processing.domain.result.FileFingerprint.FingerprintAlgorithm;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ExactDuplicateCandidateProjection(
        UUID fileId,
        String name,
        String mimeType,
        Long size,
        FingerprintAlgorithm algorithm,
        String hashValue,
        OffsetDateTime createdAt
) {
}

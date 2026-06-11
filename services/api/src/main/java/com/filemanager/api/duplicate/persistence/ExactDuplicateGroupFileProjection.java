package com.filemanager.api.duplicate.persistence;

import com.filemanager.api.processing.domain.result.FileFingerprint.FingerprintAlgorithm;
import java.util.UUID;

public record ExactDuplicateGroupFileProjection(
        FingerprintAlgorithm algorithm,
        String hashValue,
        UUID fileId,
        String name,
        String mimeType,
        Long size
) {
}
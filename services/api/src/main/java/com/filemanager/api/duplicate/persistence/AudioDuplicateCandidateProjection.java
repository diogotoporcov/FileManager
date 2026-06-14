package com.filemanager.api.duplicate.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AudioDuplicateCandidateProjection(
        UUID fileId,
        String name,
        String mimeType,
        Long size,
        String fingerprintAlgorithm,
        String fingerprintVersion,
        String fingerprintHash,
        OffsetDateTime createdAt
) {
}

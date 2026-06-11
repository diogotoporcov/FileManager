package com.filemanager.api.duplicate.persistence;

import java.util.UUID;

public record AudioDuplicateGroupFileProjection(
        String fingerprintAlgorithm,
        String fingerprintVersion,
        String fingerprintHash,
        UUID fileId,
        String name,
        String mimeType,
        Long size
) {
}

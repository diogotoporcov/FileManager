package com.filemanager.api.duplicate.persistence;

import java.util.UUID;

public record DuplicateCandidateFileProjection(
        UUID fileId,
        String name,
        String mimeType,
        Long size,
        Double distance,
        Double score
) {
}

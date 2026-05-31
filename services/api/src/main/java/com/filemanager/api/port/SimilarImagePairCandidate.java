package com.filemanager.api.port;

import java.util.UUID;

public record SimilarImagePairCandidate(
        UUID sourceFileId,
        UUID candidateFileId,
        int distance
) {
}

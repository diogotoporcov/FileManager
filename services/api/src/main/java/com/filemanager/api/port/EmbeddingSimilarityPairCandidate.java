package com.filemanager.api.port;

import java.util.UUID;

public record EmbeddingSimilarityPairCandidate(
        UUID sourceFileId,
        UUID candidateFileId,
        double distance
) {
}

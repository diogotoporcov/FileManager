package com.filemanager.api.port;

import java.util.UUID;

public record EmbeddingSimilarityCandidate(
        UUID fileId,
        double distance
) {
}

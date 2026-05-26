package com.filemanager.api.port;

import java.util.UUID;

public record SimilarImageCandidate(
        UUID fileId,
        int distance
) {
}

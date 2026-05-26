package com.filemanager.api.port;

import java.util.Objects;
import java.util.UUID;

public record SimilarImageSearchRequest(
        UUID sourceFileId,
        UUID ownerUserId,
        UUID ownerOrganizationId,
        String phash,
        int threshold,
        int maxResults
) {
    public SimilarImageSearchRequest {
        Objects.requireNonNull(sourceFileId, "sourceFileId must not be null");
        Objects.requireNonNull(phash, "phash must not be null");
        if ((ownerUserId == null) == (ownerOrganizationId == null)) {
            throw new IllegalArgumentException("exactly one owner scope must be provided");
        }
        if (threshold < 0 || threshold > 64) {
            throw new IllegalArgumentException("threshold must be between 0 and 64");
        }
        if (maxResults < 1) {
            throw new IllegalArgumentException("maxResults must be positive");
        }
    }
}

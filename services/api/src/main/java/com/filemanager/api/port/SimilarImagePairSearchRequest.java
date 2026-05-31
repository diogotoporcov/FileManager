package com.filemanager.api.port;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SimilarImagePairSearchRequest(
        UUID ownerUserId,
        UUID ownerOrganizationId,
        int threshold,
        int maxResults,
        OffsetDateTime cursorCreatedAt,
        UUID cursorFileId
) {
    public SimilarImagePairSearchRequest {
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

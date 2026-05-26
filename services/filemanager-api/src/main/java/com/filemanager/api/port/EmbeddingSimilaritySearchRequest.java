package com.filemanager.api.port;

import java.util.Objects;
import java.util.UUID;

public record EmbeddingSimilaritySearchRequest(
        UUID sourceFileId,
        UUID ownerUserId,
        UUID ownerOrganizationId,
        String modelName,
        String modelVersion,
        double maxCosineDistance,
        int maxResults
) {
    public EmbeddingSimilaritySearchRequest {
        Objects.requireNonNull(sourceFileId, "sourceFileId must not be null");
        Objects.requireNonNull(modelName, "modelName must not be null");
        Objects.requireNonNull(modelVersion, "modelVersion must not be null");
        if ((ownerUserId == null) == (ownerOrganizationId == null)) {
            throw new IllegalArgumentException("exactly one owner scope must be provided");
        }
        if (modelName.isBlank()) {
            throw new IllegalArgumentException("modelName must not be blank");
        }
        if (modelVersion.isBlank()) {
            throw new IllegalArgumentException("modelVersion must not be blank");
        }
        if (maxCosineDistance < 0.0 || maxCosineDistance > 2.0) {
            throw new IllegalArgumentException("maxCosineDistance must be between 0 and 2");
        }
        if (maxResults < 1) {
            throw new IllegalArgumentException("maxResults must be positive");
        }
    }
}

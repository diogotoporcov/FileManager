package com.filemanager.api.duplicate.persistence;

import java.util.UUID;

public interface EmbeddingDuplicateCandidateProjection {
    UUID getFileId();

    String getName();

    String getMimeType();

    Long getSize();

    Double getDistance();
}
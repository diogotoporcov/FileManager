package com.diogotoporcov.filemanager.api.duplicate.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface PhashDuplicateCandidateProjection {
    UUID getFileId();

    String getName();

    String getMimeType();

    Long getSize();

    Integer getDistance();

    OffsetDateTime getCreatedAt();
}

package com.filemanager.api.duplicate.persistence;

import java.util.UUID;

public interface PhashDuplicateCandidateProjection {
    UUID getFileId();

    String getName();

    String getMimeType();

    Long getSize();

    Integer getDistance();
}
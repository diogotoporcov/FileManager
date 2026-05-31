package com.filemanager.api.repository;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface FileListItemProjection {
    UUID getId();
    String getName();
    String getMimeType();
    Long getSize();
    UUID getOwnerUserId();
    UUID getOwnerOrganizationId();
    OffsetDateTime getCreatedAt();
    OffsetDateTime getUpdatedAt();
}

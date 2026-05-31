package com.filemanager.api.repository;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface DuplicateHashGroupProjection {
    String getHashValue();
    OffsetDateTime getOriginalCreatedAt();
    UUID getOriginalFileId();
}

package com.filemanager.api.event;

import lombok.Builder;
import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
public record FileProcessingRequestedEvent(
    UUID eventId,
    String eventType,
    OffsetDateTime occurredAt,
    UUID fileId,
    UUID processingJobId,
    String jobType,
    String storagePath,
    String mimeType,
    long size,
    UUID ownerUserId,
    UUID ownerOrganizationId
) {
}

package com.diogotoporcov.filemanager.api.processing.messaging;

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
    UUID ownerUserId
) {
}

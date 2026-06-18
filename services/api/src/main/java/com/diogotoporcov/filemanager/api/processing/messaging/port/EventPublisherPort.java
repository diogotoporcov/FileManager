package com.diogotoporcov.filemanager.api.processing.messaging.port;

import com.diogotoporcov.filemanager.api.processing.messaging.FileProcessingRequestedEvent;

public interface EventPublisherPort {
    PublishEventResponse publishFileProcessingRequested(FileProcessingRequestedEvent event);
}

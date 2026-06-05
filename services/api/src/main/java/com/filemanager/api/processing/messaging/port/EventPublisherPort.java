package com.filemanager.api.processing.messaging.port;

import com.filemanager.api.processing.messaging.FileProcessingRequestedEvent;

public interface EventPublisherPort {
    PublishEventResponse publishFileProcessingRequested(FileProcessingRequestedEvent event);
}

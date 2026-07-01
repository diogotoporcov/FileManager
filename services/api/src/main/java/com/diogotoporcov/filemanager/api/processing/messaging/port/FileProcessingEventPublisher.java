package com.diogotoporcov.filemanager.api.processing.messaging.port;

import com.diogotoporcov.filemanager.api.processing.messaging.FileProcessingRequestedEvent;

public interface FileProcessingEventPublisher {
    void publish(FileProcessingRequestedEvent event);
}

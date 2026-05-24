package com.filemanager.api.port;

import com.filemanager.api.event.FileProcessingRequestedEvent;

public interface EventPublisherPort {
    void publishFileProcessingRequested(FileProcessingRequestedEvent event);
}

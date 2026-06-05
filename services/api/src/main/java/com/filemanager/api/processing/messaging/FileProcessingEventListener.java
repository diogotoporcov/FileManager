package com.filemanager.api.processing.messaging;

import com.filemanager.api.processing.application.ProcessingJobService;
import com.filemanager.api.processing.messaging.port.EventPublisherPort;
import com.filemanager.api.processing.messaging.port.PublishEventResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;


@Component
@RequiredArgsConstructor
@Slf4j
public class FileProcessingEventListener {

    private final EventPublisherPort eventPublisherPort;
    private final ProcessingJobService processingJobService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleFileProcessingRequested(FileProcessingRequestedEvent event) {
        log.info("Handling file processing requested event for file {} after transaction commit", event.fileId());
        try {
            PublishEventResponse response = eventPublisherPort.publishFileProcessingRequested(event);
            processingJobService.updateExternalJobId(event.processingJobId(), response.getMessageId());
        } catch (Exception e) {
            log.error("Failed to publish event to Kafka after commit. Marking job {} as FAILED", event.processingJobId(), e);
            processingJobService.handleProcessingFailure(event.processingJobId(), event.fileId(), e.getMessage());
        }
    }
}

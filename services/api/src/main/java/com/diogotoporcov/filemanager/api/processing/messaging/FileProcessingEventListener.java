package com.diogotoporcov.filemanager.api.processing.messaging;

import com.diogotoporcov.filemanager.api.processing.application.ProcessingJobService;
import com.diogotoporcov.filemanager.api.processing.messaging.port.FileProcessingEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;


@Component
@RequiredArgsConstructor
@Slf4j
public class FileProcessingEventListener {

    private final FileProcessingEventPublisher fileProcessingEventPublisher;
    private final ProcessingJobService processingJobService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleFileProcessingRequested(FileProcessingRequestedEvent event) {
        log.info("Handling file processing requested event for file {} after transaction commit", event.fileId());
        try {
            fileProcessingEventPublisher.publish(event);
            processingJobService.updateExternalJobId(event.processingJobId(), event.eventId().toString());
        } catch (Exception e) {
            log.error("Failed to publish file-processing event after commit. Marking job {} as FAILED",
                    event.processingJobId(), e);
            processingJobService.handleProcessingFailure(event.processingJobId(), event.fileId(), "Event publication failed");
        }
    }
}

package com.filemanager.api.adapter;

import com.filemanager.api.event.FileProcessingRequestedEvent;
import com.filemanager.api.port.EventPublisherPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaEventPublisherAdapter implements EventPublisherPort {

    private final KafkaTemplate<String, FileProcessingRequestedEvent> kafkaTemplate;

    @Value("${app.kafka.topics.file-processing-requested}")
    private String topic;

    @Override
    public void publishFileProcessingRequested(FileProcessingRequestedEvent event) {
        log.info("Publishing file processing requested event for file {}: job {}", event.fileId(), event.processingJobId());
        try {
            // Using .get() to make it synchronous and catch async failures as requested
            kafkaTemplate.send(topic, event.fileId().toString(), event).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while publishing event to Kafka", e);
        } catch (Exception e) {
            log.error("Failed to publish event to Kafka", e);
            Throwable cause = (e instanceof java.util.concurrent.ExecutionException) ? e.getCause() : e;
            throw new RuntimeException("Event publishing failed: " + (cause != null ? cause.getMessage() : e.getMessage()), cause);
        }
    }
}

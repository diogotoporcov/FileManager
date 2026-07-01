package com.diogotoporcov.filemanager.api.processing.messaging.adapter;

import com.diogotoporcov.filemanager.api.processing.messaging.FileProcessingRequestedEvent;
import com.diogotoporcov.filemanager.api.processing.messaging.port.FileProcessingEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaFileProcessingEventPublisher implements FileProcessingEventPublisher {

    private final KafkaTemplate<String, FileProcessingRequestedEvent> kafkaTemplate;
    private final KafkaProcessingTopicResolver processingTopicResolver;

    @Override
    public void publish(FileProcessingRequestedEvent event) {
        String topic = processingTopicResolver.resolve(event);
        log.info("Publishing file processing requested event for file {}: job {}", event.fileId(), event.processingJobId());
        try {
            SendResult<String, FileProcessingRequestedEvent> result =
                    kafkaTemplate.send(topic, event.fileId().toString(), event).get();

            log.info("Successfully published event to topic {} partition {} offset {}",
                    result.getRecordMetadata().topic(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
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

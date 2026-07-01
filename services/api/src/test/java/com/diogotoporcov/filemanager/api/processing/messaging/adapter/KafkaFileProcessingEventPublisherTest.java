package com.diogotoporcov.filemanager.api.processing.messaging.adapter;

import com.diogotoporcov.filemanager.api.processing.messaging.FileProcessingRequestedEvent;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KafkaFileProcessingEventPublisherTest {

    @Mock
    private KafkaTemplate<String, FileProcessingRequestedEvent> kafkaTemplate;

    @Mock
    private KafkaProcessingTopicResolver processingTopicResolver;

    @AfterEach
    void clearInterruptStatus() {
        Thread.interrupted();
    }

    @Test
    void publishUsesResolvedTopicAndPublishesOnce() {
        UUID fileId = UUID.randomUUID();
        FileProcessingRequestedEvent event = FileProcessingRequestedEvent.builder()
                .eventId(UUID.randomUUID())
                .fileId(fileId)
                .jobType("CHECKSUM")
                .mimeType("application/pdf")
                .build();
        String topic = "file.processing.checksum";
        SendResult<String, FileProcessingRequestedEvent> sendResult = sendResult(topic, 2, 42L);

        when(processingTopicResolver.resolve(event)).thenReturn(topic);
        when(kafkaTemplate.send(eq(topic), eq(fileId.toString()), same(event)))
                .thenReturn(CompletableFuture.completedFuture(sendResult));

        KafkaFileProcessingEventPublisher publisher =
                new KafkaFileProcessingEventPublisher(kafkaTemplate, processingTopicResolver);

        publisher.publish(event);

        verify(kafkaTemplate).send(eq(topic), eq(fileId.toString()), same(event));
        verifyNoMoreInteractions(kafkaTemplate);
    }

    @Test
    void publishRestoresInterruptStatusWhenInterrupted() throws Exception {
        UUID fileId = UUID.randomUUID();
        FileProcessingRequestedEvent event = FileProcessingRequestedEvent.builder()
                .eventId(UUID.randomUUID())
                .fileId(fileId)
                .jobType("CHECKSUM")
                .mimeType("application/pdf")
                .build();
        String topic = "file.processing.checksum";
        @SuppressWarnings("unchecked")
        CompletableFuture<SendResult<String, FileProcessingRequestedEvent>> future = mock(CompletableFuture.class);

        when(processingTopicResolver.resolve(event)).thenReturn(topic);
        when(kafkaTemplate.send(eq(topic), eq(fileId.toString()), same(event))).thenReturn(future);
        when(future.get()).thenThrow(new InterruptedException("interrupted"));
        KafkaFileProcessingEventPublisher publisher =
                new KafkaFileProcessingEventPublisher(kafkaTemplate, processingTopicResolver);

        assertThatThrownBy(() -> publisher.publish(event))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Interrupted while publishing event to Kafka");
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    @Test
    void publishWrapsFailedPublishing() {
        UUID fileId = UUID.randomUUID();
        FileProcessingRequestedEvent event = FileProcessingRequestedEvent.builder()
                .eventId(UUID.randomUUID())
                .fileId(fileId)
                .jobType("CHECKSUM")
                .mimeType("application/pdf")
                .build();
        String topic = "file.processing.checksum";
        RuntimeException failure = new RuntimeException("broker unavailable");
        CompletableFuture<SendResult<String, FileProcessingRequestedEvent>> future = new CompletableFuture<>();
        future.completeExceptionally(failure);

        when(processingTopicResolver.resolve(event)).thenReturn(topic);
        when(kafkaTemplate.send(eq(topic), eq(fileId.toString()), same(event))).thenReturn(future);
        KafkaFileProcessingEventPublisher publisher =
                new KafkaFileProcessingEventPublisher(kafkaTemplate, processingTopicResolver);

        assertThatThrownBy(() -> publisher.publish(event))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Event publishing failed: broker unavailable")
                .hasCause(failure);
    }

    private static SendResult<String, FileProcessingRequestedEvent> sendResult(
            String topic,
            int partition,
            long offset) {
        @SuppressWarnings("unchecked")
        SendResult<String, FileProcessingRequestedEvent> sendResult = mock(SendResult.class);
        RecordMetadata recordMetadata = mock(RecordMetadata.class);
        when(sendResult.getRecordMetadata()).thenReturn(recordMetadata);
        when(recordMetadata.topic()).thenReturn(topic);
        when(recordMetadata.partition()).thenReturn(partition);
        when(recordMetadata.offset()).thenReturn(offset);

        return sendResult;
    }
}

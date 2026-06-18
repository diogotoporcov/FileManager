package com.diogotoporcov.filemanager.api.processing.messaging.adapter;

import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import com.diogotoporcov.filemanager.api.processing.messaging.FileProcessingRequestedEvent;
import com.diogotoporcov.filemanager.api.processing.messaging.ProcessingTopicResolver;
import com.diogotoporcov.filemanager.api.processing.messaging.port.PublishEventResponse;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KafkaEventPublisherAdapterTest {

    @Mock
    private KafkaTemplate<String, FileProcessingRequestedEvent> kafkaTemplate;

    @Mock
    private ProcessingTopicResolver processingTopicResolver;

    @Test
    void publishFileProcessingRequestedUsesResolvedTopicAndPublishesOnce() {
        UUID eventId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        FileProcessingRequestedEvent event = FileProcessingRequestedEvent.builder()
                .eventId(eventId)
                .fileId(fileId)
                .jobType("CHECKSUM")
                .mimeType("application/pdf")
                .build();
        String topic = "file.processing.checksum";
        SendResult<String, FileProcessingRequestedEvent> sendResult = sendResult(topic, 2, 42L);

        when(processingTopicResolver.resolve(event)).thenReturn(topic);
        when(kafkaTemplate.send(eq(topic), eq(fileId.toString()), same(event)))
                .thenReturn(CompletableFuture.completedFuture(sendResult));

        KafkaEventPublisherAdapter adapter = new KafkaEventPublisherAdapter(kafkaTemplate, processingTopicResolver);

        PublishEventResponse response = adapter.publishFileProcessingRequested(event);

        assertThat(response.getMessageId()).isEqualTo(eventId.toString());
        assertThat(response.getTopic()).isEqualTo(topic);
        assertThat(response.getPartition()).isEqualTo(2);
        assertThat(response.getOffset()).isEqualTo(42L);
        verify(kafkaTemplate).send(eq(topic), eq(fileId.toString()), same(event));
        verifyNoMoreInteractions(kafkaTemplate);
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

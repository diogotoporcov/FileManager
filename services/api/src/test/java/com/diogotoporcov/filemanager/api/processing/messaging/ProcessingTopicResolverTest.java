package com.diogotoporcov.filemanager.api.processing.messaging;

import com.diogotoporcov.filemanager.api.config.AppProperties;
import com.diogotoporcov.filemanager.api.processing.domain.ProcessingJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProcessingTopicResolverTest {

    private ProcessingTopicResolver resolver;

    @BeforeEach
    void setUp() {
        AppProperties appProperties = new AppProperties();
        appProperties.getKafka().getTopics().setFileProcessingChecksum("topic.checksum");
        appProperties.getKafka().getTopics().setFileProcessingImage("topic.image");
        appProperties.getKafka().getTopics().setFileProcessingAudio("topic.audio");
        resolver = new ProcessingTopicResolver(appProperties);
    }

    @Test
    void checksumPublishesToChecksumTopicForAnyMimeType() {
        String topic = resolver.resolve(ProcessingJob.JobType.CHECKSUM, "application/pdf");

        assertThat(topic).isEqualTo("topic.checksum");
    }

    @Test
    void phashPublishesToImageTopicForSupportedImage() {
        String topic = resolver.resolve(ProcessingJob.JobType.PHASH, "image/png");

        assertThat(topic).isEqualTo("topic.image");
    }

    @Test
    void embeddingPublishesToImageTopicForSupportedImage() {
        String topic = resolver.resolve(ProcessingJob.JobType.EMBEDDING, "image/jpeg; charset=binary");

        assertThat(topic).isEqualTo("topic.image");
    }

    @Test
    void audioAnalysisPublishesToAudioTopicForStandaloneAudio() {
        String topic = resolver.resolve(ProcessingJob.JobType.AUDIO_ANALYSIS, "audio/mpeg");

        assertThat(topic).isEqualTo("topic.audio");
    }

    @Test
    void audioAnalysisRejectsVideoMimeType() {
        assertThatThrownBy(() -> resolver.resolve(ProcessingJob.JobType.AUDIO_ANALYSIS, "video/webm"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Audio analysis job cannot handle MIME type");
    }

    @Test
    void unsupportedNonMediaChecksumStillUsesChecksumTopic() {
        String topic = resolver.resolve(event("CHECKSUM", "application/octet-stream"));

        assertThat(topic).isEqualTo("topic.checksum");
    }

    @Test
    void imageJobForUnsupportedMimeTypeFailsClearly() {
        assertThatThrownBy(() -> resolver.resolve(ProcessingJob.JobType.PHASH, "application/pdf"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Image processing job cannot handle MIME type");
    }

    @Test
    void unsupportedEventJobTypeFailsClearly() {
        assertThatThrownBy(() -> resolver.resolve(event("UNKNOWN", "application/pdf")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported processing job type");
    }

    private static FileProcessingRequestedEvent event(String jobType, String mimeType) {
        return FileProcessingRequestedEvent.builder()
                .jobType(jobType)
                .mimeType(mimeType)
                .build();
    }
}

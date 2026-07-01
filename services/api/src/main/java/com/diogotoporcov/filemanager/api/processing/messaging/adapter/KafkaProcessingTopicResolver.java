package com.diogotoporcov.filemanager.api.processing.messaging.adapter;

import com.diogotoporcov.filemanager.api.config.AppProperties;
import com.diogotoporcov.filemanager.api.processing.application.mime.ProcessableAudioMimeTypes;
import com.diogotoporcov.filemanager.api.processing.application.mime.ProcessableImageMimeTypes;
import com.diogotoporcov.filemanager.api.processing.domain.ProcessingJob;
import com.diogotoporcov.filemanager.api.processing.messaging.FileProcessingRequestedEvent;
import java.util.Locale;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KafkaProcessingTopicResolver {

    private final AppProperties appProperties;

    public String resolve(FileProcessingRequestedEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        ProcessingJob.JobType jobType = parseJobType(event.jobType());

        return resolve(jobType, event.mimeType());
    }

    public String resolve(ProcessingJob.JobType jobType, String mimeType) {
        Objects.requireNonNull(jobType, "jobType must not be null");

        AppProperties.Kafka.Topics topics = appProperties.getKafka().getTopics();

        return switch (jobType) {
            case CHECKSUM -> topics.getFileProcessingChecksum();
            case PHASH, EMBEDDING -> resolveImageTopic(mimeType, topics);
            case AUDIO_ANALYSIS -> resolveAudioAnalysisTopic(mimeType, topics);
        };
    }

    private String resolveImageTopic(String mimeType, AppProperties.Kafka.Topics topics) {
        if (ProcessableImageMimeTypes.contains(appProperties.getProcessableImageMimeTypes(), mimeType)) {
            return topics.getFileProcessingImage();
        }
        throw new IllegalArgumentException("Image processing job cannot handle MIME type: " + mimeType);
    }

    private String resolveAudioAnalysisTopic(String mimeType, AppProperties.Kafka.Topics topics) {
        if (ProcessableAudioMimeTypes.contains(appProperties.getProcessableAudioMimeTypes(), mimeType)) {
            return topics.getFileProcessingAudio();
        }
        throw new IllegalArgumentException("Audio analysis job cannot handle MIME type: " + mimeType);
    }

    private ProcessingJob.JobType parseJobType(String jobType) {
        if (jobType == null || jobType.isBlank()) {
            throw new IllegalArgumentException("Processing job type must not be blank");
        }
        try {
            return ProcessingJob.JobType.valueOf(jobType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported processing job type: " + jobType, exception);
        }
    }
}

package com.filemanager.api.service;

import com.filemanager.api.config.AppProperties;
import com.filemanager.api.entity.ProcessingJob;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@Order(5)
@RequiredArgsConstructor
public class AudioAnalysisJobStrategy implements JobStrategy {

    private final AppProperties appProperties;

    @Override
    public Optional<ProcessingJob.JobType> getJobType(String mimeType) {
        if (ProcessableAudioMimeTypes.contains(appProperties.getProcessableAudioMimeTypes(), mimeType)
                || ProcessableVideoMimeTypes.contains(appProperties.getProcessableVideoMimeTypes(), mimeType)) {
            return Optional.of(ProcessingJob.JobType.AUDIO_ANALYSIS);
        }

        return Optional.empty();
    }
}

package com.diogotoporcov.filemanager.api.processing.application.job;

import com.diogotoporcov.filemanager.api.config.AppProperties;
import com.diogotoporcov.filemanager.api.processing.domain.ProcessingJob;
import com.diogotoporcov.filemanager.api.processing.application.mime.ProcessableAudioMimeTypes;
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
                && appProperties.getProcessing().getAudio().isFingerprintEnabled()) {
            return Optional.of(ProcessingJob.JobType.AUDIO_ANALYSIS);
        }

        return Optional.empty();
    }
}

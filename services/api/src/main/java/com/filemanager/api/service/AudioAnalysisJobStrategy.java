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
    private final ProcessingPolicyResolver processingPolicyResolver;

    @Override
    public Optional<ProcessingJob.JobType> getJobType(ProcessingPolicyContext context) {
        String mimeType = context.mimeType();
        ProcessingPolicyContext jobContext = context.withJobType(ProcessingJob.JobType.AUDIO_ANALYSIS);

        if (ProcessableAudioMimeTypes.contains(appProperties.getProcessableAudioMimeTypes(), mimeType)
                && processingPolicyResolver.isEnabled(ProcessingCapability.AUDIO_FINGERPRINT, jobContext)) {
            return Optional.of(ProcessingJob.JobType.AUDIO_ANALYSIS);
        }

        if (ProcessableVideoMimeTypes.contains(appProperties.getProcessableVideoMimeTypes(), mimeType)
                && processingPolicyResolver.isEnabled(ProcessingCapability.VIDEO_AUDIO_ANALYSIS, jobContext)) {
            return Optional.of(ProcessingJob.JobType.AUDIO_ANALYSIS);
        }

        return Optional.empty();
    }
}

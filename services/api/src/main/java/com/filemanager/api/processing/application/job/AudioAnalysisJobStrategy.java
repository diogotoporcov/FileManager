package com.filemanager.api.processing.application.job;

import com.filemanager.api.config.AppProperties;
import com.filemanager.api.processing.domain.ProcessingJob;
import com.filemanager.api.processing.application.mime.ProcessableAudioMimeTypes;
import com.filemanager.api.processing.application.policy.ProcessingCapability;
import com.filemanager.api.processing.application.policy.ProcessingPolicyContext;
import com.filemanager.api.processing.application.policy.ProcessingPolicyResolver;
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

        return Optional.empty();
    }
}

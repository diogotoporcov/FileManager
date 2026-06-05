package com.filemanager.api.processing.application.job;

import com.filemanager.api.config.AppProperties;
import com.filemanager.api.processing.domain.ProcessingJob;
import com.filemanager.api.processing.application.mime.ProcessableVideoMimeTypes;
import com.filemanager.api.processing.application.policy.ProcessingCapability;
import com.filemanager.api.processing.application.policy.ProcessingPolicyContext;
import com.filemanager.api.processing.application.policy.ProcessingPolicyResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@Order(4)
@RequiredArgsConstructor
public class VideoAnalysisJobStrategy implements JobStrategy {

    private final AppProperties appProperties;
    private final ProcessingPolicyResolver processingPolicyResolver;

    @Override
    public Optional<ProcessingJob.JobType> getJobType(ProcessingPolicyContext context) {
        ProcessingPolicyContext jobContext = context.withJobType(ProcessingJob.JobType.VIDEO_ANALYSIS);
        if (!processingPolicyResolver.isEnabled(ProcessingCapability.VIDEO_ANALYSIS, jobContext)
                || !isAnyFrameSignalEnabled(jobContext)) {
            return Optional.empty();
        }

        String mimeType = context.mimeType();
        if (ProcessableVideoMimeTypes.contains(appProperties.getProcessableVideoMimeTypes(), mimeType)) {
            return Optional.of(ProcessingJob.JobType.VIDEO_ANALYSIS);
        }

        return Optional.empty();
    }

    private boolean isAnyFrameSignalEnabled(ProcessingPolicyContext context) {
        return processingPolicyResolver.isEnabled(ProcessingCapability.VIDEO_FRAME_PHASH, context)
                || processingPolicyResolver.isEnabled(ProcessingCapability.VIDEO_FRAME_EMBEDDING, context);
    }
}

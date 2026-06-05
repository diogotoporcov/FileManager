package com.filemanager.api.processing.application.job;

import com.filemanager.api.config.AppProperties;
import com.filemanager.api.processing.domain.ProcessingJob;
import com.filemanager.api.processing.application.mime.ProcessableImageMimeTypes;
import com.filemanager.api.processing.application.policy.ProcessingCapability;
import com.filemanager.api.processing.application.policy.ProcessingPolicyContext;
import com.filemanager.api.processing.application.policy.ProcessingPolicyResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@Order(3)
@RequiredArgsConstructor
public class EmbeddingJobStrategy implements JobStrategy {

    private final AppProperties appProperties;
    private final ProcessingPolicyResolver processingPolicyResolver;

    @Override
    public Optional<ProcessingJob.JobType> getJobType(ProcessingPolicyContext context) {
        if (!processingPolicyResolver.isEnabled(
                ProcessingCapability.IMAGE_EMBEDDING,
                context.withJobType(ProcessingJob.JobType.EMBEDDING))) {
            return Optional.empty();
        }

        String mimeType = context.mimeType();
        if (ProcessableImageMimeTypes.contains(appProperties.getProcessableImageMimeTypes(), mimeType)) {
            return Optional.of(ProcessingJob.JobType.EMBEDDING);
        }

        return Optional.empty();
    }
}

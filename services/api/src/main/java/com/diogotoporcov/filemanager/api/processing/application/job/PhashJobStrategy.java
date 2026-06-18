package com.diogotoporcov.filemanager.api.processing.application.job;

import com.diogotoporcov.filemanager.api.config.AppProperties;
import com.diogotoporcov.filemanager.api.processing.domain.ProcessingJob;
import com.diogotoporcov.filemanager.api.processing.application.mime.ProcessableImageMimeTypes;
import com.diogotoporcov.filemanager.api.processing.application.policy.ProcessingCapability;
import com.diogotoporcov.filemanager.api.processing.application.policy.ProcessingPolicyContext;
import com.diogotoporcov.filemanager.api.processing.application.policy.ProcessingPolicyResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@Order(2)
@RequiredArgsConstructor
public class PhashJobStrategy implements JobStrategy {

    private final AppProperties appProperties;
    private final ProcessingPolicyResolver processingPolicyResolver;

    @Override
    public Optional<ProcessingJob.JobType> getJobType(ProcessingPolicyContext context) {
        if (!processingPolicyResolver.isEnabled(
                ProcessingCapability.IMAGE_PHASH,
                context.withJobType(ProcessingJob.JobType.PHASH))) {
            return Optional.empty();
        }
        String mimeType = context.mimeType();
        if (ProcessableImageMimeTypes.contains(appProperties.getProcessableImageMimeTypes(), mimeType)) {
            return Optional.of(ProcessingJob.JobType.PHASH);
        }

        return Optional.empty();
    }
}

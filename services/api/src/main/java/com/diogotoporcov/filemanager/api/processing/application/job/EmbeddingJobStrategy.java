package com.diogotoporcov.filemanager.api.processing.application.job;

import com.diogotoporcov.filemanager.api.config.AppProperties;
import com.diogotoporcov.filemanager.api.processing.domain.ProcessingJob;
import com.diogotoporcov.filemanager.api.processing.application.mime.ProcessableImageMimeTypes;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@Order(3)
@RequiredArgsConstructor
public class EmbeddingJobStrategy implements JobStrategy {

    private final AppProperties appProperties;

    @Override
    public Optional<ProcessingJob.JobType> getJobType(String mimeType) {
        if (!appProperties.getProcessing().getImage().isEmbeddingEnabled()) {
            return Optional.empty();
        }

        if (ProcessableImageMimeTypes.contains(appProperties.getProcessableImageMimeTypes(), mimeType)) {
            return Optional.of(ProcessingJob.JobType.EMBEDDING);
        }

        return Optional.empty();
    }
}

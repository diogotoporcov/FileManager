package com.filemanager.api.service;

import com.filemanager.api.config.AppProperties;
import com.filemanager.api.entity.ProcessingJob;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@Order(4)
@RequiredArgsConstructor
public class VideoAnalysisJobStrategy implements JobStrategy {

    private final AppProperties appProperties;

    @Override
    public Optional<ProcessingJob.JobType> getJobType(String mimeType) {
        if (!appProperties.getEmbedding().isEnabled()) {
            return Optional.empty();
        }

        if (ProcessableVideoMimeTypes.contains(appProperties.getProcessableVideoMimeTypes(), mimeType)) {
            return Optional.of(ProcessingJob.JobType.VIDEO_ANALYSIS);
        }

        return Optional.empty();
    }
}

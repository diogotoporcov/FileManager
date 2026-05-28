package com.filemanager.api.service;

import com.filemanager.api.config.AppProperties;
import com.filemanager.api.entity.ProcessingJob;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@Order(2)
@RequiredArgsConstructor
public class PhashJobStrategy implements JobStrategy {

    private final AppProperties appProperties;

    @Override
    public Optional<ProcessingJob.JobType> getJobType(String mimeType) {
        if (ProcessableImageMimeTypes.contains(appProperties.getProcessableImageMimeTypes(), mimeType)) {
            return Optional.of(ProcessingJob.JobType.PHASH);
        }
        return Optional.empty();
    }
}

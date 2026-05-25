package com.filemanager.api.service;

import com.filemanager.api.entity.ProcessingJob;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@Order(2)
public class PhashJobStrategy implements JobStrategy {
    @Override
    public Optional<ProcessingJob.JobType> getJobType(String mimeType) {
        if (mimeType != null && mimeType.toLowerCase().startsWith("image/")) {
            return Optional.of(ProcessingJob.JobType.PHASH);
        }
        return Optional.empty();
    }
}

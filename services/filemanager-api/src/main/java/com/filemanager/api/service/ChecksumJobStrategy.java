package com.filemanager.api.service;

import com.filemanager.api.entity.ProcessingJob;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@Order(1)
public class ChecksumJobStrategy implements JobStrategy {
    @Override
    public Optional<ProcessingJob.JobType> getJobType(String mimeType) {
        return Optional.of(ProcessingJob.JobType.CHECKSUM);
    }
}

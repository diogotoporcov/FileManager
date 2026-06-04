package com.filemanager.api.service;

import com.filemanager.api.entity.ProcessingJob;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@Order(1)
@RequiredArgsConstructor
public class ChecksumJobStrategy implements JobStrategy {

    private final ProcessingPolicyResolver processingPolicyResolver;

    @Override
    public Optional<ProcessingJob.JobType> getJobType(ProcessingPolicyContext context) {
        if (!processingPolicyResolver.isEnabled(
                ProcessingCapability.CHECKSUM,
                context.withJobType(ProcessingJob.JobType.CHECKSUM))) {
            return Optional.empty();
        }

        return Optional.of(ProcessingJob.JobType.CHECKSUM);
    }
}

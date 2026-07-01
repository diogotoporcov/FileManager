package com.diogotoporcov.filemanager.api.processing.application.job;

import com.diogotoporcov.filemanager.api.config.AppProperties;
import com.diogotoporcov.filemanager.api.processing.domain.ProcessingJob;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@Order(1)
@RequiredArgsConstructor
public class ChecksumJobStrategy implements JobStrategy {

    private final AppProperties appProperties;

    @Override
    public Optional<ProcessingJob.JobType> getJobType(String mimeType) {
        if (!appProperties.getProcessing().getChecksum().isEnabled()) {
            return Optional.empty();
        }

        return Optional.of(ProcessingJob.JobType.CHECKSUM);
    }
}

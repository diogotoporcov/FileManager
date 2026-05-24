package com.filemanager.api.service;

import com.filemanager.api.entity.ProcessingJob;
import com.filemanager.api.repository.ProcessingJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProcessingJobService {

    private final ProcessingJobRepository processingJobRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markJobAsFailed(UUID jobId, String errorMessage) {
        log.info("Marking processing job {} as FAILED. Error: {}", jobId, errorMessage);
        processingJobRepository.findById(jobId).ifPresent(job -> {
            job.setStatus(ProcessingJob.JobStatus.FAILED);
            job.setErrorMessage(errorMessage);
            processingJobRepository.save(job);
        });
    }
}

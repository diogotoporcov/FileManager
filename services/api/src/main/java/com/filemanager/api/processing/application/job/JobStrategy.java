package com.filemanager.api.processing.application.job;

import com.filemanager.api.processing.domain.ProcessingJob;
import com.filemanager.api.processing.application.policy.ProcessingPolicyContext;
import java.util.Optional;

public interface JobStrategy {
    Optional<ProcessingJob.JobType> getJobType(ProcessingPolicyContext context);
}

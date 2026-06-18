package com.diogotoporcov.filemanager.api.processing.application.job;

import com.diogotoporcov.filemanager.api.processing.domain.ProcessingJob;
import com.diogotoporcov.filemanager.api.processing.application.policy.ProcessingPolicyContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ProcessingJobPlanner {

    private final List<JobStrategy> jobStrategies;

    public List<ProcessingJob.JobType> planJobs(String mimeType) {
        return planJobs(ProcessingPolicyContext.forMimeType(mimeType));
    }

    public List<ProcessingJob.JobType> planJobs(ProcessingPolicyContext context) {
        return jobStrategies.stream()
                .map(strategy -> strategy.getJobType(context))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }
}

package com.filemanager.api.service;

import com.filemanager.api.entity.ProcessingJob;
import java.util.Optional;

public interface JobStrategy {
    Optional<ProcessingJob.JobType> getJobType(String mimeType);
}

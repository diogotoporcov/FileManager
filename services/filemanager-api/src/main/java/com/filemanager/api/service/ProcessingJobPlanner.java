package com.filemanager.api.service;

import com.filemanager.api.entity.ProcessingJob;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ProcessingJobPlanner {

    public List<ProcessingJob.JobType> planJobs(String mimeType) {
        List<ProcessingJob.JobType> jobs = new ArrayList<>();
        
        // All files get CHECKSUM
        jobs.add(ProcessingJob.JobType.CHECKSUM);
        
        // Image files get PHASH
        if (mimeType != null && mimeType.toLowerCase().startsWith("image/")) {
            jobs.add(ProcessingJob.JobType.PHASH);
        }
        
        return jobs;
    }
}

package com.filemanager.api.service;

import com.filemanager.api.entity.ProcessingJob;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ProcessingJobPlanner {

    public List<ProcessingJob.JobType> planJobs(String mimeType) {
        List<ProcessingJob.JobType> jobs = new ArrayList<>();
        
        // Mandatory checksum for integrity and exact duplicate detection.
        jobs.add(ProcessingJob.JobType.CHECKSUM);
        
        // Perceptual hash for visual similarity detection in images.
        if (mimeType != null && mimeType.toLowerCase().startsWith("image/")) {
            jobs.add(ProcessingJob.JobType.PHASH);
        }
        
        return jobs;
    }
}

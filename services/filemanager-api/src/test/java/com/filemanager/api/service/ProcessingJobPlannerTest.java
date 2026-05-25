package com.filemanager.api.service;

import com.filemanager.api.entity.ProcessingJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessingJobPlannerTest {

    private ProcessingJobPlanner planner;

    @BeforeEach
    void setUp() {
        planner = new ProcessingJobPlanner(List.of(
                new ChecksumJobStrategy(),
                new PhashJobStrategy()
        ));
    }

    @Test
    void planJobs_ShouldIncludeChecksumForAllFiles() {
        List<ProcessingJob.JobType> jobs = planner.planJobs("application/pdf");
        assertTrue(jobs.contains(ProcessingJob.JobType.CHECKSUM));
    }

    @Test
    void planJobs_ShouldIncludePhashForImages() {
        List<ProcessingJob.JobType> jobs = planner.planJobs("image/png");
        assertTrue(jobs.contains(ProcessingJob.JobType.PHASH));
    }

    @Test
    void planJobs_ShouldHandleUppercaseMimeType() {
        List<ProcessingJob.JobType> jobs = planner.planJobs("IMAGE/JPEG");
        assertTrue(jobs.contains(ProcessingJob.JobType.PHASH));
    }
}

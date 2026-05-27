package com.filemanager.api.service;

import com.filemanager.api.config.AppProperties;
import com.filemanager.api.entity.ProcessingJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessingJobPlannerTest {

    private ProcessingJobPlanner planner;

    @BeforeEach
    void setUp() {
        AppProperties appProperties = new AppProperties();
        planner = new ProcessingJobPlanner(List.of(
                new ChecksumJobStrategy(),
                new PhashJobStrategy(),
                new EmbeddingJobStrategy(appProperties)
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
    void planJobs_ShouldIncludeEmbeddingForImages() {
        List<ProcessingJob.JobType> jobs = planner.planJobs("image/png");
        assertTrue(jobs.contains(ProcessingJob.JobType.EMBEDDING));
    }

    @Test
    void planJobs_ShouldHandleUppercaseMimeType() {
        List<ProcessingJob.JobType> jobs = planner.planJobs("IMAGE/JPEG");
        assertTrue(jobs.contains(ProcessingJob.JobType.PHASH));
        assertTrue(jobs.contains(ProcessingJob.JobType.EMBEDDING));
    }
}

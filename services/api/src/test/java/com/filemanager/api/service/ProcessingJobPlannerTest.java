package com.filemanager.api.service;

import com.filemanager.api.config.AppProperties;
import com.filemanager.api.entity.ProcessingJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessingJobPlannerTest {

    private ProcessingJobPlanner planner;

    @BeforeEach
    void setUp() {
        AppProperties appProperties = new AppProperties();
        planner = new ProcessingJobPlanner(List.of(
                new ChecksumJobStrategy(),
                new PhashJobStrategy(appProperties),
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

    @Test
    void planJobs_ShouldIncludeImageJobsForSupportedMimeTypeWithParameters() {
        List<ProcessingJob.JobType> jobs = planner.planJobs("image/jpeg; charset=binary");
        assertTrue(jobs.contains(ProcessingJob.JobType.PHASH));
        assertTrue(jobs.contains(ProcessingJob.JobType.EMBEDDING));
    }

    @Test
    void planJobs_ShouldNotIncludeImageJobsForUnsupportedImageMimeType() {
        List<ProcessingJob.JobType> jobs = planner.planJobs("image/svg+xml");
        assertTrue(jobs.contains(ProcessingJob.JobType.CHECKSUM));
        assertFalse(jobs.contains(ProcessingJob.JobType.PHASH));
        assertFalse(jobs.contains(ProcessingJob.JobType.EMBEDDING));
    }

    @Test
    void planJobs_ShouldUseConfiguredProcessableImageMimeTypes() {
        AppProperties appProperties = new AppProperties();
        appProperties.setProcessableImageMimeTypes(Set.of("image/example"));
        ProcessingJobPlanner customPlanner = new ProcessingJobPlanner(List.of(
                new ChecksumJobStrategy(),
                new PhashJobStrategy(appProperties),
                new EmbeddingJobStrategy(appProperties)
        ));

        List<ProcessingJob.JobType> supportedJobs = customPlanner.planJobs("image/example");
        List<ProcessingJob.JobType> defaultImageJobs = customPlanner.planJobs("image/png");

        assertTrue(supportedJobs.contains(ProcessingJob.JobType.PHASH));
        assertTrue(supportedJobs.contains(ProcessingJob.JobType.EMBEDDING));
        assertFalse(defaultImageJobs.contains(ProcessingJob.JobType.PHASH));
        assertFalse(defaultImageJobs.contains(ProcessingJob.JobType.EMBEDDING));
    }
}

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
                new EmbeddingJobStrategy(appProperties),
                new VideoAnalysisJobStrategy(appProperties),
                new AudioAnalysisJobStrategy(appProperties)
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
    void planJobs_ShouldIncludeVideoAnalysisForSupportedVideo() {
        List<ProcessingJob.JobType> jobs = planner.planJobs("video/mp4");

        assertTrue(jobs.contains(ProcessingJob.JobType.CHECKSUM));
        assertTrue(jobs.contains(ProcessingJob.JobType.VIDEO_ANALYSIS));
        assertFalse(jobs.contains(ProcessingJob.JobType.PHASH));
        assertFalse(jobs.contains(ProcessingJob.JobType.EMBEDDING));
    }

    @Test
    void planJobs_ShouldIncludeVideoAnalysisForAdditionalSupportedVideoTypes() {
        Set<String> processableVideoMimeTypes = Set.of(
                "video/x-msvideo",
                "video/matroska",
                "video/x-matroska",
                "video/x-m4v",
                "video/mpeg",
                "video/MP2T",
                "video/3gpp"
        );
        ProcessingJobPlanner customPlanner = plannerWithProcessableVideoMimeTypes(processableVideoMimeTypes);

        processableVideoMimeTypes.forEach(mimeType -> {
            List<ProcessingJob.JobType> jobs = customPlanner.planJobs(mimeType);

            assertTrue(jobs.contains(ProcessingJob.JobType.CHECKSUM));
            assertTrue(jobs.contains(ProcessingJob.JobType.VIDEO_ANALYSIS));
            assertTrue(jobs.contains(ProcessingJob.JobType.AUDIO_ANALYSIS));
            assertFalse(jobs.contains(ProcessingJob.JobType.PHASH));
            assertFalse(jobs.contains(ProcessingJob.JobType.EMBEDDING));
        });
    }

    @Test
    void planJobs_ShouldHandleVideoMimeTypeWithParameters() {
        List<ProcessingJob.JobType> jobs = planner.planJobs("video/quicktime; charset=binary");

        assertTrue(jobs.contains(ProcessingJob.JobType.VIDEO_ANALYSIS));
    }

    @Test
    void planJobs_ShouldNormalizeAdditionalVideoMimeTypes() {
        ProcessingJobPlanner customPlanner = plannerWithProcessableVideoMimeTypes(Set.of("video/x-matroska"));

        List.of(
                "VIDEO/X-MATROSKA",
                "video/x-matroska; charset=binary"
        ).forEach(mimeType -> {
            List<ProcessingJob.JobType> jobs = customPlanner.planJobs(mimeType);

            assertTrue(jobs.contains(ProcessingJob.JobType.VIDEO_ANALYSIS));
            assertTrue(jobs.contains(ProcessingJob.JobType.AUDIO_ANALYSIS));
        });
    }

    @Test
    void planJobs_ShouldNotIncludeVideoAnalysisForUnsupportedVideo() {
        List<ProcessingJob.JobType> jobs = planner.planJobs("video/x-flv");

        assertTrue(jobs.contains(ProcessingJob.JobType.CHECKSUM));
        assertFalse(jobs.contains(ProcessingJob.JobType.VIDEO_ANALYSIS));
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
                new EmbeddingJobStrategy(appProperties),
                new VideoAnalysisJobStrategy(appProperties),
                new AudioAnalysisJobStrategy(appProperties)
        ));

        List<ProcessingJob.JobType> supportedJobs = customPlanner.planJobs("image/example");
        List<ProcessingJob.JobType> defaultImageJobs = customPlanner.planJobs("image/png");

        assertTrue(supportedJobs.contains(ProcessingJob.JobType.PHASH));
        assertTrue(supportedJobs.contains(ProcessingJob.JobType.EMBEDDING));
        assertFalse(defaultImageJobs.contains(ProcessingJob.JobType.PHASH));
        assertFalse(defaultImageJobs.contains(ProcessingJob.JobType.EMBEDDING));
    }

    @Test
    void planJobs_ShouldUseConfiguredProcessableVideoMimeTypes() {
        AppProperties appProperties = new AppProperties();
        appProperties.setProcessableVideoMimeTypes(Set.of("video/example"));
        ProcessingJobPlanner customPlanner = new ProcessingJobPlanner(List.of(
                new ChecksumJobStrategy(),
                new PhashJobStrategy(appProperties),
                new EmbeddingJobStrategy(appProperties),
                new VideoAnalysisJobStrategy(appProperties),
                new AudioAnalysisJobStrategy(appProperties)
        ));

        List<ProcessingJob.JobType> supportedJobs = customPlanner.planJobs("video/example");
        List<ProcessingJob.JobType> defaultVideoJobs = customPlanner.planJobs("video/mp4");

        assertTrue(supportedJobs.contains(ProcessingJob.JobType.VIDEO_ANALYSIS));
        assertFalse(defaultVideoJobs.contains(ProcessingJob.JobType.VIDEO_ANALYSIS));
    }

    @Test
    void planJobs_ShouldIncludeAudioAnalysisForSupportedAudio() {
        List<ProcessingJob.JobType> jobs = planner.planJobs("audio/mpeg");

        assertTrue(jobs.contains(ProcessingJob.JobType.CHECKSUM));
        assertTrue(jobs.contains(ProcessingJob.JobType.AUDIO_ANALYSIS));
        assertFalse(jobs.contains(ProcessingJob.JobType.PHASH));
        assertFalse(jobs.contains(ProcessingJob.JobType.EMBEDDING));
        assertFalse(jobs.contains(ProcessingJob.JobType.VIDEO_ANALYSIS));
    }

    @Test
    void planJobs_ShouldIncludeAudioAnalysisForAdditionalSupportedAudioTypes() {
        Set<String> processableAudioMimeTypes = Set.of(
                "audio/webm",
                "audio/opus",
                "audio/matroska",
                "audio/vnd.wave",
                "audio/wave",
                "audio/x-flac",
                "audio/ac3",
                "audio/x-aiff"
        );
        ProcessingJobPlanner customPlanner = plannerWithProcessableAudioMimeTypes(processableAudioMimeTypes);

        processableAudioMimeTypes.forEach(mimeType -> {
            List<ProcessingJob.JobType> jobs = customPlanner.planJobs(mimeType);

            assertTrue(jobs.contains(ProcessingJob.JobType.CHECKSUM));
            assertTrue(jobs.contains(ProcessingJob.JobType.AUDIO_ANALYSIS));
            assertFalse(jobs.contains(ProcessingJob.JobType.PHASH));
            assertFalse(jobs.contains(ProcessingJob.JobType.EMBEDDING));
            assertFalse(jobs.contains(ProcessingJob.JobType.VIDEO_ANALYSIS));
        });
    }

    @Test
    void planJobs_ShouldIncludeVideoAndAudioAnalysisForSupportedVideo() {
        List<ProcessingJob.JobType> jobs = planner.planJobs("video/mp4");

        assertTrue(jobs.contains(ProcessingJob.JobType.VIDEO_ANALYSIS));
        assertTrue(jobs.contains(ProcessingJob.JobType.AUDIO_ANALYSIS));
    }

    @Test
    void planJobs_ShouldHandleAudioMimeTypeWithParameters() {
        List<ProcessingJob.JobType> jobs = planner.planJobs("audio/x-m4a; charset=binary");

        assertTrue(jobs.contains(ProcessingJob.JobType.AUDIO_ANALYSIS));
    }

    @Test
    void planJobs_ShouldNormalizeAdditionalAudioMimeTypes() {
        ProcessingJobPlanner customPlanner = plannerWithProcessableAudioMimeTypes(Set.of("audio/webm", "audio/vnd.wave"));

        List.of(
                " audio/webm ",
                "audio/vnd.wave; codecs=1"
        ).forEach(mimeType -> {
            List<ProcessingJob.JobType> jobs = customPlanner.planJobs(mimeType);

            assertTrue(jobs.contains(ProcessingJob.JobType.AUDIO_ANALYSIS));
            assertFalse(jobs.contains(ProcessingJob.JobType.VIDEO_ANALYSIS));
        });
    }

    @Test
    void planJobs_ShouldNotIncludeAudioAnalysisForUnsupportedMimeType() {
        List<ProcessingJob.JobType> jobs = planner.planJobs("application/pdf");

        assertTrue(jobs.contains(ProcessingJob.JobType.CHECKSUM));
        assertFalse(jobs.contains(ProcessingJob.JobType.AUDIO_ANALYSIS));
    }

    @Test
    void planJobs_ShouldUseConfiguredProcessableAudioMimeTypes() {
        AppProperties appProperties = new AppProperties();
        appProperties.setProcessableAudioMimeTypes(Set.of("audio/example"));
        ProcessingJobPlanner customPlanner = plannerWith(appProperties);

        List<ProcessingJob.JobType> supportedJobs = customPlanner.planJobs("audio/example");
        List<ProcessingJob.JobType> defaultAudioJobs = customPlanner.planJobs("audio/mpeg");

        assertTrue(supportedJobs.contains(ProcessingJob.JobType.AUDIO_ANALYSIS));
        assertFalse(defaultAudioJobs.contains(ProcessingJob.JobType.AUDIO_ANALYSIS));
    }

    private static ProcessingJobPlanner plannerWithProcessableVideoMimeTypes(Set<String> processableVideoMimeTypes) {
        AppProperties appProperties = new AppProperties();
        appProperties.setProcessableVideoMimeTypes(processableVideoMimeTypes);
        return plannerWith(appProperties);
    }

    private static ProcessingJobPlanner plannerWithProcessableAudioMimeTypes(Set<String> processableAudioMimeTypes) {
        AppProperties appProperties = new AppProperties();
        appProperties.setProcessableAudioMimeTypes(processableAudioMimeTypes);
        return plannerWith(appProperties);
    }

    private static ProcessingJobPlanner plannerWith(AppProperties appProperties) {
        return new ProcessingJobPlanner(List.of(
                new ChecksumJobStrategy(),
                new PhashJobStrategy(appProperties),
                new EmbeddingJobStrategy(appProperties),
                new VideoAnalysisJobStrategy(appProperties),
                new AudioAnalysisJobStrategy(appProperties)
        ));
    }
}

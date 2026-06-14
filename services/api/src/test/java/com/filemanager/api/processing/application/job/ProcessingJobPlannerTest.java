package com.filemanager.api.processing.application.job;

import static org.assertj.core.api.Assertions.assertThat;

import com.filemanager.api.config.AppProperties;
import com.filemanager.api.processing.application.policy.GlobalProcessingPolicyResolver;
import com.filemanager.api.processing.domain.ProcessingJob;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProcessingJobPlannerTest {

    private ProcessingJobPlanner planner;

    @BeforeEach
    void setUp() {
        planner = plannerWith(new AppProperties());
    }

    @Test
    void planJobs_IncludesChecksumForGenericFile() {
        assertThat(planner.planJobs("application/pdf"))
                .containsExactly(ProcessingJob.JobType.CHECKSUM);
    }

    @Test
    void planJobs_ImageUploadSchedulesChecksumPhashAndEmbedding() {
        assertThat(planner.planJobs("image/png"))
                .containsExactly(
                        ProcessingJob.JobType.CHECKSUM,
                        ProcessingJob.JobType.PHASH,
                        ProcessingJob.JobType.EMBEDDING);
    }

    @Test
    void planJobs_AudioUploadSchedulesAudioAnalysis() {
        assertThat(planner.planJobs("audio/mpeg"))
                .containsExactly(
                        ProcessingJob.JobType.CHECKSUM,
                        ProcessingJob.JobType.AUDIO_ANALYSIS);
    }

    @Test
    void planJobs_VideoUploadSchedulesChecksumOnly() {
        assertThat(planner.planJobs("video/mp4"))
                .containsExactly(ProcessingJob.JobType.CHECKSUM);
    }

    @Test
    void planJobs_AudioAnalysisUsesConfiguredAudioMimeTypesOnly() {
        AppProperties appProperties = new AppProperties();
        appProperties.setProcessableAudioMimeTypes(Set.of("audio/example"));

        ProcessingJobPlanner customPlanner = plannerWith(appProperties);

        assertThat(customPlanner.planJobs("audio/example"))
                .containsExactly(
                        ProcessingJob.JobType.CHECKSUM,
                        ProcessingJob.JobType.AUDIO_ANALYSIS);
        assertThat(customPlanner.planJobs("audio/mpeg"))
                .containsExactly(ProcessingJob.JobType.CHECKSUM);
        assertThat(customPlanner.planJobs("video/mp4"))
                .containsExactly(ProcessingJob.JobType.CHECKSUM);
    }

    @Test
    void planJobs_DisabledAudioFingerprintSkipsAudioAnalysis() {
        AppProperties appProperties = new AppProperties();
        appProperties.getProcessing().getAudio().setFingerprintEnabled(false);

        assertThat(plannerWith(appProperties).planJobs("audio/mpeg"))
                .containsExactly(ProcessingJob.JobType.CHECKSUM);
    }

    private static ProcessingJobPlanner plannerWith(AppProperties appProperties) {
        GlobalProcessingPolicyResolver resolver = new GlobalProcessingPolicyResolver(appProperties);
        return new ProcessingJobPlanner(List.of(
                new ChecksumJobStrategy(resolver),
                new PhashJobStrategy(appProperties, resolver),
                new EmbeddingJobStrategy(appProperties, resolver),
                new AudioAnalysisJobStrategy(appProperties, resolver)
        ));
    }
}

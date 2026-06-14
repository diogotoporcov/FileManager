package com.filemanager.api.processing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.filemanager.api.config.AppProperties;
import com.filemanager.api.config.EmbeddingDimensions;
import com.filemanager.api.duplicate.application.ExactDuplicateGroupMaintenanceService;
import com.filemanager.api.file.domain.FileEntity;
import com.filemanager.api.file.persistence.FileRepository;
import com.filemanager.api.identity.domain.User;
import com.filemanager.api.observability.application.FileManagerMetrics;
import com.filemanager.api.processing.domain.ProcessingJob;
import com.filemanager.api.processing.domain.result.AudioFingerprint;
import com.filemanager.api.processing.domain.result.FileEmbedding;
import com.filemanager.api.processing.domain.result.FileFingerprint;
import com.filemanager.api.processing.domain.result.ImageFingerprint;
import com.filemanager.api.processing.persistence.ProcessingJobRepository;
import com.filemanager.api.processing.persistence.result.AudioFingerprintRepository;
import com.filemanager.api.processing.persistence.result.FileEmbeddingRepository;
import com.filemanager.api.processing.persistence.result.FileFingerprintRepository;
import com.filemanager.api.processing.persistence.result.ImageFingerprintRepository;
import com.filemanager.api.processing.web.result.AudioAnalysisResultRequest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProcessingJobServiceTest {
    @Mock
    private ProcessingJobRepository processingJobRepository;
    @Mock
    private FileRepository fileRepository;
    @Mock
    private FileFingerprintRepository fileFingerprintRepository;
    @Mock
    private ImageFingerprintRepository imageFingerprintRepository;
    @Mock
    private FileEmbeddingRepository fileEmbeddingRepository;
    @Mock
    private AudioFingerprintRepository audioFingerprintRepository;
    @Mock
    private FileManagerMetrics fileManagerMetrics;
    @Mock
    private ExactDuplicateGroupMaintenanceService exactDuplicateGroupMaintenanceService;

    private ProcessingJobService service;
    private User owner;
    private AppProperties appProperties;

    @BeforeEach
    void setUp() {
        appProperties = new AppProperties();
        service = new ProcessingJobService(
                processingJobRepository,
                fileRepository,
                fileFingerprintRepository,
                imageFingerprintRepository,
                fileEmbeddingRepository,
                audioFingerprintRepository,
                fileManagerMetrics,
                appProperties,
                exactDuplicateGroupMaintenanceService);
        owner = User.builder().id(UUID.randomUUID()).build();
    }

    @Test
    void handleChecksumResult_PersistsFingerprintAndRefreshesExactGroups() {
        UUID jobId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        FileEntity file = file(fileId);
        ProcessingJob job = job(jobId, file, ProcessingJob.JobType.CHECKSUM);
        String sha256 = "E3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B855";
        when(processingJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.of(file));
        when(fileFingerprintRepository.findByFileIdAndAlgorithm(fileId, FileFingerprint.FingerprintAlgorithm.SHA256))
                .thenReturn(Optional.empty());

        service.handleChecksumResult(jobId, fileId, sha256);

        ArgumentCaptor<FileFingerprint> captor = ArgumentCaptor.forClass(FileFingerprint.class);
        verify(fileFingerprintRepository).save(captor.capture());
        assertThat(captor.getValue().getHashValue()).isEqualTo(sha256.toLowerCase());
        verify(exactDuplicateGroupMaintenanceService).refreshAfterFingerprintChange(
                owner.getId(),
                FileFingerprint.FingerprintAlgorithm.SHA256,
                null,
                sha256.toLowerCase());
        assertThat(job.getStatus()).isEqualTo(ProcessingJob.JobStatus.COMPLETED);
    }

    @Test
    void handlePhashResult_PersistsEvidenceWithoutCandidateRefresh() {
        UUID jobId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        FileEntity file = file(fileId);
        ProcessingJob job = job(jobId, file, ProcessingJob.JobType.PHASH);
        when(processingJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.of(file));
        when(imageFingerprintRepository.findByFileId(fileId)).thenReturn(Optional.empty());

        service.handlePhashResult(jobId, fileId, "FEDCBA9876543210");

        ArgumentCaptor<ImageFingerprint> captor = ArgumentCaptor.forClass(ImageFingerprint.class);
        verify(imageFingerprintRepository).save(captor.capture());
        assertThat(captor.getValue().getPhash()).isEqualTo("fedcba9876543210");
        verify(fileManagerMetrics).recordJobCompleted("PHASH");
    }

    @Test
    void handleEmbeddingResult_PersistsEvidenceWithoutCandidateRefresh() {
        UUID jobId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        FileEntity file = file(fileId);
        ProcessingJob job = job(jobId, file, ProcessingJob.JobType.EMBEDDING);
        when(processingJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.of(file));
        when(fileEmbeddingRepository.findByFileIdAndModelNameAndModelVersion(
                fileId,
                appProperties.getEmbedding().getModelName(),
                appProperties.getEmbedding().getModelVersion()))
                .thenReturn(Optional.empty());

        service.handleEmbeddingResult(
                jobId,
                fileId,
                appProperties.getEmbedding().getModelName(),
                appProperties.getEmbedding().getModelVersion(),
                EmbeddingDimensions.IMAGE_EMBEDDING_DIMENSION,
                embeddingVector());

        ArgumentCaptor<FileEmbedding> captor = ArgumentCaptor.forClass(FileEmbedding.class);
        verify(fileEmbeddingRepository).save(captor.capture());
        assertThat(captor.getValue().getEmbedding()).hasSize(EmbeddingDimensions.IMAGE_EMBEDDING_DIMENSION);
        verify(fileManagerMetrics).recordJobCompleted("EMBEDDING");
    }

    @Test
    void handleAudioAnalysisResult_PersistsEvidenceWithoutCandidateRefresh() {
        UUID jobId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        FileEntity file = file(fileId);
        ProcessingJob job = job(jobId, file, ProcessingJob.JobType.AUDIO_ANALYSIS);
        when(processingJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.of(file));
        when(audioFingerprintRepository.findByFileId(fileId)).thenReturn(Optional.empty());

        service.handleAudioAnalysisResult(jobId, audioRequest(fileId));

        ArgumentCaptor<AudioFingerprint> captor = ArgumentCaptor.forClass(AudioFingerprint.class);
        verify(audioFingerprintRepository).save(captor.capture());
        assertThat(captor.getValue().getFingerprintAlgorithm()).isEqualTo("chromaprint");
        assertThat(captor.getValue().getFingerprintVersion()).isEqualTo("fpcalc");
        verify(fileManagerMetrics).recordJobCompleted("AUDIO_ANALYSIS");
    }

    @Test
    void handleAudioAnalysisResult_RejectsWrongJobType() {
        UUID jobId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        when(processingJobRepository.findById(jobId))
                .thenReturn(Optional.of(job(jobId, file(fileId), ProcessingJob.JobType.EMBEDDING)));

        assertThrows(IllegalArgumentException.class, () -> service.handleAudioAnalysisResult(jobId, audioRequest(fileId)));
        verify(audioFingerprintRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private FileEntity file(UUID fileId) {
        return FileEntity.builder()
                .id(fileId)
                .name("file")
                .mimeType("image/jpeg")
                .size(1L)
                .ownerUser(owner)
                .build();
    }

    private ProcessingJob job(UUID jobId, FileEntity file, ProcessingJob.JobType type) {
        ProcessingJob job = new ProcessingJob();
        job.setId(jobId);
        job.setFile(file);
        job.setJobType(type);
        job.setStatus(ProcessingJob.JobStatus.PENDING);
        return job;
    }

    private List<Double> embeddingVector() {
        return java.util.stream.Stream.generate(() -> 1.0)
                .limit(EmbeddingDimensions.IMAGE_EMBEDDING_DIMENSION)
                .toList();
    }

    private AudioAnalysisResultRequest audioRequest(UUID fileId) {
        return AudioAnalysisResultRequest.builder()
                .fileId(fileId)
                .durationMs(1_000L)
                .codec("mp3")
                .sampleRate(44_100)
                .channels(2)
                .bitRate(128_000L)
                .audioStreamIndex(0)
                .containerFormat("mp3")
                .fingerprint("12345")
                .fingerprintAlgorithm("chromaprint")
                .fingerprintVersion("fpcalc")
                .fingerprintDurationSeconds(60)
                .build();
    }
}

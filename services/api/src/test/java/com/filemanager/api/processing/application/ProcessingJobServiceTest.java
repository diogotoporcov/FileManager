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
import com.filemanager.api.processing.domain.result.ImagePhashMihChunk;
import com.filemanager.api.processing.persistence.ProcessingJobRepository;
import com.filemanager.api.processing.persistence.result.AudioFingerprintRepository;
import com.filemanager.api.processing.persistence.result.FileEmbeddingRepository;
import com.filemanager.api.processing.persistence.result.FileFingerprintRepository;
import com.filemanager.api.processing.persistence.result.ImageFingerprintRepository;
import com.filemanager.api.processing.persistence.result.ImagePhashMihChunkRepository;
import com.filemanager.api.processing.web.result.AudioAnalysisResultRequest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
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
    private ImagePhashMihChunkRepository imagePhashMihChunkRepository;
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
                imagePhashMihChunkRepository,
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
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ImagePhashMihChunk>> chunksCaptor = ArgumentCaptor.forClass(List.class);
        verify(imagePhashMihChunkRepository).deleteByIdFileId(fileId);
        verify(imagePhashMihChunkRepository).saveAll(chunksCaptor.capture());
        assertThat(chunksCaptor.getValue())
                .hasSize(3)
                .extracting(chunk -> chunk.getId().getChunkIndex())
                .containsExactly((short) 0, (short) 1, (short) 2);
        verify(fileManagerMetrics).recordJobCompleted("PHASH");
    }

    @Test
    void handlePhashResult_ReplacesExistingChunksWhenFingerprintIsRewritten() {
        UUID jobId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        FileEntity file = file(fileId);
        ProcessingJob job = job(jobId, file, ProcessingJob.JobType.PHASH);
        ImageFingerprint existing = ImageFingerprint.builder().file(file).phash("0000000000000000").build();
        when(processingJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.of(file));
        when(imageFingerprintRepository.findByFileId(fileId)).thenReturn(Optional.of(existing));

        service.handlePhashResult(jobId, fileId, "ffffffffffffffff");

        assertThat(existing.getPhash()).isEqualTo("ffffffffffffffff");
        verify(imagePhashMihChunkRepository).deleteByIdFileId(fileId);
        verify(imagePhashMihChunkRepository).saveAll(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void handlePhashResult_PropagatesChunkWriteFailureBeforeCompletingJob() {
        UUID jobId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        FileEntity file = file(fileId);
        ProcessingJob job = job(jobId, file, ProcessingJob.JobType.PHASH);
        when(processingJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.of(file));
        when(imageFingerprintRepository.findByFileId(fileId)).thenReturn(Optional.empty());
        Mockito.doThrow(new IllegalStateException("chunk write failed"))
                .when(imagePhashMihChunkRepository)
                .saveAll(org.mockito.ArgumentMatchers.anyList());

        assertThrows(IllegalStateException.class, () -> service.handlePhashResult(jobId, fileId, "0123456789abcdef"));

        verify(imageFingerprintRepository).save(org.mockito.ArgumentMatchers.any(ImageFingerprint.class));
        verify(fileManagerMetrics, never()).recordJobCompleted("PHASH");
        assertThat(job.getStatus()).isEqualTo(ProcessingJob.JobStatus.PENDING);
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

    @Test
    void handlePhashResult_IgnoresDuplicateCompletedCallback() {
        UUID jobId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        ProcessingJob job = job(jobId, file(fileId), ProcessingJob.JobType.PHASH);
        job.setStatus(ProcessingJob.JobStatus.COMPLETED);
        when(processingJobRepository.findById(jobId)).thenReturn(Optional.of(job));

        service.handlePhashResult(jobId, fileId, "fedcba9876543210");

        verify(fileRepository, never()).findByIdAndDeletedAtIsNull(fileId);
        verify(imageFingerprintRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(fileManagerMetrics, never()).recordJobCompleted("PHASH");
    }

    @Test
    void handleProcessingFailure_IgnoresStaleFailureForCompletedJob() {
        UUID jobId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        ProcessingJob job = job(jobId, file(fileId), ProcessingJob.JobType.CHECKSUM);
        job.setStatus(ProcessingJob.JobStatus.COMPLETED);
        when(processingJobRepository.findById(jobId)).thenReturn(Optional.of(job));

        service.handleProcessingFailure(jobId, fileId, "late failure");

        assertThat(job.getStatus()).isEqualTo(ProcessingJob.JobStatus.COMPLETED);
        verify(processingJobRepository, never()).save(job);
        verify(fileManagerMetrics, never()).recordJobFailed("CHECKSUM");
    }

    @Test
    void handleProcessingFailure_NormalizesAndCapsUserVisibleErrorMessage() {
        UUID jobId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        ProcessingJob job = job(jobId, file(fileId), ProcessingJob.JobType.CHECKSUM);
        when(processingJobRepository.findById(jobId)).thenReturn(Optional.of(job));

        service.handleProcessingFailure(jobId, fileId, "failed\n" + "x".repeat(2_000));

        assertThat(job.getStatus()).isEqualTo(ProcessingJob.JobStatus.FAILED);
        assertThat(job.getErrorMessage()).doesNotContain("\n");
        assertThat(job.getErrorMessage()).hasSize(1024);
        verify(fileManagerMetrics).recordJobFailed("CHECKSUM");
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

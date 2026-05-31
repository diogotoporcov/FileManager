package com.filemanager.api.service;

import com.filemanager.api.config.AppProperties;
import com.filemanager.api.entity.FileEmbedding;
import com.filemanager.api.entity.FileEntity;
import com.filemanager.api.entity.FileFingerprint;
import com.filemanager.api.entity.ImageFingerprint;
import com.filemanager.api.entity.ProcessingJob;
import com.filemanager.api.entity.User;
import com.filemanager.api.port.ApplicationMetricsPort;
import com.filemanager.api.repository.FileEmbeddingRepository;
import com.filemanager.api.repository.FileFingerprintRepository;
import com.filemanager.api.repository.FileRepository;
import com.filemanager.api.repository.ImageFingerprintRepository;
import com.filemanager.api.repository.ProcessingJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

import static org.mockito.Mockito.when;

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
    private ApplicationMetricsPort applicationMetricsPort;
    @Mock
    private AppProperties appProperties;

    @InjectMocks
    private ProcessingJobService processingJobService;

    private User owner;

    @BeforeEach
    void setup() {
        AppProperties.Embedding embedding = new AppProperties.Embedding();
        embedding.setEnabled(true);
        embedding.setModelName("openai/clip-vit-large-patch14");
        embedding.setModelVersion("1");
        embedding.setDimension(768);
        embedding.setSimilarityThreshold(0.20);
        embedding.setMaxCandidates(5000);
        lenient().when(appProperties.getEmbedding()).thenReturn(embedding);

        owner = User.builder().id(UUID.randomUUID()).build();
    }

    @Test
    void handleChecksumResult_PersistsFingerprintAndCompletesJob() {
        UUID jobId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        String sha256 = "E3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B855";
        FileEntity file = file(fileId);
        ProcessingJob job = job(jobId, file, ProcessingJob.JobType.CHECKSUM);

        when(processingJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.of(file));
        when(fileFingerprintRepository.findByFileIdAndAlgorithm(fileId, FileFingerprint.FingerprintAlgorithm.SHA256))
                .thenReturn(Optional.empty());

        processingJobService.handleChecksumResult(jobId, fileId, sha256);

        ArgumentCaptor<FileFingerprint> fingerprintCaptor = ArgumentCaptor.forClass(FileFingerprint.class);
        verify(fileFingerprintRepository).save(fingerprintCaptor.capture());
        assertThat(fingerprintCaptor.getValue().getFile()).isEqualTo(file);
        assertThat(fingerprintCaptor.getValue().getHashValue()).isEqualTo(sha256.toLowerCase());
        assertThat(job.getStatus()).isEqualTo(ProcessingJob.JobStatus.COMPLETED);
        verify(applicationMetricsPort).recordJobCompleted("CHECKSUM");
    }

    @Test
    void handlePhashResult_PersistsFingerprintAndCompletesJob() {
        UUID jobId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        FileEntity file = file(fileId);
        ProcessingJob job = job(jobId, file, ProcessingJob.JobType.PHASH);

        when(processingJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.of(file));
        when(imageFingerprintRepository.findByFileId(fileId)).thenReturn(Optional.empty());

        processingJobService.handlePhashResult(jobId, fileId, "FEDCBA9876543210");

        ArgumentCaptor<ImageFingerprint> fingerprintCaptor = ArgumentCaptor.forClass(ImageFingerprint.class);
        verify(imageFingerprintRepository).save(fingerprintCaptor.capture());
        assertThat(fingerprintCaptor.getValue().getFile()).isEqualTo(file);
        assertThat(fingerprintCaptor.getValue().getPhash()).isEqualTo("fedcba9876543210");
        assertThat(job.getStatus()).isEqualTo(ProcessingJob.JobStatus.COMPLETED);
        verify(applicationMetricsPort).recordJobCompleted("PHASH");
    }

    @Test
    void handleEmbeddingResult_PersistsEmbeddingAndCompletesJob() {
        UUID jobId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        FileEntity file = file(fileId);
        ProcessingJob job = job(jobId, file, ProcessingJob.JobType.EMBEDDING);

        when(processingJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.of(file));
        when(fileEmbeddingRepository.findByFileIdAndModelNameAndModelVersion(
                fileId,
                "openai/clip-vit-large-patch14",
                "1"))
                .thenReturn(Optional.empty());

        processingJobService.handleEmbeddingResult(
                jobId,
                fileId,
                "openai/clip-vit-large-patch14",
                "1",
                768,
                embeddingVector(768));

        ArgumentCaptor<FileEmbedding> embeddingCaptor = ArgumentCaptor.forClass(FileEmbedding.class);
        verify(fileEmbeddingRepository).save(embeddingCaptor.capture());
        assertThat(embeddingCaptor.getValue().getFile()).isEqualTo(file);
        assertThat(embeddingCaptor.getValue().getEmbedding()).hasSize(768);
        assertThat(job.getStatus()).isEqualTo(ProcessingJob.JobStatus.COMPLETED);
        verify(applicationMetricsPort).recordJobCompleted("EMBEDDING");
    }

    @Test
    void handleEmbeddingResult_UpdatesExistingEmbedding() {
        UUID jobId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        FileEntity file = file(fileId);
        ProcessingJob job = job(jobId, file, ProcessingJob.JobType.EMBEDDING);
        FileEmbedding existing = FileEmbedding.builder()
                .file(file)
                .modelName("openai/clip-vit-large-patch14")
                .modelVersion("1")
                .dimension(768)
                .embedding(new float[768])
                .build();

        when(processingJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.of(file));
        when(fileEmbeddingRepository.findByFileIdAndModelNameAndModelVersion(
                fileId,
                "openai/clip-vit-large-patch14",
                "1"))
                .thenReturn(Optional.of(existing));

        processingJobService.handleEmbeddingResult(
                jobId,
                fileId,
                "openai/clip-vit-large-patch14",
                "1",
                768,
                embeddingVector(768));

        verify(fileEmbeddingRepository).save(existing);
        assertThat(existing.getEmbedding()).hasSize(768);
        assertThat(job.getStatus()).isEqualTo(ProcessingJob.JobStatus.COMPLETED);
    }

    @Test
    void handleProcessingFailure_MarksJobFailed() {
        UUID jobId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        FileEntity file = file(fileId);
        ProcessingJob job = job(jobId, file, ProcessingJob.JobType.CHECKSUM);

        when(processingJobRepository.findById(jobId)).thenReturn(Optional.of(job));

        processingJobService.handleProcessingFailure(jobId, fileId, "error");

        assertThat(job.getStatus()).isEqualTo(ProcessingJob.JobStatus.FAILED);
        assertThat(job.getErrorMessage()).isEqualTo("error");
        verify(applicationMetricsPort).recordJobFailed("CHECKSUM");
    }

    @Test
    void handleEmbeddingResult_RejectsWrongDimension() {
        UUID jobId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class, () -> processingJobService.handleEmbeddingResult(
                jobId,
                fileId,
                "openai/clip-vit-large-patch14",
                "1",
                512,
                embeddingVector(512)));
    }

    private FileEntity file(UUID fileId) {
        return FileEntity.builder()
                .id(fileId)
                .name("file.jpg")
                .mimeType("image/jpeg")
                .size(10L)
                .ownerUser(owner)
                .build();
    }

    private ProcessingJob job(UUID jobId, FileEntity file, ProcessingJob.JobType jobType) {
        ProcessingJob job = new ProcessingJob();
        job.setId(jobId);
        job.setFile(file);
        job.setJobType(jobType);
        job.setStatus(ProcessingJob.JobStatus.PENDING);
        return job;
    }

    private List<Double> embeddingVector(int dimension) {
        return java.util.stream.Stream.generate(() -> 1.0)
                .limit(dimension)
                .toList();
    }
}

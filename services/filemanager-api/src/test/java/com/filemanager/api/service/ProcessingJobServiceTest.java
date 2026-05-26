package com.filemanager.api.service;

import com.filemanager.api.config.AppProperties;
import com.filemanager.api.entity.DuplicateCandidate;
import com.filemanager.api.entity.FileEmbedding;
import com.filemanager.api.entity.FileEntity;
import com.filemanager.api.entity.FileFingerprint;
import com.filemanager.api.entity.ImageFingerprint;
import com.filemanager.api.entity.ProcessingJob;
import com.filemanager.api.entity.User;
import com.filemanager.api.port.ApplicationMetricsPort;
import com.filemanager.api.port.EmbeddingSimilarityCandidate;
import com.filemanager.api.port.EmbeddingSimilaritySearchPort;
import com.filemanager.api.port.EmbeddingSimilaritySearchRequest;
import com.filemanager.api.port.SimilarImageCandidate;
import com.filemanager.api.port.SimilarImageSearchPort;
import com.filemanager.api.port.SimilarImageSearchRequest;
import com.filemanager.api.repository.DuplicateCandidateRepository;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
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
    private DuplicateCandidateRepository duplicateCandidateRepository;
    @Mock
    private ApplicationMetricsPort applicationMetricsPort;
    @Mock
    private SimilarImageSearchPort similarImageSearchPort;
    @Mock
    private EmbeddingSimilaritySearchPort embeddingSimilaritySearchPort;
    @Mock
    private AppProperties appProperties;

    @InjectMocks
    private ProcessingJobService processingJobService;

    private User testUser;
    private static final String SHA256_HASH = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    @BeforeEach
    void setup() {
        AppProperties.Phash phash = new AppProperties.Phash();
        phash.setThreshold(10);
        phash.setMaxCandidates(5000);
        lenient().when(appProperties.getPhash()).thenReturn(phash);
        AppProperties.Embedding embedding = new AppProperties.Embedding();
        embedding.setEnabled(true);
        embedding.setModelName("openai/clip-vit-large-patch14");
        embedding.setModelVersion("1");
        embedding.setDimension(768);
        embedding.setSimilarityThreshold(0.20);
        embedding.setMaxCandidates(5000);
        lenient().when(appProperties.getEmbedding()).thenReturn(embedding);

        testUser = new User();
        testUser.setId(UUID.randomUUID());
    }

    @Test
    void handleChecksumResult_ShouldStoreFingerprintAndDetectDuplicate() {
        UUID jobId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        UUID otherFileId = UUID.randomUUID();
        String sha256 = "E3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B855";
        String normalizedSha256 = sha256.toLowerCase();

        FileEntity file = new FileEntity();
        file.setId(fileId);
        file.setOwnerUser(testUser);

        ProcessingJob job = new ProcessingJob();
        job.setId(jobId);
        job.setFile(file);
        job.setJobType(ProcessingJob.JobType.CHECKSUM);

        when(processingJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.of(file));
        when(fileFingerprintRepository.findByFileIdAndAlgorithm(fileId, FileFingerprint.FingerprintAlgorithm.SHA256))
                .thenReturn(Optional.empty());

        FileEntity otherFile = new FileEntity();
        otherFile.setId(otherFileId);
        otherFile.setOwnerUser(testUser);
        FileFingerprint otherFingerprint = FileFingerprint.builder()
                .file(otherFile)
                .algorithm(FileFingerprint.FingerprintAlgorithm.SHA256)
                .hashValue(normalizedSha256)
                .build();

        when(fileFingerprintRepository.findByAlgorithmAndHashValueAndFileOwnerUserIdAndFileDeletedAtIsNull(
                FileFingerprint.FingerprintAlgorithm.SHA256, normalizedSha256, testUser.getId()))
                .thenReturn(List.of(otherFingerprint));
        
        when(duplicateCandidateRepository.existsBySourceFileIdAndCandidateFileIdAndDetectionMethod(
                fileId, otherFileId, DuplicateCandidate.DetectionMethod.EXACT)).thenReturn(false);
        when(duplicateCandidateRepository.existsBySourceFileIdAndCandidateFileIdAndDetectionMethod(
                otherFileId, fileId, DuplicateCandidate.DetectionMethod.EXACT)).thenReturn(false);

        processingJobService.handleChecksumResult(jobId, fileId, sha256);

        ArgumentCaptor<FileFingerprint> fingerprintCaptor = ArgumentCaptor.forClass(FileFingerprint.class);
        verify(fileFingerprintRepository).save(fingerprintCaptor.capture());
        assertEquals(normalizedSha256, fingerprintCaptor.getValue().getHashValue());
        
        verify(duplicateCandidateRepository).save(any(DuplicateCandidate.class));
        verify(applicationMetricsPort).recordJobCompleted("CHECKSUM");
    }

    @Test
    void handleChecksumResult_ShouldAvoidInverseDuplicates() {
        UUID jobId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        UUID otherFileId = UUID.randomUUID();
        String sha256 = SHA256_HASH;

        FileEntity file = new FileEntity();
        file.setId(fileId);
        file.setOwnerUser(testUser);

        ProcessingJob job = new ProcessingJob();
        job.setId(jobId);
        job.setFile(file);
        job.setJobType(ProcessingJob.JobType.CHECKSUM);

        when(processingJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.of(file));
        
        FileEntity otherFile = new FileEntity();
        otherFile.setId(otherFileId);
        otherFile.setOwnerUser(testUser);
        FileFingerprint otherFingerprint = FileFingerprint.builder()
                .file(otherFile)
                .algorithm(FileFingerprint.FingerprintAlgorithm.SHA256)
                .hashValue(sha256)
                .build();

        when(fileFingerprintRepository.findByAlgorithmAndHashValueAndFileOwnerUserIdAndFileDeletedAtIsNull(
                FileFingerprint.FingerprintAlgorithm.SHA256, sha256, testUser.getId()))
                .thenReturn(List.of(otherFingerprint));
        
        when(duplicateCandidateRepository.existsBySourceFileIdAndCandidateFileIdAndDetectionMethod(
                fileId, otherFileId, DuplicateCandidate.DetectionMethod.EXACT)).thenReturn(false);
        when(duplicateCandidateRepository.existsBySourceFileIdAndCandidateFileIdAndDetectionMethod(
                otherFileId, fileId, DuplicateCandidate.DetectionMethod.EXACT)).thenReturn(true);

        processingJobService.handleChecksumResult(jobId, fileId, sha256);

        verify(duplicateCandidateRepository, never()).save(any(DuplicateCandidate.class));
    }

    @Test
    void handleProcessingFailure_ShouldVerifyOwnershipAndMarkFailed() {
        UUID jobId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        FileEntity file = new FileEntity();
        file.setId(fileId);
        
        ProcessingJob job = new ProcessingJob();
        job.setId(jobId);
        job.setFile(file);
        job.setJobType(ProcessingJob.JobType.CHECKSUM);
        job.setStatus(ProcessingJob.JobStatus.PENDING);

        when(processingJobRepository.findById(jobId)).thenReturn(Optional.of(job));

        processingJobService.handleProcessingFailure(jobId, fileId, "error");

        assertEquals(ProcessingJob.JobStatus.FAILED, job.getStatus());
        verify(processingJobRepository).save(job);
    }

    @Test
    void handlePhashResult_ShouldStoreFingerprintAndDetectDuplicate() {
        UUID jobId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        UUID otherFileId = UUID.randomUUID();
        String phash = "ffffffffffffffff";

        FileEntity file = new FileEntity();
        file.setId(fileId);
        file.setOwnerUser(testUser);

        ProcessingJob job = new ProcessingJob();
        job.setId(jobId);
        job.setFile(file);
        job.setJobType(ProcessingJob.JobType.PHASH);

        when(processingJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.of(file));
        when(imageFingerprintRepository.findByFileId(fileId)).thenReturn(Optional.empty());

        FileEntity otherFile = new FileEntity();
        otherFile.setId(otherFileId);
        otherFile.setOwnerUser(testUser);
        when(similarImageSearchPort.search(any(SimilarImageSearchRequest.class)))
                .thenReturn(List.of(new SimilarImageCandidate(otherFileId, 1)));
        when(fileRepository.findByIdAndDeletedAtIsNull(otherFileId)).thenReturn(Optional.of(otherFile));

        when(duplicateCandidateRepository.existsBySourceFileIdAndCandidateFileIdAndDetectionMethod(
                fileId, otherFileId, DuplicateCandidate.DetectionMethod.PHASH)).thenReturn(false);
        when(duplicateCandidateRepository.existsBySourceFileIdAndCandidateFileIdAndDetectionMethod(
                otherFileId, fileId, DuplicateCandidate.DetectionMethod.PHASH)).thenReturn(false);

        processingJobService.handlePhashResult(jobId, fileId, phash);

        verify(imageFingerprintRepository).save(any(ImageFingerprint.class));
        verify(duplicateCandidateRepository).save(any(DuplicateCandidate.class));

        ArgumentCaptor<SimilarImageSearchRequest> requestCaptor = ArgumentCaptor.forClass(SimilarImageSearchRequest.class);
        verify(similarImageSearchPort).search(requestCaptor.capture());
        assertEquals(fileId, requestCaptor.getValue().sourceFileId());
        assertEquals(testUser.getId(), requestCaptor.getValue().ownerUserId());
        assertEquals(phash, requestCaptor.getValue().phash());
        assertEquals(10, requestCaptor.getValue().threshold());
        assertEquals(5000, requestCaptor.getValue().maxResults());
    }

    @Test
    void handlePhashResult_ShouldNotCreateCandidateWhenOutsideThreshold() {
        UUID jobId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        UUID otherFileId = UUID.randomUUID();
        String phash = "ffffffffffffffff";

        FileEntity file = new FileEntity();
        file.setId(fileId);
        file.setOwnerUser(testUser);

        ProcessingJob job = new ProcessingJob();
        job.setId(jobId);
        job.setFile(file);
        job.setJobType(ProcessingJob.JobType.PHASH);

        when(processingJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.of(file));
        when(imageFingerprintRepository.findByFileId(fileId)).thenReturn(Optional.empty());

        FileEntity otherFile = new FileEntity();
        otherFile.setId(otherFileId);
        otherFile.setOwnerUser(testUser);
        when(similarImageSearchPort.search(any(SimilarImageSearchRequest.class))).thenReturn(List.of());

        processingJobService.handlePhashResult(jobId, fileId, phash);

        verify(duplicateCandidateRepository, never()).save(any());
    }

    @Test
    void handleEmbeddingResult_ShouldStoreEmbeddingAndDetectDuplicate() {
        UUID jobId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        UUID otherFileId = UUID.randomUUID();

        FileEntity file = new FileEntity();
        file.setId(fileId);
        file.setOwnerUser(testUser);

        FileEntity otherFile = new FileEntity();
        otherFile.setId(otherFileId);
        otherFile.setOwnerUser(testUser);

        ProcessingJob job = new ProcessingJob();
        job.setId(jobId);
        job.setFile(file);
        job.setJobType(ProcessingJob.JobType.EMBEDDING);

        List<Double> embedding = embeddingVector(768, 1.0);

        when(processingJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.of(file));
        when(fileEmbeddingRepository.findByFileIdAndModelNameAndModelVersion(
                fileId, "openai/clip-vit-large-patch14", "1"))
                .thenReturn(Optional.empty());
        when(embeddingSimilaritySearchPort.search(any(EmbeddingSimilaritySearchRequest.class)))
                .thenReturn(List.of(new EmbeddingSimilarityCandidate(otherFileId, 0.05)));
        when(fileRepository.findByIdAndDeletedAtIsNull(otherFileId)).thenReturn(Optional.of(otherFile));
        when(duplicateCandidateRepository.existsBySourceFileIdAndCandidateFileIdAndDetectionMethod(
                fileId, otherFileId, DuplicateCandidate.DetectionMethod.EMBEDDING)).thenReturn(false);
        when(duplicateCandidateRepository.existsBySourceFileIdAndCandidateFileIdAndDetectionMethod(
                otherFileId, fileId, DuplicateCandidate.DetectionMethod.EMBEDDING)).thenReturn(false);

        processingJobService.handleEmbeddingResult(
                jobId,
                fileId,
                "openai/clip-vit-large-patch14",
                "1",
                768,
                embedding);

        ArgumentCaptor<FileEmbedding> embeddingCaptor = ArgumentCaptor.forClass(FileEmbedding.class);
        verify(fileEmbeddingRepository).save(embeddingCaptor.capture());
        assertEquals(768, embeddingCaptor.getValue().getDimension());
        assertEquals(768, embeddingCaptor.getValue().getEmbedding().length);

        ArgumentCaptor<EmbeddingSimilaritySearchRequest> requestCaptor =
                ArgumentCaptor.forClass(EmbeddingSimilaritySearchRequest.class);
        verify(embeddingSimilaritySearchPort).search(requestCaptor.capture());
        assertEquals(fileId, requestCaptor.getValue().sourceFileId());
        assertEquals(testUser.getId(), requestCaptor.getValue().ownerUserId());
        assertEquals("openai/clip-vit-large-patch14", requestCaptor.getValue().modelName());
        assertEquals("1", requestCaptor.getValue().modelVersion());
        assertEquals(0.20, requestCaptor.getValue().maxCosineDistance(), 0.0001);
        assertEquals(5000, requestCaptor.getValue().maxResults());

        ArgumentCaptor<DuplicateCandidate> duplicateCaptor = ArgumentCaptor.forClass(DuplicateCandidate.class);
        verify(duplicateCandidateRepository).save(duplicateCaptor.capture());
        assertEquals(DuplicateCandidate.DetectionMethod.EMBEDDING, duplicateCaptor.getValue().getDetectionMethod());
        assertEquals(0.05, duplicateCaptor.getValue().getDistance(), 0.0001);
        assertEquals(0.95, duplicateCaptor.getValue().getConfidenceScore(), 0.0001);
        verify(applicationMetricsPort).recordJobCompleted("EMBEDDING");
    }

    @Test
    void handleEmbeddingResult_ShouldUpdateExistingEmbedding() {
        UUID jobId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        FileEntity file = new FileEntity();
        file.setId(fileId);
        file.setOwnerUser(testUser);

        ProcessingJob job = new ProcessingJob();
        job.setId(jobId);
        job.setFile(file);
        job.setJobType(ProcessingJob.JobType.EMBEDDING);

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
                fileId, "openai/clip-vit-large-patch14", "1"))
                .thenReturn(Optional.of(existing));
        when(embeddingSimilaritySearchPort.search(any(EmbeddingSimilaritySearchRequest.class))).thenReturn(List.of());

        processingJobService.handleEmbeddingResult(
                jobId,
                fileId,
                "openai/clip-vit-large-patch14",
                "1",
                768,
                embeddingVector(768, 2.0));

        verify(fileEmbeddingRepository).save(existing);
        assertEquals(768, existing.getEmbedding().length);
        verify(duplicateCandidateRepository, never()).save(any());
    }

    @Test
    void handleEmbeddingResult_ShouldRejectWrongDimension() {
        UUID jobId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class, () -> processingJobService.handleEmbeddingResult(
                jobId,
                fileId,
                "openai/clip-vit-large-patch14",
                "1",
                512,
                embeddingVector(512, 1.0)));
    }

    private List<Double> embeddingVector(int dimension, double value) {
        return java.util.stream.Stream.generate(() -> value)
                .limit(dimension)
                .toList();
    }
}

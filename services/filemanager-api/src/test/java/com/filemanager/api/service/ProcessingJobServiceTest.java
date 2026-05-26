package com.filemanager.api.service;

import com.filemanager.api.config.AppProperties;
import com.filemanager.api.entity.DuplicateCandidate;
import com.filemanager.api.entity.FileEntity;
import com.filemanager.api.entity.FileFingerprint;
import com.filemanager.api.entity.ImageFingerprint;
import com.filemanager.api.entity.ProcessingJob;
import com.filemanager.api.entity.User;
import com.filemanager.api.port.ApplicationMetricsPort;
import com.filemanager.api.port.SimilarImageCandidate;
import com.filemanager.api.port.SimilarImageSearchPort;
import com.filemanager.api.port.SimilarImageSearchRequest;
import com.filemanager.api.repository.DuplicateCandidateRepository;
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
    private DuplicateCandidateRepository duplicateCandidateRepository;
    @Mock
    private ApplicationMetricsPort applicationMetricsPort;
    @Mock
    private SimilarImageSearchPort similarImageSearchPort;
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
        String otherPhash = "fffffffffffffffe"; // distance = 1

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
        when(fileRepository.getReferenceById(otherFileId)).thenReturn(otherFile);

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
        String otherPhash = "0000000000000000"; // distance = 64

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
}

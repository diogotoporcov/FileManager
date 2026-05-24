package com.filemanager.api.service;

import com.filemanager.api.entity.*;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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
    private FileManagerMetrics fileManagerMetrics;

    @InjectMocks
    private ProcessingJobService processingJobService;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(processingJobService, "phashThreshold", 10);
    }

    @Test
    void handleChecksumResult_ShouldStoreFingerprintAndDetectDuplicate() {
        // Arrange
        UUID jobId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        UUID otherFileId = UUID.randomUUID();
        String sha256 = "E3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B855"; // Uppercase to test normalization
        String normalizedSha256 = sha256.toLowerCase();

        FileEntity file = new FileEntity();
        file.setId(fileId);

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
        FileFingerprint otherFingerprint = FileFingerprint.builder()
                .file(otherFile)
                .algorithm(FileFingerprint.FingerprintAlgorithm.SHA256)
                .hashValue(normalizedSha256)
                .build();

        when(fileFingerprintRepository.findByAlgorithmAndHashValue(FileFingerprint.FingerprintAlgorithm.SHA256, normalizedSha256))
                .thenReturn(List.of(otherFingerprint));
        when(duplicateCandidateRepository.existsBySourceFileIdAndCandidateFileIdAndDetectionMethod(
                fileId, otherFileId, DuplicateCandidate.DetectionMethod.EXACT)).thenReturn(false);
        when(duplicateCandidateRepository.existsBySourceFileIdAndCandidateFileIdAndDetectionMethod(
                otherFileId, fileId, DuplicateCandidate.DetectionMethod.EXACT)).thenReturn(false);

        // Act
        processingJobService.handleChecksumResult(jobId, fileId, sha256);

        // Assert
        ArgumentCaptor<FileFingerprint> fingerprintCaptor = ArgumentCaptor.forClass(FileFingerprint.class);
        verify(fileFingerprintRepository).save(fingerprintCaptor.capture());
        assertEquals(normalizedSha256, fingerprintCaptor.getValue().getHashValue());
        
        ArgumentCaptor<DuplicateCandidate> candidateCaptor = ArgumentCaptor.forClass(DuplicateCandidate.class);
        verify(duplicateCandidateRepository).save(candidateCaptor.capture());
        
        DuplicateCandidate savedCandidate = candidateCaptor.getValue();
        assertEquals(fileId, savedCandidate.getSourceFile().getId());
        assertEquals(otherFileId, savedCandidate.getCandidateFile().getId());

        assertEquals(ProcessingJob.JobStatus.COMPLETED, job.getStatus());
        verify(processingJobRepository).save(job);
        
        verify(fileManagerMetrics).recordJobCompleted("CHECKSUM");
        verify(fileManagerMetrics).recordDuplicateCandidateCreated("EXACT");
    }

    @Test
    void handleChecksumResult_ShouldAvoidInverseDuplicates() {
        // Arrange
        UUID jobId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        UUID otherFileId = UUID.randomUUID();
        String sha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

        FileEntity file = new FileEntity();
        file.setId(fileId);

        ProcessingJob job = new ProcessingJob();
        job.setId(jobId);
        job.setFile(file);
        job.setJobType(ProcessingJob.JobType.CHECKSUM);

        when(processingJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.of(file));
        
        FileEntity otherFile = new FileEntity();
        otherFile.setId(otherFileId);
        FileFingerprint otherFingerprint = FileFingerprint.builder()
                .file(otherFile)
                .algorithm(FileFingerprint.FingerprintAlgorithm.SHA256)
                .hashValue(sha256)
                .build();

        when(fileFingerprintRepository.findByAlgorithmAndHashValue(FileFingerprint.FingerprintAlgorithm.SHA256, sha256))
                .thenReturn(List.of(otherFingerprint));
        
        // Mock that inverse duplicate already exists
        when(duplicateCandidateRepository.existsBySourceFileIdAndCandidateFileIdAndDetectionMethod(
                fileId, otherFileId, DuplicateCandidate.DetectionMethod.EXACT)).thenReturn(false);
        when(duplicateCandidateRepository.existsBySourceFileIdAndCandidateFileIdAndDetectionMethod(
                otherFileId, fileId, DuplicateCandidate.DetectionMethod.EXACT)).thenReturn(true);

        // Act
        processingJobService.handleChecksumResult(jobId, fileId, sha256);

        // Assert
        verify(duplicateCandidateRepository, never()).save(any(DuplicateCandidate.class));
    }

    @Test
    void handleChecksumResult_ShouldThrowOnInvalidHash() {
        assertThrows(IllegalArgumentException.class, () -> 
            processingJobService.handleChecksumResult(UUID.randomUUID(), UUID.randomUUID(), "too-short"));
        assertThrows(IllegalArgumentException.class, () -> 
            processingJobService.handleChecksumResult(UUID.randomUUID(), UUID.randomUUID(), null));
    }

    @Test
    void handleProcessingFailure_ShouldVerifyOwnershipAndMarkFailed() {
        // Arrange
        UUID jobId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        String error = "Something went wrong";
        
        FileEntity file = new FileEntity();
        file.setId(fileId);
        
        ProcessingJob job = new ProcessingJob();
        job.setId(jobId);
        job.setFile(file);
        job.setJobType(ProcessingJob.JobType.CHECKSUM);
        job.setStatus(ProcessingJob.JobStatus.PENDING);

        when(processingJobRepository.findById(jobId)).thenReturn(Optional.of(job));

        // Act
        processingJobService.handleProcessingFailure(jobId, fileId, error);

        // Assert
        assertEquals(ProcessingJob.JobStatus.FAILED, job.getStatus());
        assertEquals(error, job.getErrorMessage());
        verify(processingJobRepository).save(job);
        
        verify(fileManagerMetrics).recordJobFailed("CHECKSUM");
    }

    @Test
    void handleProcessingFailure_ShouldThrowOnOwnershipMismatch() {
        // Arrange
        UUID jobId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        UUID wrongFileId = UUID.randomUUID();
        
        FileEntity file = new FileEntity();
        file.setId(fileId);
        
        ProcessingJob job = new ProcessingJob();
        job.setId(jobId);
        job.setFile(file);
        job.setJobType(ProcessingJob.JobType.CHECKSUM);

        when(processingJobRepository.findById(jobId)).thenReturn(Optional.of(job));

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> 
            processingJobService.handleProcessingFailure(jobId, wrongFileId, "error"));
    }

    @Test
    void handleChecksumResult_ShouldClearErrorMessageOnCompletion() {
        // Arrange
        UUID jobId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        String sha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

        FileEntity file = new FileEntity();
        file.setId(fileId);

        ProcessingJob job = new ProcessingJob();
        job.setId(jobId);
        job.setFile(file);
        job.setJobType(ProcessingJob.JobType.CHECKSUM);
        job.setErrorMessage("Old error");

        when(processingJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.of(file));

        // Act
        processingJobService.handleChecksumResult(jobId, fileId, sha256);

        // Assert
        assertEquals(ProcessingJob.JobStatus.COMPLETED, job.getStatus());
        assertEquals(null, job.getErrorMessage());
        verify(processingJobRepository).save(job);
    }
    @Test
    void handleChecksumResult_ShouldThrowOnJobTypeMismatch() {
        // Arrange
        UUID jobId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        String sha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

        FileEntity file = new FileEntity();
        file.setId(fileId);

        ProcessingJob job = new ProcessingJob();
        job.setId(jobId);
        job.setFile(file);
        job.setJobType(ProcessingJob.JobType.PHASH); // Wrong type

        when(processingJobRepository.findById(jobId)).thenReturn(Optional.of(job));

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () ->
                processingJobService.handleChecksumResult(jobId, fileId, sha256));
    }

    @Test
    void handlePhashResult_ShouldStoreFingerprintAndDetectDuplicate() {
        // Arrange
        UUID jobId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        UUID otherFileId = UUID.randomUUID();
        String phash = "ffffffffffffffff"; // All bits set
        String otherPhash = "fffffffffffffffe"; // One bit different, distance = 1

        FileEntity file = new FileEntity();
        file.setId(fileId);

        ProcessingJob job = new ProcessingJob();
        job.setId(jobId);
        job.setFile(file);
        job.setJobType(ProcessingJob.JobType.PHASH);

        when(processingJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.of(file));
        when(imageFingerprintRepository.findByFileId(fileId)).thenReturn(Optional.empty());

        FileEntity otherFile = new FileEntity();
        otherFile.setId(otherFileId);
        ImageFingerprint otherFingerprint = ImageFingerprint.builder()
                .file(otherFile)
                .phash(otherPhash)
                .build();

        when(imageFingerprintRepository.findAll()).thenReturn(List.of(otherFingerprint));
        when(duplicateCandidateRepository.existsBySourceFileIdAndCandidateFileIdAndDetectionMethod(
                fileId, otherFileId, DuplicateCandidate.DetectionMethod.PHASH)).thenReturn(false);
        when(duplicateCandidateRepository.existsBySourceFileIdAndCandidateFileIdAndDetectionMethod(
                otherFileId, fileId, DuplicateCandidate.DetectionMethod.PHASH)).thenReturn(false);

        // Act
        processingJobService.handlePhashResult(jobId, fileId, phash);

        // Assert
        ArgumentCaptor<ImageFingerprint> fingerprintCaptor = ArgumentCaptor.forClass(ImageFingerprint.class);
        verify(imageFingerprintRepository).save(fingerprintCaptor.capture());
        assertEquals(phash, fingerprintCaptor.getValue().getPhash());
        
        ArgumentCaptor<DuplicateCandidate> candidateCaptor = ArgumentCaptor.forClass(DuplicateCandidate.class);
        verify(duplicateCandidateRepository).save(candidateCaptor.capture());
        
        DuplicateCandidate savedCandidate = candidateCaptor.getValue();
        assertEquals(fileId, savedCandidate.getSourceFile().getId());
        assertEquals(otherFileId, savedCandidate.getCandidateFile().getId());
        assertEquals(DuplicateCandidate.DetectionMethod.PHASH, savedCandidate.getDetectionMethod());
        assertEquals(1.0, savedCandidate.getDistance());
        assertEquals(1.0 - (1.0/64.0), savedCandidate.getConfidenceScore(), 0.0001);

        assertEquals(ProcessingJob.JobStatus.COMPLETED, job.getStatus());
        verify(processingJobRepository).save(job);
        
        verify(fileManagerMetrics).recordJobCompleted("PHASH");
        verify(fileManagerMetrics).recordDuplicateCandidateCreated("PHASH");
    }

    @Test
    void handlePhashResult_ShouldNotCreateCandidateWhenOutsideThreshold() {
        // Arrange
        UUID jobId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        UUID otherFileId = UUID.randomUUID();
        String phash = "ffffffffffffffff";
        String otherPhash = "0000000000000000"; // 64 bits different, distance = 64

        FileEntity file = new FileEntity();
        file.setId(fileId);

        ProcessingJob job = new ProcessingJob();
        job.setId(jobId);
        job.setFile(file);
        job.setJobType(ProcessingJob.JobType.PHASH);

        when(processingJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.of(file));
        when(imageFingerprintRepository.findByFileId(fileId)).thenReturn(Optional.empty());

        FileEntity otherFile = new FileEntity();
        otherFile.setId(otherFileId);
        ImageFingerprint otherFingerprint = ImageFingerprint.builder()
                .file(otherFile)
                .phash(otherPhash)
                .build();

        when(imageFingerprintRepository.findAll()).thenReturn(List.of(otherFingerprint));

        // Act
        processingJobService.handlePhashResult(jobId, fileId, phash);

        // Assert
        verify(duplicateCandidateRepository, never()).save(any());
        assertEquals(ProcessingJob.JobStatus.COMPLETED, job.getStatus());
    }
}

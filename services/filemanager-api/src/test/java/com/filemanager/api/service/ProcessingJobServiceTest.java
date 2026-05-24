package com.filemanager.api.service;

import com.filemanager.api.entity.DuplicateCandidate;
import com.filemanager.api.entity.FileEntity;
import com.filemanager.api.entity.FileFingerprint;
import com.filemanager.api.entity.ProcessingJob;
import com.filemanager.api.repository.DuplicateCandidateRepository;
import com.filemanager.api.repository.FileFingerprintRepository;
import com.filemanager.api.repository.FileRepository;
import com.filemanager.api.repository.ProcessingJobRepository;
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
    private DuplicateCandidateRepository duplicateCandidateRepository;

    @InjectMocks
    private ProcessingJobService processingJobService;

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
        job.setStatus(ProcessingJob.JobStatus.PENDING);

        when(processingJobRepository.findById(jobId)).thenReturn(Optional.of(job));

        // Act
        processingJobService.handleProcessingFailure(jobId, fileId, error);

        // Assert
        assertEquals(ProcessingJob.JobStatus.FAILED, job.getStatus());
        assertEquals(error, job.getErrorMessage());
        verify(processingJobRepository).save(job);
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
}

package com.filemanager.api.service;

import com.filemanager.api.entity.DuplicateCandidate;
import com.filemanager.api.entity.FileEntity;
import com.filemanager.api.entity.FileFingerprint;
import com.filemanager.api.entity.ProcessingJob;
import com.filemanager.api.exception.ResourceNotFoundException;
import com.filemanager.api.repository.DuplicateCandidateRepository;
import com.filemanager.api.repository.FileFingerprintRepository;
import com.filemanager.api.repository.FileRepository;
import com.filemanager.api.repository.ProcessingJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProcessingJobService {

    private final ProcessingJobRepository processingJobRepository;
    private final FileRepository fileRepository;
    private final FileFingerprintRepository fileFingerprintRepository;
    private final DuplicateCandidateRepository duplicateCandidateRepository;

    @Transactional
    public void handleProcessingFailure(UUID jobId, UUID fileId, String errorMessage) {
        log.info("Handling processing failure for job {} (file {}): {}", jobId, fileId, errorMessage);

        ProcessingJob job = processingJobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Processing job not found: " + jobId));

        if (!job.getFile().getId().equals(fileId)) {
            throw new IllegalArgumentException("Job file ID mismatch");
        }

        job.setStatus(ProcessingJob.JobStatus.FAILED);
        job.setErrorMessage(errorMessage);
        processingJobRepository.save(job);
    }

    @Transactional
    public void handleChecksumResult(UUID jobId, UUID fileId, String sha256) {
        log.info("Handling checksum result for job {}: {}", jobId, sha256);

        if (sha256 == null || !sha256.matches("^[a-fA-F0-9]{64}$")) {
            throw new IllegalArgumentException("Invalid SHA-256 format");
        }

        String normalizedSha256 = sha256.toLowerCase(Locale.ROOT);

        ProcessingJob job = processingJobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Processing job not found: " + jobId));

        if (!job.getFile().getId().equals(fileId)) {
            throw new IllegalArgumentException("Job file ID mismatch");
        }

        FileEntity file = fileRepository.findByIdAndDeletedAtIsNull(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found or deleted: " + fileId));

        // 1. Store or update SHA-256 fingerprint
        fileFingerprintRepository.findByFileIdAndAlgorithm(file.getId(), FileFingerprint.FingerprintAlgorithm.SHA256)
                .ifPresentOrElse(
                        f -> {
                            f.setHashValue(normalizedSha256);
                            fileFingerprintRepository.save(f);
                        },
                        () -> {
                            FileFingerprint fingerprint = FileFingerprint.builder()
                                    .file(file)
                                    .algorithm(FileFingerprint.FingerprintAlgorithm.SHA256)
                                    .hashValue(normalizedSha256)
                                    .build();
                            fileFingerprintRepository.save(fingerprint);
                        }
                );

        // 2. Search for existing files with the same SHA-256
        List<FileFingerprint> existingFingerprints = fileFingerprintRepository.findByAlgorithmAndHashValue(
                FileFingerprint.FingerprintAlgorithm.SHA256, normalizedSha256);

        for (FileFingerprint existing : existingFingerprints) {
            FileEntity candidateFile = existing.getFile();

            // Skip self and deleted files
            if (candidateFile.getId().equals(file.getId()) || candidateFile.getDeletedAt() != null) {
                continue;
            }

            // Create duplicate candidate row if it doesn't exist in either direction
            boolean exists = duplicateCandidateRepository.existsBySourceFileIdAndCandidateFileIdAndDetectionMethod(
                    file.getId(), candidateFile.getId(), DuplicateCandidate.DetectionMethod.EXACT)
                    || duplicateCandidateRepository.existsBySourceFileIdAndCandidateFileIdAndDetectionMethod(
                    candidateFile.getId(), file.getId(), DuplicateCandidate.DetectionMethod.EXACT);

            if (!exists) {
                DuplicateCandidate candidate = DuplicateCandidate.builder()
                        .sourceFile(file)
                        .candidateFile(candidateFile)
                        .detectionMethod(DuplicateCandidate.DetectionMethod.EXACT)
                        .distance(0.0)
                        .confidenceScore(1.0)
                        .status(DuplicateCandidate.CandidateStatus.PENDING)
                        .build();

                duplicateCandidateRepository.save(candidate);
                log.info("Created exact duplicate candidate: {} and {}", file.getId(), candidateFile.getId());
            }
        }

        // 3. Mark job as COMPLETED
        job.setStatus(ProcessingJob.JobStatus.COMPLETED);
        job.setErrorMessage(null);
        processingJobRepository.save(job);
    }
}

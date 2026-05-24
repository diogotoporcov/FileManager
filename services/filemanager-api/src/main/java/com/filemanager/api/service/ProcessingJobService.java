package com.filemanager.api.service;

import com.filemanager.api.config.AppProperties;
import com.filemanager.api.entity.*;
import com.filemanager.api.exception.ResourceNotFoundException;
import com.filemanager.api.repository.DuplicateCandidateRepository;
import com.filemanager.api.repository.FileFingerprintRepository;
import com.filemanager.api.repository.FileRepository;
import com.filemanager.api.repository.ImageFingerprintRepository;
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
    private final ImageFingerprintRepository imageFingerprintRepository;
    private final DuplicateCandidateRepository duplicateCandidateRepository;
    private final FileManagerMetrics fileManagerMetrics;
    private final AppProperties appProperties;

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

        fileManagerMetrics.recordJobFailed(job.getJobType().name());
    }

    @Transactional
    public void handleChecksumResult(UUID jobId, UUID fileId, String sha256) {
        log.info("Handling checksum result for job {}: {}", jobId, sha256);

        validateChecksumFormat(sha256);
        String normalizedSha256 = sha256.toLowerCase(Locale.ROOT);

        ProcessingJob job = getAndValidateJob(jobId, fileId, ProcessingJob.JobType.CHECKSUM);
        FileEntity file = getActiveFile(fileId);

        // Record or update SHA-256 fingerprint.
        updateFileFingerprint(file, normalizedSha256);

        // Identify exact-match duplicates within the same ownership scope.
        List<FileFingerprint> existingFingerprints = findExistingChecksumDuplicates(file, normalizedSha256);

        for (FileFingerprint existing : existingFingerprints) {
            FileEntity candidateFile = existing.getFile();

            if (isEligibleDuplicate(file, candidateFile)) {
                createDuplicateCandidate(file, candidateFile, DuplicateCandidate.DetectionMethod.EXACT, 0.0, 1.0);
            }
        }

        completeJob(job);
    }

    @Transactional
    public void handlePhashResult(UUID jobId, UUID fileId, String phash) {
        log.info("Handling pHash result for job {}: {}", jobId, phash);

        validatePhashFormat(phash);
        String normalizedPhash = phash.toLowerCase(Locale.ROOT);

        ProcessingJob job = getAndValidateJob(jobId, fileId, ProcessingJob.JobType.PHASH);
        FileEntity file = getActiveFile(fileId);

        // Record or update pHash fingerprint.
        updateImageFingerprint(file, normalizedPhash);

        // Identify similar images within the same ownership scope.
        List<ImageFingerprint> allFingerprints = findExistingPhashDuplicates(file);

        for (ImageFingerprint existing : allFingerprints) {
            FileEntity candidateFile = existing.getFile();

            if (isEligibleDuplicate(file, candidateFile)) {
                int distance = calculateHammingDistance(normalizedPhash, existing.getPhash());

                if (distance <= appProperties.getPhash().getThreshold()) {
                    double confidenceScore = 1.0 - ((double) distance / 64.0);
                    createDuplicateCandidate(file, candidateFile, DuplicateCandidate.DetectionMethod.PHASH, (double) distance, confidenceScore);
                }
            }
        }

        completeJob(job);
    }

    private void validateChecksumFormat(String sha256) {
        if (sha256 == null || !sha256.matches("^[a-fA-F0-9]{64}$")) {
            throw new IllegalArgumentException("Invalid SHA-256 format");
        }
    }

    private void validatePhashFormat(String phash) {
        if (phash == null || !phash.matches("^[a-fA-F0-9]{16}$")) {
            throw new IllegalArgumentException("Invalid pHash format");
        }
    }

    private ProcessingJob getAndValidateJob(UUID jobId, UUID fileId, ProcessingJob.JobType expectedType) {
        ProcessingJob job = processingJobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Processing job not found: " + jobId));

        if (!job.getFile().getId().equals(fileId)) {
            throw new IllegalArgumentException("Job file ID mismatch");
        }

        if (job.getJobType() != expectedType) {
            throw new IllegalArgumentException("Job type mismatch: expected " + expectedType);
        }
        return job;
    }

    private FileEntity getActiveFile(UUID fileId) {
        return fileRepository.findByIdAndDeletedAtIsNull(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found or deleted: " + fileId));
    }

    private void updateFileFingerprint(FileEntity file, String hashValue) {
        fileFingerprintRepository.findByFileIdAndAlgorithm(file.getId(), FileFingerprint.FingerprintAlgorithm.SHA256)
                .ifPresentOrElse(
                        f -> {
                            f.setHashValue(hashValue);
                            fileFingerprintRepository.save(f);
                        },
                        () -> {
                            FileFingerprint fingerprint = FileFingerprint.builder()
                                    .file(file)
                                    .algorithm(FileFingerprint.FingerprintAlgorithm.SHA256)
                                    .hashValue(hashValue)
                                    .build();
                            fileFingerprintRepository.save(fingerprint);
                        }
                );
    }

    private void updateImageFingerprint(FileEntity file, String phash) {
        imageFingerprintRepository.findByFileId(file.getId())
                .ifPresentOrElse(
                        f -> {
                            f.setPhash(phash);
                            imageFingerprintRepository.save(f);
                        },
                        () -> {
                            ImageFingerprint fingerprint = ImageFingerprint.builder()
                                    .file(file)
                                    .phash(phash)
                                    .build();
                            imageFingerprintRepository.save(fingerprint);
                        }
                );
    }

    private boolean isEligibleDuplicate(FileEntity file, FileEntity candidateFile) {
        return !candidateFile.getId().equals(file.getId()) && candidateFile.getDeletedAt() == null;
    }

    private void createDuplicateCandidate(FileEntity file, FileEntity candidateFile, DuplicateCandidate.DetectionMethod method, Double distance, Double confidenceScore) {
        boolean exists = duplicateCandidateRepository.existsBySourceFileIdAndCandidateFileIdAndDetectionMethod(
                file.getId(), candidateFile.getId(), method)
                || duplicateCandidateRepository.existsBySourceFileIdAndCandidateFileIdAndDetectionMethod(
                candidateFile.getId(), file.getId(), method);

        if (!exists) {
            DuplicateCandidate candidate = DuplicateCandidate.builder()
                    .sourceFile(file)
                    .candidateFile(candidateFile)
                    .detectionMethod(method)
                    .distance(distance)
                    .confidenceScore(confidenceScore)
                    .status(DuplicateCandidate.CandidateStatus.PENDING)
                    .build();

            duplicateCandidateRepository.save(candidate);
            fileManagerMetrics.recordDuplicateCandidateCreated(method.name());
            log.info("Created {} duplicate candidate: {} and {}", method, file.getId(), candidateFile.getId());
        }
    }

    private void completeJob(ProcessingJob job) {
        job.setStatus(ProcessingJob.JobStatus.COMPLETED);
        job.setErrorMessage(null);
        processingJobRepository.save(job);
        fileManagerMetrics.recordJobCompleted(job.getJobType().name());
    }

    private List<FileFingerprint> findExistingChecksumDuplicates(FileEntity file, String hashValue) {
        if (file.getOwnerUser() != null) {
            return fileFingerprintRepository.findByAlgorithmAndHashValueAndFileOwnerUserIdAndFileDeletedAtIsNull(
                    FileFingerprint.FingerprintAlgorithm.SHA256, hashValue, file.getOwnerUser().getId());
        } else if (file.getOwnerOrganization() != null) {
            return fileFingerprintRepository.findByAlgorithmAndHashValueAndFileOwnerOrganizationIdAndFileDeletedAtIsNull(
                    FileFingerprint.FingerprintAlgorithm.SHA256, hashValue, file.getOwnerOrganization().getId());
        }
        return List.of();
    }

    private List<ImageFingerprint> findExistingPhashDuplicates(FileEntity file) {
        if (file.getOwnerUser() != null) {
            return imageFingerprintRepository.findByFileOwnerUserIdAndFileDeletedAtIsNull(file.getOwnerUser().getId());
        } else if (file.getOwnerOrganization() != null) {
            return imageFingerprintRepository.findByFileOwnerOrganizationIdAndFileDeletedAtIsNull(file.getOwnerOrganization().getId());
        }
        return List.of();
    }

    private int calculateHammingDistance(String h1, String h2) {
        long l1 = Long.parseUnsignedLong(h1, 16);
        long l2 = Long.parseUnsignedLong(h2, 16);
        return Long.bitCount(l1 ^ l2);
    }
}

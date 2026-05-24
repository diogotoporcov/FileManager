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

        if (sha256 == null || !sha256.matches("^[a-fA-F0-9]{64}$")) {
            throw new IllegalArgumentException("Invalid SHA-256 format");
        }

        String normalizedSha256 = sha256.toLowerCase(Locale.ROOT);

        ProcessingJob job = processingJobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Processing job not found: " + jobId));

        if (!job.getFile().getId().equals(fileId)) {
            throw new IllegalArgumentException("Job file ID mismatch");
        }

        if (job.getJobType() != ProcessingJob.JobType.CHECKSUM) {
            throw new IllegalArgumentException("Job type mismatch: expected CHECKSUM");
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

        // 2. Search for existing files with the same SHA-256 within the same ownership boundary
        List<FileFingerprint> existingFingerprints = findExistingChecksumDuplicates(file, normalizedSha256);

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
                    fileManagerMetrics.recordDuplicateCandidateCreated(DuplicateCandidate.DetectionMethod.EXACT.name());
                    log.info("Created exact duplicate candidate: {} and {}", file.getId(), candidateFile.getId());
                }
        }

        // 3. Mark job as COMPLETED
        job.setStatus(ProcessingJob.JobStatus.COMPLETED);
        job.setErrorMessage(null);
        processingJobRepository.save(job);
        fileManagerMetrics.recordJobCompleted(job.getJobType().name());
    }

    @Transactional
    public void handlePhashResult(UUID jobId, UUID fileId, String phash) {
        log.info("Handling pHash result for job {}: {}", jobId, phash);

        if (phash == null || !phash.matches("^[a-fA-F0-9]{16}$")) {
            throw new IllegalArgumentException("Invalid pHash format");
        }

        String normalizedPhash = phash.toLowerCase(Locale.ROOT);

        ProcessingJob job = processingJobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Processing job not found: " + jobId));

        if (!job.getFile().getId().equals(fileId)) {
            throw new IllegalArgumentException("Job file ID mismatch");
        }

        if (job.getJobType() != ProcessingJob.JobType.PHASH) {
            throw new IllegalArgumentException("Job type mismatch: expected PHASH");
        }

        FileEntity file = fileRepository.findByIdAndDeletedAtIsNull(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found or deleted: " + fileId));

        // 1. Store or update pHash fingerprint
        imageFingerprintRepository.findByFileId(file.getId())
                .ifPresentOrElse(
                        f -> {
                            f.setPhash(normalizedPhash);
                            imageFingerprintRepository.save(f);
                        },
                        () -> {
                            ImageFingerprint fingerprint = ImageFingerprint.builder()
                                    .file(file)
                                    .phash(normalizedPhash)
                                    .build();
                            imageFingerprintRepository.save(fingerprint);
                        }
                );

        // 2. Search for existing image fingerprints to compare within the same ownership boundary
        List<ImageFingerprint> allFingerprints = findExistingPhashDuplicates(file);

        for (ImageFingerprint existing : allFingerprints) {
            FileEntity candidateFile = existing.getFile();

            // Skip self and deleted files
            if (candidateFile.getId().equals(file.getId()) || candidateFile.getDeletedAt() != null) {
                continue;
            }

            int distance = calculateHammingDistance(normalizedPhash, existing.getPhash());

            if (distance <= appProperties.getPhash().getThreshold()) {
                // Create duplicate candidate row if it doesn't exist in either direction for PHASH
                boolean exists = duplicateCandidateRepository.existsBySourceFileIdAndCandidateFileIdAndDetectionMethod(
                        file.getId(), candidateFile.getId(), DuplicateCandidate.DetectionMethod.PHASH)
                        || duplicateCandidateRepository.existsBySourceFileIdAndCandidateFileIdAndDetectionMethod(
                        candidateFile.getId(), file.getId(), DuplicateCandidate.DetectionMethod.PHASH);

                if (!exists) {
                    double confidenceScore = 1.0 - ((double) distance / 64.0);
                    DuplicateCandidate candidate = DuplicateCandidate.builder()
                            .sourceFile(file)
                            .candidateFile(candidateFile)
                            .detectionMethod(DuplicateCandidate.DetectionMethod.PHASH)
                            .distance((double) distance)
                            .confidenceScore(confidenceScore)
                            .status(DuplicateCandidate.CandidateStatus.PENDING)
                            .build();

                    duplicateCandidateRepository.save(candidate);
                    fileManagerMetrics.recordDuplicateCandidateCreated(DuplicateCandidate.DetectionMethod.PHASH.name());
                    log.info("Created pHash duplicate candidate: {} and {} (distance: {})", file.getId(), candidateFile.getId(), distance);
                }
            }
        }

        // 3. Mark job as COMPLETED
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

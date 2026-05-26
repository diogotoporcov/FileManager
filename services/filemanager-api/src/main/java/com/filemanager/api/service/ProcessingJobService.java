package com.filemanager.api.service;

import com.filemanager.api.config.AppProperties;
import com.filemanager.api.entity.DuplicateCandidate;
import com.filemanager.api.entity.FileEntity;
import com.filemanager.api.entity.FileFingerprint;
import com.filemanager.api.entity.ImageFingerprint;
import com.filemanager.api.entity.ProcessingJob;
import com.filemanager.api.exception.ResourceNotFoundException;
import com.filemanager.api.port.ApplicationMetricsPort;
import com.filemanager.api.port.SimilarImageCandidate;
import com.filemanager.api.port.SimilarImageSearchPort;
import com.filemanager.api.port.SimilarImageSearchRequest;
import com.filemanager.api.repository.DuplicateCandidateRepository;
import com.filemanager.api.repository.FileFingerprintRepository;
import com.filemanager.api.repository.FileRepository;
import com.filemanager.api.repository.ImageFingerprintRepository;
import com.filemanager.api.repository.ProcessingJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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
    private final ApplicationMetricsPort applicationMetricsPort;
    private final SimilarImageSearchPort similarImageSearchPort;
    private final AppProperties appProperties;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateExternalJobId(UUID jobId, String externalJobId) {
        processingJobRepository.findById(jobId).ifPresent(job -> {
            job.setExternalJobId(externalJobId);
            processingJobRepository.save(job);
            log.info("Updated job {} with external ID: {}", jobId, externalJobId);
        });
    }

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

        applicationMetricsPort.recordJobFailed(job.getJobType().name());
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
            createExactDuplicateCandidate(file, candidateFile);
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

        List<SimilarImageCandidate> similarImages = findSimilarPhashCandidates(file, normalizedPhash);
        int maxCandidates = appProperties.getPhash().getMaxCandidates();
        if (similarImages.size() == maxCandidates) {
            log.warn("pHash match cap reached for file {} at {} candidates", fileId, maxCandidates);
        }

        for (SimilarImageCandidate candidate : similarImages) {
            double confidenceScore = 1.0 - ((double) candidate.distance() / 64.0);
            createDuplicateCandidate(file, candidate.fileId(), DuplicateCandidate.DetectionMethod.PHASH,
                    (double) candidate.distance(), confidenceScore);
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

    private void createExactDuplicateCandidate(FileEntity file, FileEntity candidateFile) {
        if (!isEligibleDuplicate(file, candidateFile)) {
            return;
        }

        createDuplicateCandidate(file, candidateFile.getId(), DuplicateCandidate.DetectionMethod.EXACT, 0.0, 1.0);
    }

    private void createDuplicateCandidate(FileEntity file, UUID candidateFileId, DuplicateCandidate.DetectionMethod method, Double distance, Double confidenceScore) {
        if (candidateFileId.equals(file.getId())) {
            return;
        }

        boolean exists = duplicateCandidateRepository.existsBySourceFileIdAndCandidateFileIdAndDetectionMethod(
                file.getId(), candidateFileId, method)
                || duplicateCandidateRepository.existsBySourceFileIdAndCandidateFileIdAndDetectionMethod(
                candidateFileId, file.getId(), method);

        if (!exists) {
            DuplicateCandidate candidate = DuplicateCandidate.builder()
                    .sourceFile(file)
                    .candidateFile(fileRepository.getReferenceById(candidateFileId))
                    .detectionMethod(method)
                    .distance(distance)
                    .confidenceScore(confidenceScore)
                    .status(DuplicateCandidate.CandidateStatus.PENDING)
                    .build();

            duplicateCandidateRepository.save(candidate);
            applicationMetricsPort.recordDuplicateCandidateCreated(method.name());
            log.info("Created {} duplicate candidate: {} and {}", method, file.getId(), candidateFileId);
        }
    }

    private void completeJob(ProcessingJob job) {
        job.setStatus(ProcessingJob.JobStatus.COMPLETED);
        job.setErrorMessage(null);
        processingJobRepository.save(job);
        applicationMetricsPort.recordJobCompleted(job.getJobType().name());
    }

    private List<FileFingerprint> findExistingChecksumDuplicates(FileEntity file, String hashValue) {
        if (file.getOwnerUser() != null) {
            return fileFingerprintRepository.findByAlgorithmAndHashValueAndFileOwnerUserIdAndFileDeletedAtIsNull(
                    FileFingerprint.FingerprintAlgorithm.SHA256, hashValue, file.getOwnerUser().getId());
        }

        if (file.getOwnerOrganization() != null) {
            return fileFingerprintRepository.findByAlgorithmAndHashValueAndFileOwnerOrganizationIdAndFileDeletedAtIsNull(
                    FileFingerprint.FingerprintAlgorithm.SHA256, hashValue, file.getOwnerOrganization().getId());
        }

        return List.of();
    }

    private List<SimilarImageCandidate> findSimilarPhashCandidates(FileEntity file, String phash) {
        if (file.getOwnerUser() != null) {
            return similarImageSearchPort.search(new SimilarImageSearchRequest(
                    file.getId(),
                    file.getOwnerUser().getId(),
                    null,
                    phash,
                    appProperties.getPhash().getThreshold(),
                    appProperties.getPhash().getMaxCandidates()));
        }

        if (file.getOwnerOrganization() != null) {
            return similarImageSearchPort.search(new SimilarImageSearchRequest(
                    file.getId(),
                    null,
                    file.getOwnerOrganization().getId(),
                    phash,
                    appProperties.getPhash().getThreshold(),
                    appProperties.getPhash().getMaxCandidates()));
        }
        return List.of();
    }
}

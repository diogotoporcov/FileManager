package com.filemanager.api.service;

import com.filemanager.api.config.AppProperties;
import com.filemanager.api.entity.DuplicateCandidate;
import com.filemanager.api.entity.FileEmbedding;
import com.filemanager.api.entity.FileEntity;
import com.filemanager.api.entity.FileFingerprint;
import com.filemanager.api.entity.ImageFingerprint;
import com.filemanager.api.entity.ProcessingJob;
import com.filemanager.api.exception.ResourceNotFoundException;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProcessingJobService {

    private final ProcessingJobRepository processingJobRepository;
    private final FileRepository fileRepository;
    private final FileFingerprintRepository fileFingerprintRepository;
    private final ImageFingerprintRepository imageFingerprintRepository;
    private final FileEmbeddingRepository fileEmbeddingRepository;
    private final DuplicateCandidateRepository duplicateCandidateRepository;
    private final ApplicationMetricsPort applicationMetricsPort;
    private final SimilarImageSearchPort similarImageSearchPort;
    private final EmbeddingSimilaritySearchPort embeddingSimilaritySearchPort;
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

    @Transactional
    public void handleEmbeddingResult(
            UUID jobId,
            UUID fileId,
            String modelName,
            String modelVersion,
            Integer dimension,
            List<Double> embedding) {
        log.info("Handling embedding result for job {} and file {}", jobId, fileId);

        AppProperties.Embedding embeddingProperties = appProperties.getEmbedding();
        validateEmbeddingRequest(modelName, modelVersion, dimension, embedding, embeddingProperties);

        ProcessingJob job = getAndValidateJob(jobId, fileId, ProcessingJob.JobType.EMBEDDING);
        FileEntity file = getActiveFile(fileId);

        float[] normalizedEmbedding = normalizeEmbedding(embedding);
        updateFileEmbedding(file, modelName, modelVersion, dimension, normalizedEmbedding);

        List<EmbeddingSimilarityCandidate> similarImages = findSimilarEmbeddingCandidates(
                file,
                modelName,
                modelVersion);
        int maxCandidates = embeddingProperties.getMaxCandidates();
        if (similarImages.size() == maxCandidates) {
            log.warn("Embedding match cap reached for file {} at {} candidates", fileId, maxCandidates);
        }

        for (EmbeddingSimilarityCandidate candidate : similarImages) {
            double confidenceScore = Math.clamp(1.0 - candidate.distance(), 0.0, 1.0);
            createDuplicateCandidate(
                    file,
                    candidate.fileId(),
                    DuplicateCandidate.DetectionMethod.EMBEDDING,
                    candidate.distance(),
                    confidenceScore);
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

    private void validateEmbeddingRequest(
            String modelName,
            String modelVersion,
            Integer dimension,
            List<Double> embedding,
            AppProperties.Embedding embeddingProperties) {
        if (!embeddingProperties.isEnabled()) {
            throw new IllegalArgumentException("Embedding processing is disabled");
        }
        if (!Objects.equals(modelName, embeddingProperties.getModelName())) {
            throw new IllegalArgumentException("Embedding model name mismatch");
        }
        if (!Objects.equals(modelVersion, embeddingProperties.getModelVersion())) {
            throw new IllegalArgumentException("Embedding model version mismatch");
        }
        if (dimension == null || dimension != embeddingProperties.getDimension()) {
            throw new IllegalArgumentException("Embedding dimension mismatch");
        }
        if (embedding == null || embedding.isEmpty()) {
            throw new IllegalArgumentException("Embedding must not be empty");
        }
        if (embedding.size() != dimension) {
            throw new IllegalArgumentException("Embedding length must match dimension");
        }
        if (embedding.stream().anyMatch(value -> value == null || !Double.isFinite(value))) {
            throw new IllegalArgumentException("Embedding values must be finite");
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

    private void updateFileEmbedding(
            FileEntity file,
            String modelName,
            String modelVersion,
            Integer dimension,
            float[] embedding) {
        fileEmbeddingRepository.findByFileIdAndModelNameAndModelVersion(file.getId(), modelName, modelVersion)
                .ifPresentOrElse(
                        existing -> {
                            existing.setDimension(dimension);
                            existing.setEmbedding(embedding);
                            fileEmbeddingRepository.save(existing);
                        },
                        () -> {
                            FileEmbedding fileEmbedding = FileEmbedding.builder()
                                    .file(file)
                                    .modelName(modelName)
                                    .modelVersion(modelVersion)
                                    .dimension(dimension)
                                    .embedding(embedding)
                                    .build();
                            fileEmbeddingRepository.save(fileEmbedding);
                        }
                );
    }

    private float[] normalizeEmbedding(List<Double> embedding) {
        double squaredNorm = 0.0;
        for (Double value : embedding) {
            squaredNorm += value * value;
        }
        double norm = Math.sqrt(squaredNorm);
        if (!Double.isFinite(norm) || norm == 0.0) {
            throw new IllegalArgumentException("Embedding norm must be finite and non-zero");
        }

        float[] normalized = new float[embedding.size()];
        for (int i = 0; i < embedding.size(); i++) {
            normalized[i] = (float) (embedding.get(i) / norm);
        }
        return normalized;
    }

    private boolean isEligibleDuplicate(FileEntity file, FileEntity candidateFile) {
        return !candidateFile.getId().equals(file.getId())
                && candidateFile.getDeletedAt() == null
                && belongsToSameOwnerScope(file, candidateFile);
    }

    private void createExactDuplicateCandidate(FileEntity file, FileEntity candidateFile) {
        if (!isEligibleDuplicate(file, candidateFile)) {
            return;
        }

        createDuplicateCandidate(file, candidateFile, DuplicateCandidate.DetectionMethod.EXACT, 0.0, 1.0);
    }

    private void createDuplicateCandidate(FileEntity file, UUID candidateFileId, DuplicateCandidate.DetectionMethod method, Double distance, Double confidenceScore) {
        if (candidateFileId.equals(file.getId())) {
            return;
        }

        fileRepository.findByIdAndDeletedAtIsNull(candidateFileId)
                .filter(candidateFile -> isEligibleDuplicate(file, candidateFile))
                .ifPresent(candidateFile -> createDuplicateCandidate(file, candidateFile, method, distance, confidenceScore));
    }

    private void createDuplicateCandidate(FileEntity file, FileEntity candidateFile, DuplicateCandidate.DetectionMethod method, Double distance, Double confidenceScore) {
        if (!isEligibleDuplicate(file, candidateFile)) {
            return;
        }

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
            applicationMetricsPort.recordDuplicateCandidateCreated(method.name());
            log.info("Created {} duplicate candidate: {} and {}", method, file.getId(), candidateFile.getId());
        }
    }

    private boolean belongsToSameOwnerScope(FileEntity file, FileEntity candidateFile) {
        if (file.getOwnerUser() != null) {
            return candidateFile.getOwnerUser() != null
                    && file.getOwnerUser().getId().equals(candidateFile.getOwnerUser().getId());
        }
        if (file.getOwnerOrganization() != null) {
            return candidateFile.getOwnerOrganization() != null
                    && file.getOwnerOrganization().getId().equals(candidateFile.getOwnerOrganization().getId());
        }
        return false;
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

    private List<EmbeddingSimilarityCandidate> findSimilarEmbeddingCandidates(
            FileEntity file,
            String modelName,
            String modelVersion) {
        AppProperties.Embedding embeddingProperties = appProperties.getEmbedding();
        if (file.getOwnerUser() != null) {
            return embeddingSimilaritySearchPort.search(new EmbeddingSimilaritySearchRequest(
                    file.getId(),
                    file.getOwnerUser().getId(),
                    null,
                    modelName,
                    modelVersion,
                    embeddingProperties.getSimilarityThreshold(),
                    embeddingProperties.getMaxCandidates()));
        }

        if (file.getOwnerOrganization() != null) {
            return embeddingSimilaritySearchPort.search(new EmbeddingSimilaritySearchRequest(
                    file.getId(),
                    null,
                    file.getOwnerOrganization().getId(),
                    modelName,
                    modelVersion,
                    embeddingProperties.getSimilarityThreshold(),
                    embeddingProperties.getMaxCandidates()));
        }
        return List.of();
    }
}

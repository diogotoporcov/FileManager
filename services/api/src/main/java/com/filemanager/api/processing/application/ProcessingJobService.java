package com.filemanager.api.processing.application;

import com.filemanager.api.config.AppProperties;
import com.filemanager.api.duplicate.application.ExactDuplicateGroupMaintenanceService;
import com.filemanager.api.exception.ResourceNotFoundException;
import com.filemanager.api.file.domain.FileEntity;
import com.filemanager.api.file.persistence.FileRepository;
import com.filemanager.api.observability.application.FileManagerMetrics;
import com.filemanager.api.processing.web.result.AudioAnalysisResultRequest;
import com.filemanager.api.processing.domain.ProcessingJob;
import com.filemanager.api.processing.domain.result.AudioFingerprint;
import com.filemanager.api.processing.domain.result.FileEmbedding;
import com.filemanager.api.processing.domain.result.FileFingerprint;
import com.filemanager.api.processing.domain.result.ImageFingerprint;
import com.filemanager.api.processing.persistence.ProcessingJobRepository;
import com.filemanager.api.processing.persistence.result.AudioFingerprintRepository;
import com.filemanager.api.processing.persistence.result.FileEmbeddingRepository;
import com.filemanager.api.processing.persistence.result.FileFingerprintRepository;
import com.filemanager.api.processing.persistence.result.ImageFingerprintRepository;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProcessingJobService {
    private final ProcessingJobRepository processingJobRepository;
    private final FileRepository fileRepository;
    private final FileFingerprintRepository fileFingerprintRepository;
    private final ImageFingerprintRepository imageFingerprintRepository;
    private final FileEmbeddingRepository fileEmbeddingRepository;
    private final AudioFingerprintRepository audioFingerprintRepository;
    private final FileManagerMetrics fileManagerMetrics;
    private final AppProperties appProperties;
    private final ExactDuplicateGroupMaintenanceService exactDuplicateGroupMaintenanceService;

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

        fileManagerMetrics.recordJobFailed(job.getJobType().name());
    }

    @Transactional
    public void handleChecksumResult(UUID jobId, UUID fileId, String sha256) {
        log.info("Handling checksum result for job {}: {}", jobId, sha256);

        validateChecksumFormat(sha256);
        String normalizedSha256 = sha256.toLowerCase(Locale.ROOT);

        ProcessingJob job = getAndValidateJob(jobId, fileId, ProcessingJob.JobType.CHECKSUM);
        FileEntity file = getActiveFile(fileId);

        String oldHashValue = updateFileFingerprint(file, normalizedSha256);
        exactDuplicateGroupMaintenanceService.refreshAfterFingerprintChange(
                file.getOwnerUser().getId(),
                FileFingerprint.FingerprintAlgorithm.SHA256,
                oldHashValue,
                normalizedSha256);
        completeJob(job);
    }

    @Transactional
    public void handlePhashResult(UUID jobId, UUID fileId, String phash) {
        log.info("Handling pHash result for job {}: {}", jobId, phash);

        validatePhashFormat(phash);
        String normalizedPhash = phash.toLowerCase(Locale.ROOT);

        ProcessingJob job = getAndValidateJob(jobId, fileId, ProcessingJob.JobType.PHASH);
        FileEntity file = getActiveFile(fileId);

        updateImageFingerprint(file, normalizedPhash);
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
        completeJob(job);
    }

    @Transactional
    public void handleAudioAnalysisResult(UUID jobId, AudioAnalysisResultRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Audio analysis result request must not be null");
        }
        log.info("Handling audio analysis result for job {} and file {}", jobId, request.getFileId());

        validateAudioAnalysisRequest(request);

        ProcessingJob job = getAndValidateJob(jobId, request.getFileId(), ProcessingJob.JobType.AUDIO_ANALYSIS);
        FileEntity file = getActiveFile(request.getFileId());

        updateAudioFingerprint(file, request);
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
        validateEmbeddingModel(modelName, modelVersion, dimension, embeddingProperties);
        validateEmbeddingValues(dimension, embedding);
    }

    private void validateAudioAnalysisRequest(AudioAnalysisResultRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Audio analysis result request must not be null");
        }

        if (request.getDurationMs() == null || request.getDurationMs() <= 0) {
            throw new IllegalArgumentException("Audio duration must be positive");
        }

        if (request.getSampleRate() == null || request.getSampleRate() <= 0) {
            throw new IllegalArgumentException("Audio sample rate must be positive");
        }

        if (request.getChannels() == null || request.getChannels() <= 0) {
            throw new IllegalArgumentException("Audio channels must be positive");
        }

        if (request.getBitRate() != null && request.getBitRate() <= 0) {
            throw new IllegalArgumentException("Audio bit rate must be positive");
        }

        if (request.getAudioStreamIndex() != null && request.getAudioStreamIndex() < 0) {
            throw new IllegalArgumentException("Audio stream index must be non-negative");
        }

        if (request.getFingerprintDurationSeconds() == null || request.getFingerprintDurationSeconds() <= 0) {
            throw new IllegalArgumentException("Audio fingerprint duration must be positive");
        }

        validateRequiredText(request.getCodec(), "Audio codec", 255);
        validateRequiredText(request.getFingerprint(), "Audio fingerprint", AudioFingerprint.MAX_FINGERPRINT_LENGTH);
        validateRequiredText(
                request.getFingerprintAlgorithm(),
                "Audio fingerprint algorithm",
                AudioFingerprint.MAX_FINGERPRINT_ALGORITHM_LENGTH);
        validateRequiredText(
                request.getFingerprintVersion(),
                "Audio fingerprint version",
                AudioFingerprint.MAX_FINGERPRINT_VERSION_LENGTH);
        validateNullableText(
                request.getContainerFormat(),
                "Audio container format",
                255);
    }

    private void validateRequiredText(String value, String fieldName, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }

        if (value.trim().length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " exceeds maximum length");
        }
    }

    private void validateNullableText(String value, String fieldName, int maxLength) {
        if (value != null && value.trim().length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " exceeds maximum length");
        }
    }

    private void validateEmbeddingModel(
            String modelName,
            String modelVersion,
            Integer dimension,
            AppProperties.Embedding embeddingProperties) {
        if (!Objects.equals(modelName, embeddingProperties.getModelName())) {
            throw new IllegalArgumentException("Embedding model name mismatch");
        }

        if (!Objects.equals(modelVersion, embeddingProperties.getModelVersion())) {
            throw new IllegalArgumentException("Embedding model version mismatch");
        }

        if (dimension == null || dimension != embeddingProperties.getDimension()) {
            throw new IllegalArgumentException("Embedding dimension mismatch");
        }
    }

    private void validateEmbeddingValues(Integer dimension, List<Double> embedding) {
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

    private String updateFileFingerprint(FileEntity file, String hashValue) {
        return fileFingerprintRepository.findByFileIdAndAlgorithm(file.getId(), FileFingerprint.FingerprintAlgorithm.SHA256)
                .map(existing -> {
                    String oldHashValue = existing.getHashValue();
                    existing.setHashValue(hashValue);
                    fileFingerprintRepository.save(existing);
                    return oldHashValue;
                })
                .orElseGet(() -> {
                    FileFingerprint fingerprint = FileFingerprint.builder()
                            .file(file)
                            .algorithm(FileFingerprint.FingerprintAlgorithm.SHA256)
                            .hashValue(hashValue)
                            .build();
                    fileFingerprintRepository.save(fingerprint);
                    return null;
                });
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

    private void updateAudioFingerprint(FileEntity file, AudioAnalysisResultRequest request) {
        audioFingerprintRepository.findByFileId(file.getId())
                .ifPresentOrElse(
                        existing -> {
                            existing.setDurationMs(request.getDurationMs());
                            existing.setCodec(request.getCodec().trim());
                            existing.setSampleRate(request.getSampleRate());
                            existing.setChannels(request.getChannels());
                            existing.setBitRate(request.getBitRate());
                            existing.setAudioStreamIndex(request.getAudioStreamIndex());
                            existing.setContainerFormat(normalizeNullableText(request.getContainerFormat()));
                            existing.setFingerprint(request.getFingerprint().trim());
                            existing.setFingerprintAlgorithm(request.getFingerprintAlgorithm().trim());
                            existing.setFingerprintVersion(request.getFingerprintVersion().trim());
                            existing.setFingerprintDurationSeconds(request.getFingerprintDurationSeconds());
                            audioFingerprintRepository.save(existing);
                        },
                        () -> audioFingerprintRepository.save(AudioFingerprint.builder()
                                .file(file)
                                .durationMs(request.getDurationMs())
                                .codec(request.getCodec().trim())
                                .sampleRate(request.getSampleRate())
                                .channels(request.getChannels())
                                .bitRate(request.getBitRate())
                                .audioStreamIndex(request.getAudioStreamIndex())
                                .containerFormat(normalizeNullableText(request.getContainerFormat()))
                                .fingerprint(request.getFingerprint().trim())
                                .fingerprintAlgorithm(request.getFingerprintAlgorithm().trim())
                                .fingerprintVersion(request.getFingerprintVersion().trim())
                                .fingerprintDurationSeconds(request.getFingerprintDurationSeconds())
                                .build())
                );
    }

    private String normalizeNullableText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private float[] normalizeEmbedding(List<Double> embedding) {
        double squaredNorm = 0.0;
        for (Double value : embedding) {
            squaredNorm += value * value;
        }
        return normalizeVector(embedding, squaredNorm);
    }

    private float[] normalizeVector(double[] values) {
        double squaredNorm = 0.0;
        for (double value : values) {
            squaredNorm += value * value;
        }
        return normalizeVector(values, squaredNorm);
    }

    private float[] normalizeVector(List<Double> values, double squaredNorm) {
        double norm = Math.sqrt(squaredNorm);
        if (!Double.isFinite(norm) || norm == 0.0) {
            throw new IllegalArgumentException("Embedding norm must be finite and non-zero");
        }

        float[] normalized = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            normalized[i] = (float) (values.get(i) / norm);
        }

        return normalized;
    }

    private float[] normalizeVector(double[] values, double squaredNorm) {
        double norm = Math.sqrt(squaredNorm);
        if (!Double.isFinite(norm) || norm == 0.0) {
            throw new IllegalArgumentException("Embedding norm must be finite and non-zero");
        }

        float[] normalized = new float[values.length];
        for (int i = 0; i < values.length; i++) {
            normalized[i] = (float) (values[i] / norm);
        }

        return normalized;
    }

    private void completeJob(ProcessingJob job) {
        job.setStatus(ProcessingJob.JobStatus.COMPLETED);
        job.setErrorMessage(null);
        processingJobRepository.save(job);
        fileManagerMetrics.recordJobCompleted(job.getJobType().name());
    }
}

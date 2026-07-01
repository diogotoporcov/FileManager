package com.diogotoporcov.filemanager.api.processing.application;

import com.diogotoporcov.filemanager.api.config.AppProperties;
import com.diogotoporcov.filemanager.api.duplicate.application.ExactDuplicateGroupMaintenanceService;
import com.diogotoporcov.filemanager.api.duplicate.phash.PhashMih;
import com.diogotoporcov.filemanager.api.exception.ResourceNotFoundException;
import com.diogotoporcov.filemanager.api.file.domain.FileEntity;
import com.diogotoporcov.filemanager.api.file.persistence.FileRepository;
import com.diogotoporcov.filemanager.api.observability.application.FileManagerMetrics;
import com.diogotoporcov.filemanager.api.processing.domain.ProcessingJob;
import com.diogotoporcov.filemanager.api.processing.domain.result.AudioFingerprint;
import com.diogotoporcov.filemanager.api.processing.domain.result.FileEmbedding;
import com.diogotoporcov.filemanager.api.processing.domain.result.FileFingerprint;
import com.diogotoporcov.filemanager.api.processing.domain.result.ImageFingerprint;
import com.diogotoporcov.filemanager.api.processing.domain.result.ImagePhashMihChunk;
import com.diogotoporcov.filemanager.api.processing.domain.result.ImagePhashMihChunkId;
import com.diogotoporcov.filemanager.api.processing.persistence.ProcessingJobRepository;
import com.diogotoporcov.filemanager.api.processing.persistence.result.AudioFingerprintRepository;
import com.diogotoporcov.filemanager.api.processing.persistence.result.FileEmbeddingRepository;
import com.diogotoporcov.filemanager.api.processing.persistence.result.FileFingerprintRepository;
import com.diogotoporcov.filemanager.api.processing.persistence.result.ImageFingerprintRepository;
import com.diogotoporcov.filemanager.api.processing.persistence.result.ImagePhashMihChunkRepository;
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
    private static final int MAX_ERROR_MESSAGE_LENGTH = 1024;
    private static final String DEFAULT_ERROR_MESSAGE = "Processing failed";

    private final ProcessingJobRepository processingJobRepository;
    private final FileRepository fileRepository;
    private final FileFingerprintRepository fileFingerprintRepository;
    private final ImageFingerprintRepository imageFingerprintRepository;
    private final ImagePhashMihChunkRepository imagePhashMihChunkRepository;
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
        ProcessingJob job = processingJobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Processing job not found: " + jobId));

        if (!job.getFile().getId().equals(fileId)) {
            throw new IllegalArgumentException("Job file ID mismatch");
        }

        if (job.getStatus() == ProcessingJob.JobStatus.COMPLETED) {
            log.info("Ignoring failure callback for already completed job {}", jobId);
            return;
        }

        String safeErrorMessage = safeErrorMessage(errorMessage);
        log.info("Handling processing failure for job {} (file {}): {}", jobId, fileId, safeErrorMessage);

        if (job.getStatus() == ProcessingJob.JobStatus.FAILED) {
            job.setErrorMessage(safeErrorMessage);
            processingJobRepository.save(job);
            return;
        }

        job.setStatus(ProcessingJob.JobStatus.FAILED);
        job.setErrorMessage(safeErrorMessage);
        processingJobRepository.save(job);

        fileManagerMetrics.recordJobFailed(job.getJobType().name());
    }

    @Transactional
    public void handleChecksumResult(UUID jobId, UUID fileId, String sha256) {
        log.info("Handling checksum result for job {}: {}", jobId, sha256);

        validateChecksumFormat(sha256);
        String normalizedSha256 = sha256.toLowerCase(Locale.ROOT);

        ProcessingJob job = getAndValidateJob(jobId, fileId, ProcessingJob.JobType.CHECKSUM);
        if (shouldIgnoreSuccessfulTerminalCallback(job)) {
            return;
        }
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

        String normalizedPhash = PhashMih.normalize(phash);

        ProcessingJob job = getAndValidateJob(jobId, fileId, ProcessingJob.JobType.PHASH);
        if (shouldIgnoreSuccessfulTerminalCallback(job)) {
            return;
        }
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
        if (shouldIgnoreSuccessfulTerminalCallback(job)) {
            return;
        }
        FileEntity file = getActiveFile(fileId);

        float[] normalizedEmbedding = normalizeEmbedding(embedding);
        updateFileEmbedding(file, modelName, modelVersion, dimension, normalizedEmbedding);
        completeJob(job);
    }

    @Transactional
    public void handleAudioAnalysisResult(UUID jobId, AudioAnalysisResultCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Audio analysis result command must not be null");
        }
        log.info("Handling audio analysis result for job {} and file {}", jobId, command.fileId());

        validateAudioAnalysisResult(command);

        ProcessingJob job = getAndValidateJob(jobId, command.fileId(), ProcessingJob.JobType.AUDIO_ANALYSIS);
        if (shouldIgnoreSuccessfulTerminalCallback(job)) {
            return;
        }
        FileEntity file = getActiveFile(command.fileId());

        updateAudioFingerprint(file, command);
        completeJob(job);
    }

    private void validateChecksumFormat(String sha256) {
        if (sha256 == null || !sha256.matches("^[a-fA-F0-9]{64}$")) {
            throw new IllegalArgumentException("Invalid SHA-256 format");
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

    private void validateAudioAnalysisResult(AudioAnalysisResultCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Audio analysis result command must not be null");
        }

        if (command.durationMs() == null || command.durationMs() <= 0) {
            throw new IllegalArgumentException("Audio duration must be positive");
        }

        if (command.sampleRate() == null || command.sampleRate() <= 0) {
            throw new IllegalArgumentException("Audio sample rate must be positive");
        }

        if (command.channels() == null || command.channels() <= 0) {
            throw new IllegalArgumentException("Audio channels must be positive");
        }

        if (command.bitRate() != null && command.bitRate() <= 0) {
            throw new IllegalArgumentException("Audio bit rate must be positive");
        }

        if (command.audioStreamIndex() != null && command.audioStreamIndex() < 0) {
            throw new IllegalArgumentException("Audio stream index must be non-negative");
        }

        if (command.fingerprintDurationSeconds() == null || command.fingerprintDurationSeconds() <= 0) {
            throw new IllegalArgumentException("Audio fingerprint duration must be positive");
        }

        validateRequiredText(command.codec(), "Audio codec", 255);
        validateRequiredText(command.fingerprint(), "Audio fingerprint", AudioFingerprint.MAX_FINGERPRINT_LENGTH);
        validateRequiredText(
                command.fingerprintAlgorithm(),
                "Audio fingerprint algorithm",
                AudioFingerprint.MAX_FINGERPRINT_ALGORITHM_LENGTH);
        validateRequiredText(
                command.fingerprintVersion(),
                "Audio fingerprint version",
                AudioFingerprint.MAX_FINGERPRINT_VERSION_LENGTH);
        validateNullableText(
                command.containerFormat(),
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

    private boolean shouldIgnoreSuccessfulTerminalCallback(ProcessingJob job) {
        if (job.getStatus() == ProcessingJob.JobStatus.COMPLETED) {
            log.info("Ignoring duplicate success callback for already completed job {}", job.getId());
            return true;
        }

        if (job.getStatus() == ProcessingJob.JobStatus.FAILED) {
            throw new IllegalStateException("Processing job is already failed");
        }

        return false;
    }

    private String safeErrorMessage(String errorMessage) {
        String normalized = errorMessage == null || errorMessage.isBlank()
                ? DEFAULT_ERROR_MESSAGE
                : errorMessage.strip().replaceAll("[\\r\\n\\t]+", " ");

        if (normalized.length() <= MAX_ERROR_MESSAGE_LENGTH) {
            return normalized;
        }

        return normalized.substring(0, MAX_ERROR_MESSAGE_LENGTH);
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
        imagePhashMihChunkRepository.deleteByIdFileId(file.getId());
        imagePhashMihChunkRepository.saveAll(PhashMih.chunks(phash).stream()
                .map(chunk -> ImagePhashMihChunk.builder()
                        .id(new ImagePhashMihChunkId(file.getId(), (short) chunk.index()))
                        .file(file)
                        .chunkValue(chunk.value())
                        .build())
                .toList());
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

    private void updateAudioFingerprint(FileEntity file, AudioAnalysisResultCommand command) {
        audioFingerprintRepository.findByFileId(file.getId())
                .ifPresentOrElse(
                        existing -> {
                            existing.setDurationMs(command.durationMs());
                            existing.setCodec(command.codec().trim());
                            existing.setSampleRate(command.sampleRate());
                            existing.setChannels(command.channels());
                            existing.setBitRate(command.bitRate());
                            existing.setAudioStreamIndex(command.audioStreamIndex());
                            existing.setContainerFormat(normalizeNullableText(command.containerFormat()));
                            existing.setFingerprint(command.fingerprint().trim());
                            existing.setFingerprintAlgorithm(command.fingerprintAlgorithm().trim());
                            existing.setFingerprintVersion(command.fingerprintVersion().trim());
                            existing.setFingerprintDurationSeconds(command.fingerprintDurationSeconds());
                            audioFingerprintRepository.save(existing);
                        },
                        () -> audioFingerprintRepository.save(AudioFingerprint.builder()
                                .file(file)
                                .durationMs(command.durationMs())
                                .codec(command.codec().trim())
                                .sampleRate(command.sampleRate())
                                .channels(command.channels())
                                .bitRate(command.bitRate())
                                .audioStreamIndex(command.audioStreamIndex())
                                .containerFormat(normalizeNullableText(command.containerFormat()))
                                .fingerprint(command.fingerprint().trim())
                                .fingerprintAlgorithm(command.fingerprintAlgorithm().trim())
                                .fingerprintVersion(command.fingerprintVersion().trim())
                                .fingerprintDurationSeconds(command.fingerprintDurationSeconds())
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

    private void completeJob(ProcessingJob job) {
        job.setStatus(ProcessingJob.JobStatus.COMPLETED);
        job.setErrorMessage(null);
        processingJobRepository.save(job);
        fileManagerMetrics.recordJobCompleted(job.getJobType().name());
    }
}

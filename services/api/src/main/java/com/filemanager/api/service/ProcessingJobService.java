package com.filemanager.api.service;

import com.filemanager.api.config.AppProperties;
import com.filemanager.api.dto.internal.VideoAnalysisResultRequest;
import com.filemanager.api.entity.FileEmbedding;
import com.filemanager.api.entity.FileEntity;
import com.filemanager.api.entity.FileFingerprint;
import com.filemanager.api.entity.ImageFingerprint;
import com.filemanager.api.entity.ProcessingJob;
import com.filemanager.api.entity.VideoFingerprint;
import com.filemanager.api.entity.VideoFrameEmbedding;
import com.filemanager.api.entity.VideoFrameFingerprint;
import com.filemanager.api.exception.ResourceNotFoundException;
import com.filemanager.api.port.ApplicationMetricsPort;
import com.filemanager.api.repository.FileEmbeddingRepository;
import com.filemanager.api.repository.FileFingerprintRepository;
import com.filemanager.api.repository.FileRepository;
import com.filemanager.api.repository.ImageFingerprintRepository;
import com.filemanager.api.repository.ProcessingJobRepository;
import com.filemanager.api.repository.VideoFingerprintRepository;
import com.filemanager.api.repository.VideoFrameEmbeddingRepository;
import com.filemanager.api.repository.VideoFrameFingerprintRepository;
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
    private final VideoFingerprintRepository videoFingerprintRepository;
    private final VideoFrameFingerprintRepository videoFrameFingerprintRepository;
    private final VideoFrameEmbeddingRepository videoFrameEmbeddingRepository;
    private final ApplicationMetricsPort applicationMetricsPort;
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

        updateFileFingerprint(file, normalizedSha256);
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
    public void handleVideoAnalysisResult(UUID jobId, VideoAnalysisResultRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Video analysis result request must not be null");
        }
        log.info("Handling video analysis result for job {} and file {}", jobId, request.getFileId());

        AppProperties.Embedding embeddingProperties = appProperties.getEmbedding();
        validateVideoAnalysisRequest(request, embeddingProperties);

        ProcessingJob job = getAndValidateJob(jobId, request.getFileId(), ProcessingJob.JobType.VIDEO_ANALYSIS);
        FileEntity file = getActiveFile(request.getFileId());

        updateVideoFingerprint(file, request);
        replaceVideoFrameSignals(file, request);
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

    private void validateVideoAnalysisRequest(
            VideoAnalysisResultRequest request,
            AppProperties.Embedding embeddingProperties) {
        if (request == null) {
            throw new IllegalArgumentException("Video analysis result request must not be null");
        }
        if (request.getFrames() == null || request.getFrames().isEmpty()) {
            throw new IllegalArgumentException("Video analysis frames must not be empty");
        }
        if (request.getSampledFrameCount() == null || request.getSampledFrameCount() != request.getFrames().size()) {
            throw new IllegalArgumentException("Sampled frame count must match frame results");
        }
        if (request.getSampledFrameCount() > VideoAnalysisResultRequest.MAX_FRAMES) {
            throw new IllegalArgumentException("Sampled frame count exceeds maximum");
        }
        validateEmbeddingModel(request.getModelName(), request.getModelVersion(), request.getDimension(), embeddingProperties);
        for (VideoAnalysisResultRequest.FrameResult frame : request.getFrames()) {
            if (frame == null) {
                throw new IllegalArgumentException("Video analysis frame must not be null");
            }
            validatePhashFormat(frame.getPhash());
            validateEmbeddingValues(request.getDimension(), frame.getEmbedding());
        }
    }

    private void validateEmbeddingModel(
            String modelName,
            String modelVersion,
            Integer dimension,
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

    private void updateVideoFingerprint(FileEntity file, VideoAnalysisResultRequest request) {
        videoFingerprintRepository.findByFileId(file.getId())
                .ifPresentOrElse(
                        existing -> {
                            existing.setDurationMs(request.getDurationMs());
                            existing.setWidth(request.getWidth());
                            existing.setHeight(request.getHeight());
                            existing.setFrameCount(request.getFrameCount());
                            existing.setCodec(normalizeNullableText(request.getCodec()));
                            existing.setSampledFrameCount(request.getSampledFrameCount());
                            existing.setSamplingStrategy(request.getSamplingStrategy());
                            videoFingerprintRepository.save(existing);
                        },
                        () -> videoFingerprintRepository.save(VideoFingerprint.builder()
                                .file(file)
                                .durationMs(request.getDurationMs())
                                .width(request.getWidth())
                                .height(request.getHeight())
                                .frameCount(request.getFrameCount())
                                .codec(normalizeNullableText(request.getCodec()))
                                .sampledFrameCount(request.getSampledFrameCount())
                                .samplingStrategy(request.getSamplingStrategy())
                                .build())
                );
    }

    private void replaceVideoFrameSignals(FileEntity file, VideoAnalysisResultRequest request) {
        videoFrameFingerprintRepository.deleteByFileId(file.getId());
        videoFrameEmbeddingRepository.deleteByFileId(file.getId());

        List<VideoFrameFingerprint> fingerprints = request.getFrames().stream()
                .map(frame -> VideoFrameFingerprint.builder()
                        .file(file)
                        .timestampMs(frame.getTimestampMs())
                        .frameIndex(frame.getFrameIndex())
                        .phash(frame.getPhash().toLowerCase(Locale.ROOT))
                        .build())
                .toList();
        videoFrameFingerprintRepository.saveAll(fingerprints);

        List<VideoFrameEmbedding> embeddings = request.getFrames().stream()
                .map(frame -> VideoFrameEmbedding.builder()
                        .file(file)
                        .timestampMs(frame.getTimestampMs())
                        .frameIndex(frame.getFrameIndex())
                        .modelName(request.getModelName())
                        .modelVersion(request.getModelVersion())
                        .dimension(request.getDimension())
                        .embedding(normalizeEmbedding(frame.getEmbedding()))
                        .build())
                .toList();
        videoFrameEmbeddingRepository.saveAll(embeddings);
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

    private void completeJob(ProcessingJob job) {
        job.setStatus(ProcessingJob.JobStatus.COMPLETED);
        job.setErrorMessage(null);
        processingJobRepository.save(job);
        applicationMetricsPort.recordJobCompleted(job.getJobType().name());
    }
}

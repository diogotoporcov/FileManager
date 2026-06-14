package com.filemanager.api.duplicate.application;

import com.filemanager.api.duplicate.domain.DuplicateCandidate;
import com.filemanager.api.duplicate.domain.DuplicateSearchMethod;
import com.filemanager.api.duplicate.persistence.DuplicateCandidateRefreshRepository;
import com.filemanager.api.duplicate.persistence.DuplicateCandidateRepository;
import com.filemanager.api.file.domain.FileEntity;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;
import java.util.UUID;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DuplicateCandidateMaintenanceService {
    private final DuplicateCandidateRepository duplicateCandidateRepository;
    private final DuplicateCandidateRefreshRepository duplicateCandidateRefreshRepository;
    private final DuplicateDetectionProperties properties;
    private final DataSource dataSource;

    @Transactional
    public void refreshImagePhashCandidates(FileEntity sourceFile) {
        UUID ownerUserId = ownerUserId(sourceFile);
        duplicateCandidateRepository.deleteInvolvingSource(
                ownerUserId,
                sourceFile.getId(),
                DuplicateSearchMethod.IMAGE_PHASH.name());

        if (!properties.getImagePhash().isEnabled() || !supportsPostgresCandidateGeneration()) {
            return;
        }

        String thresholdVersion = phashThresholdVersion();
        int candidateCount = duplicateCandidateRepository.insertImagePhashCandidates(
                ownerUserId,
                sourceFile.getId(),
                properties.getImagePhash().getMaxDistance(),
                properties.getImagePhash().getMaxCandidates(),
                DuplicateCandidate.NO_MODEL,
                DuplicateCandidate.NO_MODEL,
                thresholdVersion);
        recordRefresh(
                ownerUserId,
                sourceFile.getId(),
                DuplicateSearchMethod.IMAGE_PHASH,
                DuplicateCandidate.NO_MODEL,
                DuplicateCandidate.NO_MODEL,
                thresholdVersion,
                candidateCount);
    }

    @Transactional
    public void refreshImageEmbeddingCandidates(FileEntity sourceFile, String modelName, String modelVersion, int dimension) {
        UUID ownerUserId = ownerUserId(sourceFile);
        duplicateCandidateRepository.deleteInvolvingSource(
                ownerUserId,
                sourceFile.getId(),
                DuplicateSearchMethod.IMAGE_EMBEDDING.name());

        if (!properties.getImageEmbedding().isEnabled() || !supportsPostgresCandidateGeneration()) {
            return;
        }

        String thresholdVersion = embeddingThresholdVersion(properties.getImageEmbedding().getMaxDistance());
        int candidateCount = duplicateCandidateRepository.insertImageEmbeddingCandidates(
                ownerUserId,
                sourceFile.getId(),
                modelName,
                modelVersion,
                dimension,
                properties.getImageEmbedding().getMaxDistance(),
                properties.getImageEmbedding().getMaxCandidates(),
                thresholdVersion);
        recordRefresh(
                ownerUserId,
                sourceFile.getId(),
                DuplicateSearchMethod.IMAGE_EMBEDDING,
                modelName,
                modelVersion,
                thresholdVersion,
                candidateCount);
    }

    @Transactional
    public void refreshAudioFingerprintCandidates(FileEntity sourceFile) {
        UUID ownerUserId = ownerUserId(sourceFile);
        duplicateCandidateRepository.deleteInvolvingSource(
                ownerUserId,
                sourceFile.getId(),
                DuplicateSearchMethod.AUDIO_FINGERPRINT.name());

        if (!properties.getAudioFingerprint().isEnabled() || !supportsPostgresCandidateGeneration()) {
            return;
        }

        String thresholdVersion = audioThresholdVersion();
        int candidateCount = duplicateCandidateRepository.insertAudioFingerprintCandidates(
                ownerUserId,
                sourceFile.getId(),
                properties.getAudioFingerprint().getMaxCandidates(),
                DuplicateCandidate.NO_MODEL,
                DuplicateCandidate.NO_MODEL,
                thresholdVersion);
        recordRefresh(
                ownerUserId,
                sourceFile.getId(),
                DuplicateSearchMethod.AUDIO_FINGERPRINT,
                DuplicateCandidate.NO_MODEL,
                DuplicateCandidate.NO_MODEL,
                thresholdVersion,
                candidateCount);
    }

    @Transactional
    public void refreshVideoEmbeddingCandidates(FileEntity sourceFile, String modelName, String modelVersion, int dimension) {
        UUID ownerUserId = ownerUserId(sourceFile);
        duplicateCandidateRepository.deleteInvolvingSource(
                ownerUserId,
                sourceFile.getId(),
                DuplicateSearchMethod.VIDEO_EMBEDDING.name());

        if (!properties.getVideoEmbedding().isEnabled() || !supportsPostgresCandidateGeneration()) {
            return;
        }

        String thresholdVersion = embeddingThresholdVersion(properties.getVideoEmbedding().getMaxDistance());
        int candidateCount = duplicateCandidateRepository.insertVideoEmbeddingCandidates(
                ownerUserId,
                sourceFile.getId(),
                modelName,
                modelVersion,
                dimension,
                properties.getVideoEmbedding().getMaxDistance(),
                properties.getVideoEmbedding().getMaxCandidates(),
                thresholdVersion);
        recordRefresh(
                ownerUserId,
                sourceFile.getId(),
                DuplicateSearchMethod.VIDEO_EMBEDDING,
                modelName,
                modelVersion,
                thresholdVersion,
                candidateCount);
    }

    public String phashThresholdVersion() {
        return "maxDistance=" + properties.getImagePhash().getMaxDistance()
                + ";topN=" + properties.getImagePhash().getMaxCandidates();
    }

    public String imageEmbeddingThresholdVersion() {
        return embeddingThresholdVersion(properties.getImageEmbedding().getMaxDistance());
    }

    public String videoEmbeddingThresholdVersion() {
        return embeddingThresholdVersion(properties.getVideoEmbedding().getMaxDistance());
    }

    public String audioThresholdVersion() {
        return "topN=" + properties.getAudioFingerprint().getMaxCandidates();
    }

    private String embeddingThresholdVersion(double maxDistance) {
        return String.format(Locale.ROOT, "maxDistance=%.6f", maxDistance);
    }

    private UUID ownerUserId(FileEntity sourceFile) {
        return sourceFile.getOwnerUser().getId();
    }

    private void recordRefresh(
            UUID ownerUserId,
            UUID sourceFileId,
            DuplicateSearchMethod method,
            String modelName,
            String modelVersion,
            String thresholdVersion,
            int candidateCount) {
        duplicateCandidateRefreshRepository.upsertRefresh(
                ownerUserId,
                sourceFileId,
                method.name(),
                modelName,
                modelVersion,
                thresholdVersion,
                candidateCount);
    }

    private boolean supportsPostgresCandidateGeneration() {
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            return connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT).contains("postgres");
        } catch (SQLException ex) {
            throw new IllegalStateException("Unable to inspect database product for duplicate candidate generation", ex);
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }
}

package com.filemanager.api.duplicate.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

import com.filemanager.api.duplicate.domain.DuplicateCandidate;
import com.filemanager.api.duplicate.domain.DuplicateSearchMethod;
import com.filemanager.api.duplicate.persistence.DuplicateCandidateRefreshRepository;
import com.filemanager.api.duplicate.persistence.DuplicateCandidateRepository;
import com.filemanager.api.file.domain.FileEntity;
import com.filemanager.api.identity.domain.User;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DuplicateCandidateMaintenanceServiceTest {
    @Mock
    private DuplicateCandidateRepository duplicateCandidateRepository;
    @Mock
    private DuplicateCandidateRefreshRepository duplicateCandidateRefreshRepository;
    @Mock
    private DataSource dataSource;
    @Mock
    private Connection connection;
    @Mock
    private DatabaseMetaData databaseMetaData;

    private DuplicateDetectionProperties properties;
    private DuplicateCandidateMaintenanceService service;
    private User owner;
    private FileEntity file;

    @BeforeEach
    void setUp() throws Exception {
        properties = new DuplicateDetectionProperties();
        service = new DuplicateCandidateMaintenanceService(
                duplicateCandidateRepository,
                duplicateCandidateRefreshRepository,
                properties,
                dataSource);
        owner = User.builder().id(UUID.randomUUID()).build();
        file = FileEntity.builder()
                .id(UUID.randomUUID())
                .ownerUser(owner)
                .build();

        lenient().when(dataSource.getConnection()).thenReturn(connection);
        lenient().when(connection.getMetaData()).thenReturn(databaseMetaData);
    }

    @Test
    void phashRefreshDeletesOldSourceRowsAndInsertsBoundedPostgresCandidates() throws Exception {
        when(databaseMetaData.getDatabaseProductName()).thenReturn("PostgreSQL");
        when(duplicateCandidateRepository.insertImagePhashCandidates(
                owner.getId(),
                file.getId(),
                properties.getImagePhash().getMaxDistance(),
                properties.getImagePhash().getMaxCandidates(),
                DuplicateCandidate.NO_MODEL,
                DuplicateCandidate.NO_MODEL,
                "maxDistance=10;topN=100"))
                .thenReturn(3);

        service.refreshImagePhashCandidates(file);

        verify(duplicateCandidateRepository).deleteInvolvingSource(
                owner.getId(),
                file.getId(),
                DuplicateSearchMethod.IMAGE_PHASH.name());
        verify(duplicateCandidateRepository).insertImagePhashCandidates(
                owner.getId(),
                file.getId(),
                properties.getImagePhash().getMaxDistance(),
                properties.getImagePhash().getMaxCandidates(),
                DuplicateCandidate.NO_MODEL,
                DuplicateCandidate.NO_MODEL,
                "maxDistance=10;topN=100");
        verify(duplicateCandidateRefreshRepository).upsertRefresh(
                owner.getId(),
                file.getId(),
                DuplicateSearchMethod.IMAGE_PHASH.name(),
                DuplicateCandidate.NO_MODEL,
                DuplicateCandidate.NO_MODEL,
                "maxDistance=10;topN=100",
                3);
    }

    @Test
    void phashRefreshWithZeroCandidatesWritesZeroCountMarker() throws Exception {
        when(databaseMetaData.getDatabaseProductName()).thenReturn("PostgreSQL");

        service.refreshImagePhashCandidates(file);

        verify(duplicateCandidateRefreshRepository).upsertRefresh(
                owner.getId(),
                file.getId(),
                DuplicateSearchMethod.IMAGE_PHASH.name(),
                DuplicateCandidate.NO_MODEL,
                DuplicateCandidate.NO_MODEL,
                "maxDistance=10;topN=100",
                0);
    }

    @Test
    void phashRefreshSkipsGenerationOutsidePostgresWithoutWritingMarker() throws Exception {
        when(databaseMetaData.getDatabaseProductName()).thenReturn("H2");

        service.refreshImagePhashCandidates(file);

        verify(duplicateCandidateRepository).deleteInvolvingSource(
                owner.getId(),
                file.getId(),
                DuplicateSearchMethod.IMAGE_PHASH.name());
        verify(duplicateCandidateRepository, never()).insertImagePhashCandidates(
                owner.getId(),
                file.getId(),
                properties.getImagePhash().getMaxDistance(),
                properties.getImagePhash().getMaxCandidates(),
                DuplicateCandidate.NO_MODEL,
                DuplicateCandidate.NO_MODEL,
                "maxDistance=10;topN=100");
        verify(duplicateCandidateRefreshRepository, never()).upsertRefresh(
                owner.getId(),
                file.getId(),
                DuplicateSearchMethod.IMAGE_PHASH.name(),
                DuplicateCandidate.NO_MODEL,
                DuplicateCandidate.NO_MODEL,
                "maxDistance=10;topN=100",
                0);
    }

    @Test
    void disabledPhashRefreshRemovesStaleRowsWithoutWritingMarker() {
        properties.getImagePhash().setEnabled(false);

        service.refreshImagePhashCandidates(file);

        verify(duplicateCandidateRepository).deleteInvolvingSource(
                owner.getId(),
                file.getId(),
                DuplicateSearchMethod.IMAGE_PHASH.name());
        verify(duplicateCandidateRepository, never()).insertImagePhashCandidates(
                owner.getId(),
                file.getId(),
                properties.getImagePhash().getMaxDistance(),
                properties.getImagePhash().getMaxCandidates(),
                DuplicateCandidate.NO_MODEL,
                DuplicateCandidate.NO_MODEL,
                "maxDistance=10;topN=100");
        verify(duplicateCandidateRefreshRepository, never()).upsertRefresh(
                owner.getId(),
                file.getId(),
                DuplicateSearchMethod.IMAGE_PHASH.name(),
                DuplicateCandidate.NO_MODEL,
                DuplicateCandidate.NO_MODEL,
                "maxDistance=10;topN=100",
                0);
    }

    @Test
    void failedCandidateGenerationDoesNotWriteRefreshMarker() throws Exception {
        when(databaseMetaData.getDatabaseProductName()).thenReturn("PostgreSQL");
        when(duplicateCandidateRepository.insertImagePhashCandidates(
                owner.getId(),
                file.getId(),
                properties.getImagePhash().getMaxDistance(),
                properties.getImagePhash().getMaxCandidates(),
                DuplicateCandidate.NO_MODEL,
                DuplicateCandidate.NO_MODEL,
                "maxDistance=10;topN=100"))
                .thenThrow(new IllegalStateException("insert failed"));

        assertThatThrownBy(() -> service.refreshImagePhashCandidates(file))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("insert failed");

        verify(duplicateCandidateRefreshRepository, never()).upsertRefresh(
                owner.getId(),
                file.getId(),
                DuplicateSearchMethod.IMAGE_PHASH.name(),
                DuplicateCandidate.NO_MODEL,
                DuplicateCandidate.NO_MODEL,
                "maxDistance=10;topN=100",
                0);
    }

    @Test
    void repeatedPhashRefreshUsesSameMarkerKey() throws Exception {
        when(databaseMetaData.getDatabaseProductName()).thenReturn("PostgreSQL");

        service.refreshImagePhashCandidates(file);
        service.refreshImagePhashCandidates(file);

        verify(duplicateCandidateRefreshRepository, times(2)).upsertRefresh(
                owner.getId(),
                file.getId(),
                DuplicateSearchMethod.IMAGE_PHASH.name(),
                DuplicateCandidate.NO_MODEL,
                DuplicateCandidate.NO_MODEL,
                "maxDistance=10;topN=100",
                0);
    }

    @Test
    void imageEmbeddingRefreshUsesModelVersionDimensionAndThresholdSignature() throws Exception {
        when(databaseMetaData.getDatabaseProductName()).thenReturn("PostgreSQL");

        service.refreshImageEmbeddingCandidates(file, "clip", "1", 768);

        verify(duplicateCandidateRepository).deleteInvolvingSource(
                owner.getId(),
                file.getId(),
                DuplicateSearchMethod.IMAGE_EMBEDDING.name());
        verify(duplicateCandidateRepository).insertImageEmbeddingCandidates(
                owner.getId(),
                file.getId(),
                "clip",
                "1",
                768,
                properties.getImageEmbedding().getMaxDistance(),
                properties.getImageEmbedding().getMaxCandidates(),
                "maxDistance=0.200000");
        verify(duplicateCandidateRefreshRepository).upsertRefresh(
                owner.getId(),
                file.getId(),
                DuplicateSearchMethod.IMAGE_EMBEDDING.name(),
                "clip",
                "1",
                "maxDistance=0.200000",
                0);
    }

    @Test
    void audioRefreshUsesExactFingerprintThresholdSignature() throws Exception {
        when(databaseMetaData.getDatabaseProductName()).thenReturn("PostgreSQL");

        service.refreshAudioFingerprintCandidates(file);

        verify(duplicateCandidateRepository).deleteInvolvingSource(
                owner.getId(),
                file.getId(),
                DuplicateSearchMethod.AUDIO_FINGERPRINT.name());
        verify(duplicateCandidateRepository).insertAudioFingerprintCandidates(
                owner.getId(),
                file.getId(),
                properties.getAudioFingerprint().getMaxCandidates(),
                DuplicateCandidate.NO_MODEL,
                DuplicateCandidate.NO_MODEL,
                "topN=100");
        verify(duplicateCandidateRefreshRepository).upsertRefresh(
                owner.getId(),
                file.getId(),
                DuplicateSearchMethod.AUDIO_FINGERPRINT.name(),
                DuplicateCandidate.NO_MODEL,
                DuplicateCandidate.NO_MODEL,
                "topN=100",
                0);
    }

    @Test
    void videoEmbeddingRefreshUsesModelVersionDimensionAndThresholdSignature() throws Exception {
        when(databaseMetaData.getDatabaseProductName()).thenReturn("PostgreSQL");

        service.refreshVideoEmbeddingCandidates(file, "clip", "1", 768);

        verify(duplicateCandidateRepository).deleteInvolvingSource(
                owner.getId(),
                file.getId(),
                DuplicateSearchMethod.VIDEO_EMBEDDING.name());
        verify(duplicateCandidateRepository).insertVideoEmbeddingCandidates(
                owner.getId(),
                file.getId(),
                "clip",
                "1",
                768,
                properties.getVideoEmbedding().getMaxDistance(),
                properties.getVideoEmbedding().getMaxCandidates(),
                "maxDistance=0.200000");
        verify(duplicateCandidateRefreshRepository).upsertRefresh(
                owner.getId(),
                file.getId(),
                DuplicateSearchMethod.VIDEO_EMBEDDING.name(),
                "clip",
                "1",
                "maxDistance=0.200000",
                0);
    }

    @Test
    void thresholdSignatureReflectsConfiguredTopN() {
        properties.getImagePhash().setMaxCandidates(20);
        properties.getImagePhash().setMaxDistance(4);

        assertThat(service.phashThresholdVersion()).isEqualTo("maxDistance=4;topN=20");
    }
}

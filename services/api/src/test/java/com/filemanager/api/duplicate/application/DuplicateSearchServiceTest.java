package com.filemanager.api.duplicate.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

import com.filemanager.api.config.AppProperties;
import com.filemanager.api.duplicate.domain.DuplicateCandidate;
import com.filemanager.api.duplicate.domain.DuplicateCandidate.DuplicateCandidateStatus;
import com.filemanager.api.duplicate.domain.DuplicateSearchMethod;
import com.filemanager.api.duplicate.persistence.AudioDuplicateCandidateRepository;
import com.filemanager.api.duplicate.persistence.DuplicateCandidateFileProjection;
import com.filemanager.api.duplicate.persistence.DuplicateCandidateRefreshRepository;
import com.filemanager.api.duplicate.persistence.DuplicateCandidateRepository;
import com.filemanager.api.duplicate.persistence.EmbeddingDuplicateCandidateProjection;
import com.filemanager.api.duplicate.persistence.ExactDuplicateCandidateRepository;
import com.filemanager.api.duplicate.persistence.ExactDuplicateGroupRepository;
import com.filemanager.api.duplicate.persistence.ImageEmbeddingDuplicateCandidateRepository;
import com.filemanager.api.duplicate.persistence.ImagePhashDuplicateCandidateRepository;
import com.filemanager.api.duplicate.persistence.PhashDuplicateCandidateProjection;
import com.filemanager.api.duplicate.persistence.VideoEmbeddingDuplicateCandidateRepository;
import com.filemanager.api.duplicate.web.DuplicateSearchResponse;
import com.filemanager.api.file.domain.FileEntity;
import com.filemanager.api.file.persistence.FileRepository;
import com.filemanager.api.identity.domain.User;
import com.filemanager.api.observability.application.FileManagerMetrics;
import com.filemanager.api.processing.domain.result.FileEmbedding;
import com.filemanager.api.processing.domain.result.ImageFingerprint;
import com.filemanager.api.processing.persistence.result.AudioFingerprintRepository;
import com.filemanager.api.processing.persistence.result.FileEmbeddingRepository;
import com.filemanager.api.processing.persistence.result.FileFingerprintRepository;
import com.filemanager.api.processing.persistence.result.ImageFingerprintRepository;
import com.filemanager.api.processing.persistence.result.VideoEmbeddingRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class DuplicateSearchServiceTest {
    private static final String PHASH_THRESHOLD = "maxDistance=10;topN=100";
    private static final String EMBEDDING_THRESHOLD = "maxDistance=0.200000";

    @Mock
    private FileRepository fileRepository;
    @Mock
    private FileFingerprintRepository fileFingerprintRepository;
    @Mock
    private ImageFingerprintRepository imageFingerprintRepository;
    @Mock
    private FileEmbeddingRepository fileEmbeddingRepository;
    @Mock
    private AudioFingerprintRepository audioFingerprintRepository;
    @Mock
    private ExactDuplicateCandidateRepository exactDuplicateCandidateRepository;
    @Mock
    private ExactDuplicateGroupRepository exactDuplicateGroupRepository;
    @Mock
    private DuplicateCandidateRepository duplicateCandidateRepository;
    @Mock
    private DuplicateCandidateRefreshRepository duplicateCandidateRefreshRepository;
    @Mock
    private ImagePhashDuplicateCandidateRepository imagePhashDuplicateCandidateRepository;
    @Mock
    private ImageEmbeddingDuplicateCandidateRepository imageEmbeddingDuplicateCandidateRepository;
    @Mock
    private AudioDuplicateCandidateRepository audioDuplicateCandidateRepository;
    @Mock
    private VideoEmbeddingRepository videoEmbeddingRepository;
    @Mock
    private VideoEmbeddingDuplicateCandidateRepository videoEmbeddingDuplicateCandidateRepository;
    @Mock
    private DuplicateCandidateMaintenanceService duplicateCandidateMaintenanceService;
    @Mock
    private FileManagerMetrics fileManagerMetrics;

    private DuplicateSearchService service;
    private DuplicateDetectionProperties properties;
    private AppProperties appProperties;
    private User actor;
    private FileEntity source;

    @BeforeEach
    void setUp() {
        properties = new DuplicateDetectionProperties();
        appProperties = new AppProperties();
        service = new DuplicateSearchService(
                fileRepository,
                fileFingerprintRepository,
                imageFingerprintRepository,
                fileEmbeddingRepository,
                audioFingerprintRepository,
                exactDuplicateCandidateRepository,
                exactDuplicateGroupRepository,
                duplicateCandidateRepository,
                duplicateCandidateRefreshRepository,
                imagePhashDuplicateCandidateRepository,
                imageEmbeddingDuplicateCandidateRepository,
                audioDuplicateCandidateRepository,
                videoEmbeddingRepository,
                videoEmbeddingDuplicateCandidateRepository,
                properties,
                duplicateCandidateMaintenanceService,
                appProperties,
                fileManagerMetrics);
        actor = User.builder().id(UUID.randomUUID()).build();
        source = FileEntity.builder()
                .id(UUID.randomUUID())
                .name("source.png")
                .mimeType("image/png")
                .size(10L)
                .ownerUser(actor)
                .build();

        when(fileRepository.findEligibleById(source.getId())).thenReturn(Optional.of(source));
        lenient().when(duplicateCandidateMaintenanceService.phashThresholdVersion()).thenReturn(PHASH_THRESHOLD);
        lenient().when(duplicateCandidateMaintenanceService.imageEmbeddingThresholdVersion()).thenReturn(EMBEDDING_THRESHOLD);
    }

    @Test
    void phashReadReturnsPersistedCandidatesWithoutCheckingFallbackMarker() {
        UUID persistedMatch = UUID.randomUUID();
        when(imageFingerprintRepository.findByFileId(source.getId()))
                .thenReturn(Optional.of(ImageFingerprint.builder().file(source).phash("0000000000000000").build()));
        when(duplicateCandidateRepository.findCandidatesForFile(
                actor.getId(),
                source.getId(),
                DuplicateSearchMethod.IMAGE_PHASH,
                DuplicateCandidate.NO_MODEL,
                DuplicateCandidate.NO_MODEL,
                PHASH_THRESHOLD,
                DuplicateCandidateStatus.ACTIVE,
                PageRequest.of(0, properties.getImagePhash().getMaxCandidates())))
                .thenReturn(List.of(new DuplicateCandidateFileProjection(
                        persistedMatch,
                        "match.png",
                        "image/png",
                        10L,
                        1.0,
                        0.9)));

        var response = service.searchDuplicatesForFile(
                source.getId(),
                List.of(DuplicateSearchMethod.IMAGE_PHASH),
                actor.getId());

        assertThat(response.methods().getFirst().matches())
                .extracting(DuplicateSearchResponse.DuplicateMatchResponse::fileId)
                .containsExactly(persistedMatch);
        verify(duplicateCandidateRefreshRepository, never())
                .existsByOwnerUserIdAndSourceFileIdAndMethodAndModelNameAndModelVersionAndThresholdVersion(
                        actor.getId(),
                        source.getId(),
                        DuplicateSearchMethod.IMAGE_PHASH,
                        DuplicateCandidate.NO_MODEL,
                        DuplicateCandidate.NO_MODEL,
                        PHASH_THRESHOLD);
        verify(imagePhashDuplicateCandidateRepository, never()).findCandidates(
                actor.getId(),
                source.getId(),
                "0000000000000000",
                properties.getImagePhash().getMaxDistance(),
                properties.getImagePhash().getMaxCandidates());
    }

    @Test
    void phashReadReturnsEmptyWithoutFallbackWhenMatchingRefreshMarkerExists() {
        when(imageFingerprintRepository.findByFileId(source.getId()))
                .thenReturn(Optional.of(ImageFingerprint.builder().file(source).phash("0000000000000000").build()));
        when(duplicateCandidateRefreshRepository
                .existsByOwnerUserIdAndSourceFileIdAndMethodAndModelNameAndModelVersionAndThresholdVersion(
                        actor.getId(),
                        source.getId(),
                        DuplicateSearchMethod.IMAGE_PHASH,
                        DuplicateCandidate.NO_MODEL,
                        DuplicateCandidate.NO_MODEL,
                        PHASH_THRESHOLD))
                .thenReturn(true);

        var response = service.searchDuplicatesForFile(
                source.getId(),
                List.of(DuplicateSearchMethod.IMAGE_PHASH),
                actor.getId());

        assertThat(response.methods().getFirst().matches()).isEmpty();
        verify(imagePhashDuplicateCandidateRepository, never()).findCandidates(
                actor.getId(),
                source.getId(),
                "0000000000000000",
                properties.getImagePhash().getMaxDistance(),
                properties.getImagePhash().getMaxCandidates());
    }

    @Test
    void phashReadUsesFallbackWhenNoMatchingRefreshMarkerExists() {
        UUID fallbackMatch = UUID.randomUUID();
        when(imageFingerprintRepository.findByFileId(source.getId()))
                .thenReturn(Optional.of(ImageFingerprint.builder().file(source).phash("0000000000000000").build()));
        when(imagePhashDuplicateCandidateRepository.findCandidates(
                actor.getId(),
                source.getId(),
                "0000000000000000",
                properties.getImagePhash().getMaxDistance(),
                properties.getImagePhash().getMaxCandidates()))
                .thenReturn(List.of(phashProjection(fallbackMatch, 1)));

        var response = service.searchDuplicatesForFile(
                source.getId(),
                List.of(DuplicateSearchMethod.IMAGE_PHASH),
                actor.getId());

        assertThat(response.methods().getFirst().matches())
                .extracting(DuplicateSearchResponse.DuplicateMatchResponse::fileId)
                .containsExactly(fallbackMatch);
        verify(duplicateCandidateRefreshRepository)
                .existsByOwnerUserIdAndSourceFileIdAndMethodAndModelNameAndModelVersionAndThresholdVersion(
                        actor.getId(),
                        source.getId(),
                        DuplicateSearchMethod.IMAGE_PHASH,
                        DuplicateCandidate.NO_MODEL,
                        DuplicateCandidate.NO_MODEL,
                        PHASH_THRESHOLD);
    }

    @Test
    void imageEmbeddingReadUsesCurrentModelMarkerBeforeSuppressingFallback() {
        FileEmbedding sourceEmbedding = FileEmbedding.builder()
                .file(source)
                .modelName(appProperties.getEmbedding().getModelName())
                .modelVersion(appProperties.getEmbedding().getModelVersion())
                .dimension(appProperties.getEmbedding().getDimension())
                .embedding(new float[appProperties.getEmbedding().getDimension()])
                .build();
        when(fileEmbeddingRepository.findByFileIdAndModelNameAndModelVersion(
                source.getId(),
                appProperties.getEmbedding().getModelName(),
                appProperties.getEmbedding().getModelVersion()))
                .thenReturn(Optional.of(sourceEmbedding));
        when(duplicateCandidateRefreshRepository
                .existsByOwnerUserIdAndSourceFileIdAndMethodAndModelNameAndModelVersionAndThresholdVersion(
                        actor.getId(),
                        source.getId(),
                        DuplicateSearchMethod.IMAGE_EMBEDDING,
                        sourceEmbedding.getModelName(),
                        sourceEmbedding.getModelVersion(),
                        EMBEDDING_THRESHOLD))
                .thenReturn(true);

        var response = service.searchDuplicatesForFile(
                source.getId(),
                List.of(DuplicateSearchMethod.IMAGE_EMBEDDING),
                actor.getId());

        assertThat(response.methods().getFirst().matches()).isEmpty();
        verify(imageEmbeddingDuplicateCandidateRepository, never()).findCandidates(
                actor.getId(),
                source.getId(),
                sourceEmbedding.getModelName(),
                sourceEmbedding.getModelVersion(),
                sourceEmbedding.getDimension(),
                properties.getImageEmbedding().getMaxDistance(),
                properties.getImageEmbedding().getMaxCandidates());
    }

    @Test
    void imageEmbeddingReadFallsBackWhenCurrentModelMarkerIsMissing() {
        UUID fallbackMatch = UUID.randomUUID();
        FileEmbedding sourceEmbedding = FileEmbedding.builder()
                .file(source)
                .modelName(appProperties.getEmbedding().getModelName())
                .modelVersion(appProperties.getEmbedding().getModelVersion())
                .dimension(appProperties.getEmbedding().getDimension())
                .embedding(new float[appProperties.getEmbedding().getDimension()])
                .build();
        when(fileEmbeddingRepository.findByFileIdAndModelNameAndModelVersion(
                source.getId(),
                appProperties.getEmbedding().getModelName(),
                appProperties.getEmbedding().getModelVersion()))
                .thenReturn(Optional.of(sourceEmbedding));
        when(imageEmbeddingDuplicateCandidateRepository.findCandidates(
                actor.getId(),
                source.getId(),
                sourceEmbedding.getModelName(),
                sourceEmbedding.getModelVersion(),
                sourceEmbedding.getDimension(),
                properties.getImageEmbedding().getMaxDistance(),
                properties.getImageEmbedding().getMaxCandidates()))
                .thenReturn(List.of(embeddingProjection(fallbackMatch, 0.1)));

        var response = service.searchDuplicatesForFile(
                source.getId(),
                List.of(DuplicateSearchMethod.IMAGE_EMBEDDING),
                actor.getId());

        assertThat(response.methods().getFirst().matches())
                .extracting(DuplicateSearchResponse.DuplicateMatchResponse::fileId)
                .containsExactly(fallbackMatch);
    }

    private PhashDuplicateCandidateProjection phashProjection(UUID fileId, int distance) {
        return new PhashDuplicateCandidateProjection() {
            @Override
            public UUID getFileId() {
                return fileId;
            }

            @Override
            public String getName() {
                return "match.png";
            }

            @Override
            public String getMimeType() {
                return "image/png";
            }

            @Override
            public Long getSize() {
                return 10L;
            }

            @Override
            public Integer getDistance() {
                return distance;
            }
        };
    }

    private EmbeddingDuplicateCandidateProjection embeddingProjection(UUID fileId, double distance) {
        return new EmbeddingDuplicateCandidateProjection() {
            @Override
            public UUID getFileId() {
                return fileId;
            }

            @Override
            public String getName() {
                return "match.png";
            }

            @Override
            public String getMimeType() {
                return "image/png";
            }

            @Override
            public Long getSize() {
                return 10L;
            }

            @Override
            public Double getDistance() {
                return distance;
            }
        };
    }
}

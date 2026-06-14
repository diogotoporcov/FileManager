package com.filemanager.api.duplicate.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.filemanager.api.config.AppProperties;
import com.filemanager.api.duplicate.domain.DuplicateMethodStatus;
import com.filemanager.api.duplicate.domain.DuplicateSearchMethod;
import com.filemanager.api.duplicate.persistence.AudioDuplicateCandidateRepository;
import com.filemanager.api.duplicate.persistence.EmbeddingDuplicateCandidateProjection;
import com.filemanager.api.duplicate.persistence.ExactDuplicateCandidateRepository;
import com.filemanager.api.duplicate.persistence.ExactDuplicateGroupRepository;
import com.filemanager.api.duplicate.persistence.ImageEmbeddingDuplicateCandidateRepository;
import com.filemanager.api.duplicate.persistence.ImagePhashDuplicateCandidateRepository;
import com.filemanager.api.duplicate.persistence.PhashDuplicateCandidateProjection;
import com.filemanager.api.duplicate.web.DuplicateGroupSearchRequest;
import com.filemanager.api.duplicate.web.DuplicateGroupSearchResponse;
import com.filemanager.api.duplicate.web.DuplicateSearchPageRequest;
import com.filemanager.api.duplicate.web.DuplicateSearchResponse;
import com.filemanager.api.file.domain.FileEntity;
import com.filemanager.api.file.persistence.FileRepository;
import com.filemanager.api.identity.domain.User;
import com.filemanager.api.observability.application.FileManagerMetrics;
import com.filemanager.api.processing.domain.result.AudioFingerprint;
import com.filemanager.api.processing.domain.result.FileEmbedding;
import com.filemanager.api.processing.domain.result.FileFingerprint;
import com.filemanager.api.processing.domain.result.ImageFingerprint;
import com.filemanager.api.processing.persistence.result.AudioFingerprintRepository;
import com.filemanager.api.processing.persistence.result.FileEmbeddingRepository;
import com.filemanager.api.processing.persistence.result.FileFingerprintRepository;
import com.filemanager.api.processing.persistence.result.ImageFingerprintRepository;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class DuplicateSearchServiceTest {
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
    private ImagePhashDuplicateCandidateRepository imagePhashDuplicateCandidateRepository;
    @Mock
    private ImageEmbeddingDuplicateCandidateRepository imageEmbeddingDuplicateCandidateRepository;
    @Mock
    private AudioDuplicateCandidateRepository audioDuplicateCandidateRepository;
    @Mock
    private FileManagerMetrics fileManagerMetrics;

    private DuplicateSearchService service;
    private DuplicateDetectionProperties properties;
    private AppProperties appProperties;
    private User actor;
    private OffsetDateTime createdAt;

    @BeforeEach
    void setUp() {
        properties = new DuplicateDetectionProperties();
        properties.getImageEmbedding().setMaxSearchWindow(properties.getImageEmbedding().getSearchWindow());
        appProperties = new AppProperties();
        service = new DuplicateSearchService(
                fileRepository,
                fileFingerprintRepository,
                imageFingerprintRepository,
                fileEmbeddingRepository,
                audioFingerprintRepository,
                exactDuplicateCandidateRepository,
                exactDuplicateGroupRepository,
                imagePhashDuplicateCandidateRepository,
                imageEmbeddingDuplicateCandidateRepository,
                audioDuplicateCandidateRepository,
                properties,
                appProperties,
                fileManagerMetrics);
        actor = User.builder().id(UUID.randomUUID()).build();
        createdAt = OffsetDateTime.parse("2026-06-14T08:00:00Z");
    }

    @Test
    void exactSearchReturnsOnePageAndNextCursorWhenMoreCandidatesExist() {
        FileEntity source = sourceFile("text/plain");
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        String hash = "hash";
        when(fileRepository.findEligibleById(source.getId())).thenReturn(Optional.of(source));
        when(fileFingerprintRepository.findByFileIdAndAlgorithm(source.getId(), FileFingerprint.FingerprintAlgorithm.SHA256))
                .thenReturn(Optional.of(FileFingerprint.builder()
                        .file(source)
                        .algorithm(FileFingerprint.FingerprintAlgorithm.SHA256)
                        .hashValue(hash)
                        .build()));
        when(exactDuplicateCandidateRepository.findCandidates(
                org.mockito.ArgumentMatchers.eq(actor.getId()),
                org.mockito.ArgumentMatchers.eq(source.getId()),
                org.mockito.ArgumentMatchers.eq(FileFingerprint.FingerprintAlgorithm.SHA256),
                org.mockito.ArgumentMatchers.eq(hash),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(List.of(
                        new com.filemanager.api.duplicate.persistence.ExactDuplicateCandidateProjection(
                                first,
                                "first.txt",
                                "text/plain",
                                1L,
                                FileFingerprint.FingerprintAlgorithm.SHA256,
                                hash,
                                createdAt),
                        new com.filemanager.api.duplicate.persistence.ExactDuplicateCandidateProjection(
                                second,
                                "second.txt",
                                "text/plain",
                                1L,
                                FileFingerprint.FingerprintAlgorithm.SHA256,
                                hash,
                                createdAt.minusSeconds(1))));

        var response = service.searchDuplicatesForFile(
                source.getId(),
                List.of(DuplicateSearchMethod.EXACT),
                actor.getId(),
                new DuplicateSearchPageRequest(1, null));

        DuplicateSearchResponse.DuplicateMethodResultResponse method = response.methods().getFirst();
        assertThat(method.matches())
                .extracting(DuplicateSearchResponse.DuplicateMatchResponse::fileId)
                .containsExactly(first);
        assertThat(method.pageSize()).isEqualTo(1);
        assertThat(method.hasMore()).isTrue();
        assertThat(method.nextCursor()).isNotBlank();
    }

    @Test
    void imagePhashSearchRunsDirectEvidenceQuery() {
        FileEntity source = sourceFile("image/png");
        UUID match = UUID.randomUUID();
        when(fileRepository.findEligibleById(source.getId())).thenReturn(Optional.of(source));
        when(imageFingerprintRepository.findByFileId(source.getId()))
                .thenReturn(Optional.of(ImageFingerprint.builder().file(source).phash("0000000000000000").build()));
        when(imagePhashDuplicateCandidateRepository.findCandidates(
                actor.getId(),
                source.getId(),
                "0000000000000000",
                properties.getImagePhash().getMaxDistance(),
                null,
                null,
                null,
                properties.getImagePhash().getPageSize() + 1))
                .thenReturn(List.of(phashProjection(match, 3)));

        var response = service.searchDuplicatesForFile(
                source.getId(),
                List.of(DuplicateSearchMethod.IMAGE_PHASH),
                actor.getId());

        assertThat(response.methods().getFirst().matches())
                .extracting(DuplicateSearchResponse.DuplicateMatchResponse::fileId)
                .containsExactly(match);
        assertThat(response.methods().getFirst().hasMore()).isFalse();
    }

    @Test
    void imageEmbeddingSearchRunsDirectEvidenceQuery() {
        FileEntity source = sourceFile("image/jpeg");
        UUID match = UUID.randomUUID();
        FileEmbedding embedding = FileEmbedding.builder()
                .file(source)
                .modelName(appProperties.getEmbedding().getModelName())
                .modelVersion(appProperties.getEmbedding().getModelVersion())
                .dimension(appProperties.getEmbedding().getDimension())
                .embedding(new float[appProperties.getEmbedding().getDimension()])
                .build();
        when(fileRepository.findEligibleById(source.getId())).thenReturn(Optional.of(source));
        when(fileEmbeddingRepository.findByFileIdAndModelNameAndModelVersion(
                source.getId(),
                appProperties.getEmbedding().getModelName(),
                appProperties.getEmbedding().getModelVersion()))
                .thenReturn(Optional.of(embedding));
        when(imageEmbeddingDuplicateCandidateRepository.findCandidates(
                actor.getId(),
                source.getId(),
                embedding.getModelName(),
                embedding.getModelVersion(),
                embedding.getDimension(),
                "[0.0" + ",0.0".repeat(appProperties.getEmbedding().getDimension() - 1) + "]",
                properties.getImageEmbedding().getMaxDistance(),
                properties.getImageEmbedding().getSearchWindow(),
                null,
                null,
                null,
                properties.getImageEmbedding().getPageSize() + 1))
                .thenReturn(List.of(embeddingProjection(match, 0.1)));

        var response = service.searchDuplicatesForFile(
                source.getId(),
                List.of(DuplicateSearchMethod.IMAGE_EMBEDDING),
                actor.getId());

        assertThat(response.methods().getFirst().matches())
                .extracting(DuplicateSearchResponse.DuplicateMatchResponse::fileId)
                .containsExactly(match);
    }

    @Test
    void audioFingerprintSearchRunsDirectEvidenceQuery() {
        FileEntity source = sourceFile("audio/mpeg");
        UUID match = UUID.randomUUID();
        AudioFingerprint fingerprint = AudioFingerprint.builder()
                .file(source)
                .durationMs(1_000L)
                .codec("mp3")
                .sampleRate(44_100)
                .channels(2)
                .fingerprint("abc")
                .fingerprintHash("hash")
                .fingerprintAlgorithm("chromaprint")
                .fingerprintVersion("fpcalc")
                .fingerprintDurationSeconds(60)
                .build();
        when(fileRepository.findEligibleById(source.getId())).thenReturn(Optional.of(source));
        when(audioFingerprintRepository.findByFileId(source.getId())).thenReturn(Optional.of(fingerprint));
        when(audioDuplicateCandidateRepository.findCandidates(
                org.mockito.ArgumentMatchers.eq(actor.getId()),
                org.mockito.ArgumentMatchers.eq(source.getId()),
                org.mockito.ArgumentMatchers.eq("chromaprint"),
                org.mockito.ArgumentMatchers.eq("fpcalc"),
                org.mockito.ArgumentMatchers.eq("hash"),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(List.of(new com.filemanager.api.duplicate.persistence.AudioDuplicateCandidateProjection(
                        match,
                        "match.mp3",
                        "audio/mpeg",
                        1L,
                        "chromaprint",
                        "fpcalc",
                        "hash",
                        createdAt)));

        var response = service.searchDuplicatesForFile(
                source.getId(),
                List.of(DuplicateSearchMethod.AUDIO_FINGERPRINT),
                actor.getId());

        assertThat(response.methods().getFirst().matches())
                .extracting(DuplicateSearchResponse.DuplicateMatchResponse::fileId)
                .containsExactly(match);
    }

    @Test
    void imagePhashSearchUsesKeysetCursorAndDoesNotTreatPageSizeAsTotalCap() {
        FileEntity source = sourceFile("image/png");
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(fileRepository.findEligibleById(source.getId())).thenReturn(Optional.of(source));
        when(imageFingerprintRepository.findByFileId(source.getId()))
                .thenReturn(Optional.of(ImageFingerprint.builder().file(source).phash("0000000000000000").build()));
        when(imagePhashDuplicateCandidateRepository.findCandidates(
                actor.getId(),
                source.getId(),
                "0000000000000000",
                properties.getImagePhash().getMaxDistance(),
                null,
                null,
                null,
                2))
                .thenReturn(List.of(phashProjection(first, 1, createdAt), phashProjection(second, 2, createdAt.minusSeconds(1))));
        when(imagePhashDuplicateCandidateRepository.findCandidates(
                actor.getId(),
                source.getId(),
                "0000000000000000",
                properties.getImagePhash().getMaxDistance(),
                1,
                createdAt.toString(),
                first.toString(),
                2))
                .thenReturn(List.of(phashProjection(second, 2, createdAt.minusSeconds(1))));

        var firstPage = service.searchDuplicatesForFile(
                source.getId(),
                List.of(DuplicateSearchMethod.IMAGE_PHASH),
                actor.getId(),
                new DuplicateSearchPageRequest(1, null));
        var secondPage = service.searchDuplicatesForFile(
                source.getId(),
                List.of(DuplicateSearchMethod.IMAGE_PHASH),
                actor.getId(),
                new DuplicateSearchPageRequest(1, firstPage.methods().getFirst().nextCursor()));

        assertThat(firstPage.methods().getFirst().matches())
                .extracting(DuplicateSearchResponse.DuplicateMatchResponse::fileId)
                .containsExactly(first);
        assertThat(firstPage.methods().getFirst().hasMore()).isTrue();
        assertThat(secondPage.methods().getFirst().matches())
                .extracting(DuplicateSearchResponse.DuplicateMatchResponse::fileId)
                .containsExactly(second);
        assertThat(secondPage.methods().getFirst().hasMore()).isFalse();
    }

    @Test
    void imageEmbeddingSearchReturnsPaginatedMetadataWhenMoreCandidatesExist() {
        FileEntity source = sourceFile("image/jpeg");
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        FileEmbedding embedding = FileEmbedding.builder()
                .file(source)
                .modelName(appProperties.getEmbedding().getModelName())
                .modelVersion(appProperties.getEmbedding().getModelVersion())
                .dimension(appProperties.getEmbedding().getDimension())
                .embedding(new float[appProperties.getEmbedding().getDimension()])
                .build();
        when(fileRepository.findEligibleById(source.getId())).thenReturn(Optional.of(source));
        when(fileEmbeddingRepository.findByFileIdAndModelNameAndModelVersion(
                source.getId(),
                appProperties.getEmbedding().getModelName(),
                appProperties.getEmbedding().getModelVersion()))
                .thenReturn(Optional.of(embedding));
        when(imageEmbeddingDuplicateCandidateRepository.findCandidates(
                org.mockito.ArgumentMatchers.eq(actor.getId()),
                org.mockito.ArgumentMatchers.eq(source.getId()),
                org.mockito.ArgumentMatchers.eq(embedding.getModelName()),
                org.mockito.ArgumentMatchers.eq(embedding.getModelVersion()),
                org.mockito.ArgumentMatchers.eq(embedding.getDimension()),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(properties.getImageEmbedding().getMaxDistance()),
                org.mockito.ArgumentMatchers.eq(properties.getImageEmbedding().getSearchWindow()),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq(2)))
                .thenReturn(List.of(
                        embeddingProjection(first, 0.01, createdAt),
                        embeddingProjection(second, 0.02, createdAt.minusSeconds(1))));

        var response = service.searchDuplicatesForFile(
                source.getId(),
                List.of(DuplicateSearchMethod.IMAGE_EMBEDDING),
                actor.getId(),
                new DuplicateSearchPageRequest(1, null));

        DuplicateSearchResponse.DuplicateMethodResultResponse method = response.methods().getFirst();
        assertThat(method.matches())
                .extracting(DuplicateSearchResponse.DuplicateMatchResponse::fileId)
                .containsExactly(first);
        assertThat(method.hasMore()).isTrue();
        assertThat(method.nextCursor()).isNotBlank();
    }

    @Test
    void imageEmbeddingSearchExpandsAnnWindowToDetermineMoreResults() {
        properties.getImageEmbedding().setSearchWindow(2);
        properties.getImageEmbedding().setMaxSearchWindow(4);
        FileEntity source = sourceFile("image/jpeg");
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        FileEmbedding embedding = FileEmbedding.builder()
                .file(source)
                .modelName(appProperties.getEmbedding().getModelName())
                .modelVersion(appProperties.getEmbedding().getModelVersion())
                .dimension(appProperties.getEmbedding().getDimension())
                .embedding(new float[appProperties.getEmbedding().getDimension()])
                .build();
        when(fileRepository.findEligibleById(source.getId())).thenReturn(Optional.of(source));
        when(fileEmbeddingRepository.findByFileIdAndModelNameAndModelVersion(
                source.getId(),
                appProperties.getEmbedding().getModelName(),
                appProperties.getEmbedding().getModelVersion()))
                .thenReturn(Optional.of(embedding));
        when(imageEmbeddingDuplicateCandidateRepository.findCandidates(
                org.mockito.ArgumentMatchers.eq(actor.getId()),
                org.mockito.ArgumentMatchers.eq(source.getId()),
                org.mockito.ArgumentMatchers.eq(embedding.getModelName()),
                org.mockito.ArgumentMatchers.eq(embedding.getModelVersion()),
                org.mockito.ArgumentMatchers.eq(embedding.getDimension()),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(properties.getImageEmbedding().getMaxDistance()),
                org.mockito.ArgumentMatchers.eq(2),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq(2)))
                .thenReturn(List.of(embeddingProjection(first, 0.01, createdAt)));
        when(imageEmbeddingDuplicateCandidateRepository.findCandidates(
                org.mockito.ArgumentMatchers.eq(actor.getId()),
                org.mockito.ArgumentMatchers.eq(source.getId()),
                org.mockito.ArgumentMatchers.eq(embedding.getModelName()),
                org.mockito.ArgumentMatchers.eq(embedding.getModelVersion()),
                org.mockito.ArgumentMatchers.eq(embedding.getDimension()),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(properties.getImageEmbedding().getMaxDistance()),
                org.mockito.ArgumentMatchers.eq(4),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq(2)))
                .thenReturn(List.of(
                        embeddingProjection(first, 0.01, createdAt),
                        embeddingProjection(second, 0.02, createdAt.minusSeconds(1))));

        var response = service.searchDuplicatesForFile(
                source.getId(),
                List.of(DuplicateSearchMethod.IMAGE_EMBEDDING),
                actor.getId(),
                new DuplicateSearchPageRequest(1, null));

        DuplicateSearchResponse.DuplicateMethodResultResponse method = response.methods().getFirst();
        assertThat(method.matches())
                .extracting(DuplicateSearchResponse.DuplicateMatchResponse::fileId)
                .containsExactly(first);
        assertThat(method.hasMore()).isTrue();
        assertThat(method.nextCursor()).isNotBlank();
    }

    @Test
    void imageEmbeddingSearchKeepsHasMoreWhenBoundedAnnWindowIsFull() {
        properties.getImageEmbedding().setSearchWindow(2);
        properties.getImageEmbedding().setMaxSearchWindow(2);
        FileEntity source = sourceFile("image/jpeg");
        UUID first = UUID.randomUUID();
        FileEmbedding embedding = FileEmbedding.builder()
                .file(source)
                .modelName(appProperties.getEmbedding().getModelName())
                .modelVersion(appProperties.getEmbedding().getModelVersion())
                .dimension(appProperties.getEmbedding().getDimension())
                .embedding(new float[appProperties.getEmbedding().getDimension()])
                .build();
        when(fileRepository.findEligibleById(source.getId())).thenReturn(Optional.of(source));
        when(fileEmbeddingRepository.findByFileIdAndModelNameAndModelVersion(
                source.getId(),
                appProperties.getEmbedding().getModelName(),
                appProperties.getEmbedding().getModelVersion()))
                .thenReturn(Optional.of(embedding));
        when(imageEmbeddingDuplicateCandidateRepository.findCandidates(
                org.mockito.ArgumentMatchers.eq(actor.getId()),
                org.mockito.ArgumentMatchers.eq(source.getId()),
                org.mockito.ArgumentMatchers.eq(embedding.getModelName()),
                org.mockito.ArgumentMatchers.eq(embedding.getModelVersion()),
                org.mockito.ArgumentMatchers.eq(embedding.getDimension()),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(properties.getImageEmbedding().getMaxDistance()),
                org.mockito.ArgumentMatchers.eq(2),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq(2)))
                .thenReturn(List.of(embeddingProjection(first, 0.01, createdAt)));

        var response = service.searchDuplicatesForFile(
                source.getId(),
                List.of(DuplicateSearchMethod.IMAGE_EMBEDDING),
                actor.getId(),
                new DuplicateSearchPageRequest(1, null));

        DuplicateSearchResponse.DuplicateMethodResultResponse method = response.methods().getFirst();
        assertThat(method.matches())
                .extracting(DuplicateSearchResponse.DuplicateMatchResponse::fileId)
                .containsExactly(first);
        assertThat(method.hasMore()).isTrue();
        assertThat(method.nextCursor()).isNotBlank();
    }

    @Test
    void audioFingerprintSearchReturnsPaginatedMetadataWhenMoreCandidatesExist() {
        FileEntity source = sourceFile("audio/mpeg");
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        AudioFingerprint fingerprint = AudioFingerprint.builder()
                .file(source)
                .durationMs(1_000L)
                .codec("mp3")
                .sampleRate(44_100)
                .channels(2)
                .fingerprint("abc")
                .fingerprintHash("hash")
                .fingerprintAlgorithm("chromaprint")
                .fingerprintVersion("fpcalc")
                .fingerprintDurationSeconds(60)
                .build();
        when(fileRepository.findEligibleById(source.getId())).thenReturn(Optional.of(source));
        when(audioFingerprintRepository.findByFileId(source.getId())).thenReturn(Optional.of(fingerprint));
        when(audioDuplicateCandidateRepository.findCandidates(
                org.mockito.ArgumentMatchers.eq(actor.getId()),
                org.mockito.ArgumentMatchers.eq(source.getId()),
                org.mockito.ArgumentMatchers.eq("chromaprint"),
                org.mockito.ArgumentMatchers.eq("fpcalc"),
                org.mockito.ArgumentMatchers.eq("hash"),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(List.of(
                        new com.filemanager.api.duplicate.persistence.AudioDuplicateCandidateProjection(
                                first,
                                "first.mp3",
                                "audio/mpeg",
                                1L,
                                "chromaprint",
                                "fpcalc",
                                "hash",
                                createdAt),
                        new com.filemanager.api.duplicate.persistence.AudioDuplicateCandidateProjection(
                                second,
                                "second.mp3",
                                "audio/mpeg",
                                1L,
                                "chromaprint",
                                "fpcalc",
                                "hash",
                                createdAt.minusSeconds(1))));

        var response = service.searchDuplicatesForFile(
                source.getId(),
                List.of(DuplicateSearchMethod.AUDIO_FINGERPRINT),
                actor.getId(),
                new DuplicateSearchPageRequest(1, null));

        DuplicateSearchResponse.DuplicateMethodResultResponse method = response.methods().getFirst();
        assertThat(method.matches())
                .extracting(DuplicateSearchResponse.DuplicateMatchResponse::fileId)
                .containsExactly(first);
        assertThat(method.hasMore()).isTrue();
        assertThat(method.nextCursor()).isNotBlank();
    }

    @Test
    void cursorRequestWithMultipleMethodsIsRejected() {
        String cursor = cursor(DuplicateSearchMethod.EXACT, createdAt, UUID.randomUUID(), null);

        assertThatThrownBy(() -> service.searchDuplicatesForFile(
                        UUID.randomUUID(),
                        List.of(DuplicateSearchMethod.EXACT, DuplicateSearchMethod.IMAGE_PHASH),
                        actor.getId(),
                        new DuplicateSearchPageRequest(10, cursor)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly one method");

        verify(fileRepository, never()).findEligibleById(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void cursorRequestWithoutExplicitMethodIsRejected() {
        String cursor = cursor(DuplicateSearchMethod.EXACT, createdAt, UUID.randomUUID(), null);

        assertThatThrownBy(() -> service.searchDuplicatesForFile(
                        UUID.randomUUID(),
                        null,
                        actor.getId(),
                        new DuplicateSearchPageRequest(10, cursor)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly one method");

        verify(fileRepository, never()).findEligibleById(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void cursorMethodMismatchIsRejected() {
        String cursor = cursor(DuplicateSearchMethod.IMAGE_PHASH, createdAt, UUID.randomUUID(), 1.0);

        assertThatThrownBy(() -> service.searchDuplicatesForFile(
                        UUID.randomUUID(),
                        List.of(DuplicateSearchMethod.EXACT),
                        actor.getId(),
                        new DuplicateSearchPageRequest(10, cursor)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("method does not match");

        verify(fileRepository, never()).findEligibleById(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void invalidCursorIsRejectedAsClientErrorException() {
        assertThatThrownBy(() -> service.searchDuplicatesForFile(
                        UUID.randomUUID(),
                        List.of(DuplicateSearchMethod.EXACT),
                        actor.getId(),
                        new DuplicateSearchPageRequest(10, "%%%%")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid duplicate search cursor");

        verify(fileRepository, never()).findEligibleById(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void cursorWithInvalidDateIsRejectedAsClientErrorException() {
        String cursor = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("EXACT|not-a-date|" + UUID.randomUUID() + "|").getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.searchDuplicatesForFile(
                        UUID.randomUUID(),
                        List.of(DuplicateSearchMethod.EXACT),
                        actor.getId(),
                        new DuplicateSearchPageRequest(10, cursor)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid duplicate search cursor");

        verify(fileRepository, never()).findEligibleById(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void groupSearchIsExactOnly() {
        var response = service.searchGroups(
                new DuplicateGroupSearchRequest(
                        List.of(DuplicateSearchMethod.AUDIO_FINGERPRINT, DuplicateSearchMethod.IMAGE_PHASH),
                        null,
                        null,
                        null,
                        null,
                        null),
                actor.getId());

        assertThat(response.methods())
                .extracting(DuplicateGroupSearchResponse.DuplicateGroupMethodResultResponse::status)
                .containsExactly(
                        DuplicateMethodStatus.NOT_SUPPORTED_FOR_GROUPED_SEARCH_YET,
                        DuplicateMethodStatus.NOT_SUPPORTED_FOR_GROUPED_SEARCH_YET);
        verify(exactDuplicateGroupRepository, never()).findOwnerGroupKeys(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void videoFileDoesNotRunNonExactMethods() {
        FileEntity source = sourceFile("video/mp4");
        when(fileRepository.findEligibleById(source.getId())).thenReturn(Optional.of(source));

        var response = service.searchDuplicatesForFile(
                source.getId(),
                List.of(
                        DuplicateSearchMethod.IMAGE_PHASH,
                        DuplicateSearchMethod.IMAGE_EMBEDDING,
                        DuplicateSearchMethod.AUDIO_FINGERPRINT),
                actor.getId());

        assertThat(response.methods())
                .extracting(DuplicateSearchResponse.DuplicateMethodResultResponse::status)
                .containsOnly(DuplicateMethodStatus.UNSUPPORTED_FOR_FILE_TYPE);
    }

    private FileEntity sourceFile(String mimeType) {
        return FileEntity.builder()
                .id(UUID.randomUUID())
                .ownerUser(actor)
                .mimeType(mimeType)
                .name("source")
                .size(1L)
                .build();
    }

    private static PhashDuplicateCandidateProjection phashProjection(UUID fileId, int distance) {
        return phashProjection(fileId, distance, OffsetDateTime.parse("2026-06-14T08:00:00Z"));
    }

    private static PhashDuplicateCandidateProjection phashProjection(UUID fileId, int distance, OffsetDateTime createdAt) {
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
                return 1L;
            }

            @Override
            public Integer getDistance() {
                return distance;
            }

            @Override
            public OffsetDateTime getCreatedAt() {
                return createdAt;
            }
        };
    }

    private static EmbeddingDuplicateCandidateProjection embeddingProjection(UUID fileId, double distance) {
        return embeddingProjection(fileId, distance, OffsetDateTime.parse("2026-06-14T08:00:00Z"));
    }

    private static EmbeddingDuplicateCandidateProjection embeddingProjection(
            UUID fileId,
            double distance,
            OffsetDateTime createdAt) {
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
                return 1L;
            }

            @Override
            public Double getDistance() {
                return distance;
            }

            @Override
            public OffsetDateTime getCreatedAt() {
                return createdAt;
            }
        };
    }

    private static String cursor(
            DuplicateSearchMethod method,
            OffsetDateTime createdAt,
            UUID fileId,
            Double distance) {
        String rawCursor = method.name()
                + "|" + createdAt
                + "|" + fileId
                + "|" + (distance == null ? "" : distance);

        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(rawCursor.getBytes(StandardCharsets.UTF_8));
    }
}

package com.filemanager.api.duplicate.application;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.filemanager.api.duplicate.web.DuplicateSearchResponse;
import com.filemanager.api.file.domain.FileEntity;
import com.filemanager.api.file.persistence.FileRepository;
import com.filemanager.api.identity.domain.User;
import com.filemanager.api.observability.application.FileManagerMetrics;
import com.filemanager.api.processing.domain.result.AudioFingerprint;
import com.filemanager.api.processing.domain.result.FileEmbedding;
import com.filemanager.api.processing.domain.result.ImageFingerprint;
import com.filemanager.api.processing.persistence.result.AudioFingerprintRepository;
import com.filemanager.api.processing.persistence.result.FileEmbeddingRepository;
import com.filemanager.api.processing.persistence.result.FileFingerprintRepository;
import com.filemanager.api.processing.persistence.result.ImageFingerprintRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

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
                imagePhashDuplicateCandidateRepository,
                imageEmbeddingDuplicateCandidateRepository,
                audioDuplicateCandidateRepository,
                properties,
                appProperties,
                fileManagerMetrics);
        actor = User.builder().id(UUID.randomUUID()).build();
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
                properties.getImagePhash().getMaxCandidates()))
                .thenReturn(List.of(phashProjection(match, 3)));

        var response = service.searchDuplicatesForFile(
                source.getId(),
                List.of(DuplicateSearchMethod.IMAGE_PHASH),
                actor.getId());

        assertThat(response.methods().getFirst().matches())
                .extracting(DuplicateSearchResponse.DuplicateMatchResponse::fileId)
                .containsExactly(match);
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
                properties.getImageEmbedding().getMaxDistance(),
                properties.getImageEmbedding().getMaxCandidates()))
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
                .fingerprintAlgorithm("chromaprint")
                .fingerprintVersion("fpcalc")
                .fingerprintDurationSeconds(60)
                .build();
        when(fileRepository.findEligibleById(source.getId())).thenReturn(Optional.of(source));
        when(audioFingerprintRepository.findByFileId(source.getId())).thenReturn(Optional.of(fingerprint));
        when(audioDuplicateCandidateRepository.findCandidates(
                actor.getId(),
                source.getId(),
                "chromaprint",
                "fpcalc",
                fingerprint.getFingerprintHash(),
                PageRequest.of(0, properties.getAudioFingerprint().getMaxCandidates())))
                .thenReturn(List.of(new com.filemanager.api.duplicate.persistence.AudioDuplicateCandidateProjection(
                        match,
                        "match.mp3",
                        "audio/mpeg",
                        1L,
                        "chromaprint",
                        "fpcalc",
                        fingerprint.getFingerprintHash())));

        var response = service.searchDuplicatesForFile(
                source.getId(),
                List.of(DuplicateSearchMethod.AUDIO_FINGERPRINT),
                actor.getId());

        assertThat(response.methods().getFirst().matches())
                .extracting(DuplicateSearchResponse.DuplicateMatchResponse::fileId)
                .containsExactly(match);
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
        };
    }

    private static EmbeddingDuplicateCandidateProjection embeddingProjection(UUID fileId, double distance) {
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
        };
    }
}

package com.filemanager.api.service;

import com.filemanager.api.auth.AccessControlService;
import com.filemanager.api.config.AppProperties;
import com.filemanager.api.dto.DuplicateGroupResponse;
import com.filemanager.api.dto.DuplicateSearchMethod;
import com.filemanager.api.dto.FileDuplicateSearchResponse;
import com.filemanager.api.entity.FileEmbedding;
import com.filemanager.api.entity.FileEntity;
import com.filemanager.api.entity.FileFingerprint;
import com.filemanager.api.entity.ImageFingerprint;
import com.filemanager.api.entity.Organization;
import com.filemanager.api.entity.User;
import com.filemanager.api.exception.AccessDeniedException;
import com.filemanager.api.mapper.FileSummaryResponseMapper;
import com.filemanager.api.port.EmbeddingSimilarityCandidate;
import com.filemanager.api.port.EmbeddingSimilarityPairCandidate;
import com.filemanager.api.port.EmbeddingSimilarityPairSearchPort;
import com.filemanager.api.port.EmbeddingSimilarityPairSearchRequest;
import com.filemanager.api.port.EmbeddingSimilaritySearchPort;
import com.filemanager.api.port.EmbeddingSimilaritySearchRequest;
import com.filemanager.api.port.SimilarImageCandidate;
import com.filemanager.api.port.SimilarImagePairCandidate;
import com.filemanager.api.port.SimilarImagePairSearchPort;
import com.filemanager.api.port.SimilarImagePairSearchRequest;
import com.filemanager.api.port.SimilarImageSearchPort;
import com.filemanager.api.port.SimilarImageSearchRequest;
import com.filemanager.api.repository.FileEmbeddingRepository;
import com.filemanager.api.repository.FileFingerprintRepository;
import com.filemanager.api.repository.FileRepository;
import com.filemanager.api.repository.ImageFingerprintRepository;
import com.filemanager.api.repository.DuplicateHashGroupProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.filemanager.api.entity.FileFingerprint.FingerprintAlgorithm.SHA256;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DuplicateSearchServiceTest {

    private static final String HASH = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    @Mock
    private FileRepository fileRepository;
    @Mock
    private FileFingerprintRepository fileFingerprintRepository;
    @Mock
    private ImageFingerprintRepository imageFingerprintRepository;
    @Mock
    private FileEmbeddingRepository fileEmbeddingRepository;
    @Mock
    private SimilarImageSearchPort similarImageSearchPort;
    @Mock
    private SimilarImagePairSearchPort similarImagePairSearchPort;
    @Mock
    private EmbeddingSimilaritySearchPort embeddingSimilaritySearchPort;
    @Mock
    private EmbeddingSimilarityPairSearchPort embeddingSimilarityPairSearchPort;
    @Mock
    private AccessControlService accessControlService;
    @Mock
    private AppProperties appProperties;

    @Spy
    private FileSummaryResponseMapper fileSummaryResponseMapper = new FileSummaryResponseMapper();

    @InjectMocks
    private DuplicateSearchService duplicateSearchService;

    private User user;
    private User otherUser;
    private Organization organization;
    private Organization otherOrganization;

    @BeforeEach
    void setUp() {
        AppProperties.Phash phash = new AppProperties.Phash();
        phash.setThreshold(10);
        phash.setMaxCandidates(50);
        lenient().when(appProperties.getPhash()).thenReturn(phash);

        AppProperties.Embedding embedding = new AppProperties.Embedding();
        embedding.setEnabled(true);
        embedding.setModelName("openai/clip-vit-large-patch14");
        embedding.setModelVersion("1");
        embedding.setDimension(768);
        embedding.setSimilarityThreshold(0.20);
        embedding.setMaxCandidates(25);
        lenient().when(appProperties.getEmbedding()).thenReturn(embedding);

        user = User.builder().id(UUID.randomUUID()).build();
        otherUser = User.builder().id(UUID.randomUUID()).build();
        organization = Organization.builder().id(UUID.randomUUID()).build();
        otherOrganization = Organization.builder().id(UUID.randomUUID()).build();
    }

    @Test
    void findDuplicatesForFile_DefaultsToSha256Only() {
        FileEntity source = userFile("source.jpg", user, 1);
        FileEntity duplicate = userFile("duplicate.jpg", user, 2);

        when(fileRepository.findByIdAndDeletedAtIsNull(source.getId())).thenReturn(Optional.of(source));
        when(fileFingerprintRepository.findByFileIdAndAlgorithm(source.getId(), SHA256))
                .thenReturn(Optional.of(fingerprint(source)));
        when(fileFingerprintRepository.findByAlgorithmAndHashValueAndFileOwnerUserIdAndFileDeletedAtIsNull(
                org.mockito.ArgumentMatchers.eq(SHA256),
                org.mockito.ArgumentMatchers.eq(HASH),
                org.mockito.ArgumentMatchers.eq(user.getId()),
                any()))
                .thenReturn(List.of(fingerprint(source), fingerprint(duplicate)));

        FileDuplicateSearchResponse response = duplicateSearchService.findDuplicatesForFile(
                source.getId(),
                user.getId(),
                null);

        assertThat(response.getOriginalFile().getId()).isEqualTo(source.getId());
        assertThat(response.getMatches()).hasSize(1);
        assertThat(response.getMatches().getFirst().getFile().getId()).isEqualTo(duplicate.getId());
        assertThat(response.getMatches().getFirst().getBestMethod()).isEqualTo(DuplicateSearchMethod.SHA256);
        verifyNoInteractions(similarImageSearchPort, embeddingSimilaritySearchPort);
    }

    @Test
    void findDuplicatesForFile_RejectsCrossUserExactDuplicatesEvenIfRepositoryReturnsThem() {
        FileEntity source = userFile("source.jpg", user, 1);
        FileEntity crossUser = userFile("cross.jpg", otherUser, 2);

        when(fileRepository.findByIdAndDeletedAtIsNull(source.getId())).thenReturn(Optional.of(source));
        when(fileFingerprintRepository.findByFileIdAndAlgorithm(source.getId(), SHA256))
                .thenReturn(Optional.of(fingerprint(source)));
        when(fileFingerprintRepository.findByAlgorithmAndHashValueAndFileOwnerUserIdAndFileDeletedAtIsNull(
                org.mockito.ArgumentMatchers.eq(SHA256),
                org.mockito.ArgumentMatchers.eq(HASH),
                org.mockito.ArgumentMatchers.eq(user.getId()),
                any()))
                .thenReturn(List.of(fingerprint(source), fingerprint(crossUser)));

        FileDuplicateSearchResponse response = duplicateSearchService.findDuplicatesForFile(
                source.getId(),
                user.getId(),
                List.of(DuplicateSearchMethod.SHA256));

        assertThat(response.getMatches()).isEmpty();
    }

    @Test
    void findDuplicatesForFile_AllowsSameOrganizationAndRejectsCrossOrganizationExactDuplicates() {
        FileEntity source = orgFile("source.jpg", organization, 1);
        FileEntity sameOrg = orgFile("same.jpg", organization, 2);
        FileEntity crossOrg = orgFile("cross.jpg", otherOrganization, 3);

        when(fileRepository.findByIdAndDeletedAtIsNull(source.getId())).thenReturn(Optional.of(source));
        when(fileFingerprintRepository.findByFileIdAndAlgorithm(source.getId(), SHA256))
                .thenReturn(Optional.of(fingerprint(source)));
        when(fileFingerprintRepository.findByAlgorithmAndHashValueAndFileOwnerOrganizationIdAndFileDeletedAtIsNull(
                org.mockito.ArgumentMatchers.eq(SHA256),
                org.mockito.ArgumentMatchers.eq(HASH),
                org.mockito.ArgumentMatchers.eq(organization.getId()),
                any()))
                .thenReturn(List.of(fingerprint(source), fingerprint(sameOrg), fingerprint(crossOrg)));

        FileDuplicateSearchResponse response = duplicateSearchService.findDuplicatesForFile(
                source.getId(),
                user.getId(),
                List.of(DuplicateSearchMethod.SHA256));

        assertThat(response.getMatches()).extracting(match -> match.getFile().getId())
                .containsExactly(sameOrg.getId());
    }

    @Test
    void findDuplicatesForFile_RequiresFileAccess() {
        UUID fileId = UUID.randomUUID();
        doThrow(new AccessDeniedException("denied"))
                .when(accessControlService).assertCanAccessFile(any(), any(), any());

        assertThatThrownBy(() -> duplicateSearchService.findDuplicatesForFile(
                fileId,
                user.getId(),
                List.of(DuplicateSearchMethod.SHA256)))
                .isInstanceOf(AccessDeniedException.class);

        verify(fileRepository, never()).findByIdAndDeletedAtIsNull(any());
    }

    @Test
    void findDuplicatesForFile_PhashAndEmbeddingStayOwnerScopedAndMergeSameFileEvidence() {
        FileEntity source = userFile("source.jpg", user, 1);
        FileEntity duplicate = userFile("duplicate.jpg", user, 2);
        FileEntity crossUser = userFile("cross.jpg", otherUser, 3);

        when(fileRepository.findByIdAndDeletedAtIsNull(source.getId())).thenReturn(Optional.of(source));
        when(imageFingerprintRepository.findByFileId(source.getId())).thenReturn(Optional.of(imageFingerprint(source)));
        when(fileEmbeddingRepository.findByFileIdAndModelNameAndModelVersion(
                source.getId(),
                "openai/clip-vit-large-patch14",
                "1"))
                .thenReturn(Optional.of(embedding(source)));
        when(similarImageSearchPort.search(any(SimilarImageSearchRequest.class)))
                .thenReturn(List.of(
                        new SimilarImageCandidate(duplicate.getId(), 2),
                        new SimilarImageCandidate(crossUser.getId(), 1)));
        when(embeddingSimilaritySearchPort.search(any(EmbeddingSimilaritySearchRequest.class)))
                .thenReturn(List.of(new EmbeddingSimilarityCandidate(duplicate.getId(), 0.04)));
        when(fileRepository.findByIdInAndDeletedAtIsNull(anyCollection()))
                .thenReturn(List.of(duplicate, crossUser));

        FileDuplicateSearchResponse response = duplicateSearchService.findDuplicatesForFile(
                source.getId(),
                user.getId(),
                List.of(DuplicateSearchMethod.PHASH, DuplicateSearchMethod.EMBEDDING));

        assertThat(response.getMatches()).hasSize(1);
        assertThat(response.getMatches().getFirst().getFile().getId()).isEqualTo(duplicate.getId());
        assertThat(response.getMatches().getFirst().getBestMethod()).isEqualTo(DuplicateSearchMethod.PHASH);
        assertThat(response.getMatches().getFirst().getMatches()).extracting("method")
                .containsExactly(DuplicateSearchMethod.PHASH, DuplicateSearchMethod.EMBEDDING);

        ArgumentCaptor<SimilarImageSearchRequest> phashRequest = ArgumentCaptor.forClass(SimilarImageSearchRequest.class);
        verify(similarImageSearchPort).search(phashRequest.capture());
        assertThat(phashRequest.getValue().ownerUserId()).isEqualTo(user.getId());
        assertThat(phashRequest.getValue().ownerOrganizationId()).isNull();

        ArgumentCaptor<EmbeddingSimilaritySearchRequest> embeddingRequest =
                ArgumentCaptor.forClass(EmbeddingSimilaritySearchRequest.class);
        verify(embeddingSimilaritySearchPort).search(embeddingRequest.capture());
        assertThat(embeddingRequest.getValue().ownerUserId()).isEqualTo(user.getId());
        assertThat(embeddingRequest.getValue().ownerOrganizationId()).isNull();
    }

    @Test
    void findDuplicatesForFile_SelectedMethodsControlSearchAndDeletedFilesAreExcluded() {
        FileEntity source = userFile("source.jpg", user, 1);
        FileEntity activeDuplicate = userFile("active.jpg", user, 2);
        FileEntity deletedDuplicate = userFile("deleted.jpg", user, 3);
        deletedDuplicate.setDeletedAt(OffsetDateTime.now());

        when(fileRepository.findByIdAndDeletedAtIsNull(source.getId())).thenReturn(Optional.of(source));
        when(imageFingerprintRepository.findByFileId(source.getId())).thenReturn(Optional.of(imageFingerprint(source)));
        when(similarImageSearchPort.search(any(SimilarImageSearchRequest.class)))
                .thenReturn(List.of(
                        new SimilarImageCandidate(activeDuplicate.getId(), 1),
                        new SimilarImageCandidate(deletedDuplicate.getId(), 1)));
        when(fileRepository.findByIdInAndDeletedAtIsNull(anyCollection()))
                .thenReturn(List.of(activeDuplicate));

        FileDuplicateSearchResponse response = duplicateSearchService.findDuplicatesForFile(
                source.getId(),
                user.getId(),
                List.of(DuplicateSearchMethod.PHASH));

        assertThat(response.getMatches()).extracting(match -> match.getFile().getId())
                .containsExactly(activeDuplicate.getId());
        verifyNoInteractions(embeddingSimilaritySearchPort);
        verify(fileFingerprintRepository, never()).findByFileIdAndAlgorithm(any(), any());
    }

    @Test
    void findDuplicatesForFile_MultipleSameMethodMatchesAreSeparateAndBestMethodPrefersSha256() {
        FileEntity source = userFile("source.jpg", user, 1);
        FileEntity duplicateA = userFile("a.jpg", user, 2);
        FileEntity duplicateB = userFile("b.jpg", user, 3);

        when(fileRepository.findByIdAndDeletedAtIsNull(source.getId())).thenReturn(Optional.of(source));
        when(fileFingerprintRepository.findByFileIdAndAlgorithm(source.getId(), SHA256))
                .thenReturn(Optional.of(fingerprint(source)));
        when(fileFingerprintRepository.findByAlgorithmAndHashValueAndFileOwnerUserIdAndFileDeletedAtIsNull(
                org.mockito.ArgumentMatchers.eq(SHA256),
                org.mockito.ArgumentMatchers.eq(HASH),
                org.mockito.ArgumentMatchers.eq(user.getId()),
                any()))
                .thenReturn(List.of(fingerprint(source), fingerprint(duplicateB)));
        when(imageFingerprintRepository.findByFileId(source.getId())).thenReturn(Optional.of(imageFingerprint(source)));
        when(similarImageSearchPort.search(any(SimilarImageSearchRequest.class)))
                .thenReturn(List.of(
                        new SimilarImageCandidate(duplicateA.getId(), 1),
                        new SimilarImageCandidate(duplicateB.getId(), 2)));
        when(fileRepository.findByIdInAndDeletedAtIsNull(anyCollection()))
                .thenReturn(List.of(duplicateA, duplicateB));

        FileDuplicateSearchResponse response = duplicateSearchService.findDuplicatesForFile(
                source.getId(),
                user.getId(),
                List.of(DuplicateSearchMethod.SHA256, DuplicateSearchMethod.PHASH));

        assertThat(response.getMatches()).hasSize(2);
        assertThat(response.getMatches().getFirst().getFile().getId()).isEqualTo(duplicateB.getId());
        assertThat(response.getMatches().getFirst().getBestMethod()).isEqualTo(DuplicateSearchMethod.SHA256);
        assertThat(response.getMatches().getFirst().getMatches()).extracting("method")
                .containsExactly(DuplicateSearchMethod.SHA256, DuplicateSearchMethod.PHASH);
        assertThat(response.getMatches().get(1).getFile().getId()).isEqualTo(duplicateA.getId());
    }

    @Test
    void findDuplicateGroups_GroupsExactDuplicatesWithEarliestOriginalAndExcludesSingletons() {
        FileEntity original = userFile("original.jpg", user, 1);
        FileEntity duplicateA = userFile("a.jpg", user, 2);
        FileEntity duplicateB = userFile("b.jpg", user, 3);
        when(fileFingerprintRepository.findDuplicateSha256GroupsForOwnerUser(
                org.mockito.ArgumentMatchers.eq(SHA256.name()),
                org.mockito.ArgumentMatchers.eq(user.getId()),
                any(),
                any(),
                any(Integer.class)))
                .thenReturn(List.of(hashGroup()));
        when(fileFingerprintRepository.findByOwnerUserAndAlgorithmAndHashValueIn(
                org.mockito.ArgumentMatchers.eq(SHA256),
                org.mockito.ArgumentMatchers.eq(user.getId()),
                anyCollection()))
                .thenReturn(List.of(
                        fingerprint(original),
                        fingerprint(duplicateA),
                        fingerprint(duplicateB)));

        List<DuplicateGroupResponse> response = duplicateSearchService.findDuplicateGroups(
                user.getId(),
                null,
                List.of(DuplicateSearchMethod.SHA256),
                user.getId());

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().getOriginalFile().getId()).isEqualTo(original.getId());
        assertThat(response.getFirst().getDuplicates()).extracting(match -> match.getFile().getId())
                .containsExactly(duplicateA.getId(), duplicateB.getId());
        assertThat(response.getFirst().getGroupSize()).isEqualTo(3);
        verify(accessControlService).assertCanViewDuplicates(user.getId(), user.getId(), null);
    }

    @Test
    void findDuplicateGroups_RequiresDuplicateViewPermission() {
        doThrow(new AccessDeniedException("denied"))
                .when(accessControlService).assertCanViewDuplicates(user.getId(), user.getId(), null);

        assertThatThrownBy(() -> duplicateSearchService.findDuplicateGroups(
                user.getId(),
                null,
                List.of(DuplicateSearchMethod.SHA256),
                user.getId()))
                .isInstanceOf(AccessDeniedException.class);

        verify(fileFingerprintRepository, never()).findDuplicateSha256GroupsForOwnerUser(any(), any(), any(), any(), anyInt());
    }

    @Test
    void findDuplicateGroups_PhashUsesDatabasePairSearchInsideOwnerScope() {
        FileEntity source = userFile("source.jpg", user, 1);
        FileEntity duplicate = userFile("duplicate.jpg", user, 2);

        when(similarImagePairSearchPort.search(any(SimilarImagePairSearchRequest.class)))
                .thenReturn(List.of(new SimilarImagePairCandidate(source.getId(), duplicate.getId(), 2)));
        when(fileRepository.findByIdInAndDeletedAtIsNull(anyCollection()))
                .thenReturn(List.of(source, duplicate));

        List<DuplicateGroupResponse> response = duplicateSearchService.findDuplicateGroups(
                user.getId(),
                null,
                List.of(DuplicateSearchMethod.PHASH),
                user.getId());

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().getOriginalFile().getId()).isEqualTo(source.getId());
        assertThat(response.getFirst().getDuplicates().getFirst().getFile().getId()).isEqualTo(duplicate.getId());

        ArgumentCaptor<SimilarImagePairSearchRequest> request = ArgumentCaptor.forClass(SimilarImagePairSearchRequest.class);
        verify(similarImagePairSearchPort).search(request.capture());
        assertThat(request.getValue().ownerUserId()).isEqualTo(user.getId());
        assertThat(request.getValue().ownerOrganizationId()).isNull();
        assertThat(request.getValue().threshold()).isEqualTo(10);
        assertThat(request.getValue().maxResults()).isEqualTo(50);
        verify(imageFingerprintRepository, never()).findByFileId(any());
    }

    @Test
    void findDuplicateGroups_EmbeddingUsesDatabasePairSearchInsideOwnerScope() {
        FileEntity source = orgFile("source.jpg", organization, 1);
        FileEntity duplicate = orgFile("duplicate.jpg", organization, 2);

        when(embeddingSimilarityPairSearchPort.search(any(EmbeddingSimilarityPairSearchRequest.class)))
                .thenReturn(List.of(new EmbeddingSimilarityPairCandidate(source.getId(), duplicate.getId(), 0.04)));
        when(fileRepository.findByIdInAndDeletedAtIsNull(anyCollection()))
                .thenReturn(List.of(source, duplicate));

        List<DuplicateGroupResponse> response = duplicateSearchService.findDuplicateGroups(
                null,
                organization.getId(),
                List.of(DuplicateSearchMethod.EMBEDDING),
                user.getId());

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().getOriginalFile().getId()).isEqualTo(source.getId());
        assertThat(response.getFirst().getDuplicates().getFirst().getFile().getId()).isEqualTo(duplicate.getId());

        ArgumentCaptor<EmbeddingSimilarityPairSearchRequest> request =
                ArgumentCaptor.forClass(EmbeddingSimilarityPairSearchRequest.class);
        verify(embeddingSimilarityPairSearchPort).search(request.capture());
        assertThat(request.getValue().ownerUserId()).isNull();
        assertThat(request.getValue().ownerOrganizationId()).isEqualTo(organization.getId());
        assertThat(request.getValue().modelName()).isEqualTo("openai/clip-vit-large-patch14");
        assertThat(request.getValue().modelVersion()).isEqualTo("1");
        assertThat(request.getValue().maxCosineDistance()).isEqualTo(0.20);
        assertThat(request.getValue().maxResults()).isEqualTo(25);
        verify(fileEmbeddingRepository, never()).findByFileIdAndModelNameAndModelVersion(any(), any(), any());
    }

    private FileFingerprint fingerprint(FileEntity file) {
        return FileFingerprint.builder()
                .file(file)
                .algorithm(SHA256)
                .hashValue(HASH)
                .build();
    }

    private DuplicateHashGroupProjection hashGroup() {
        return () -> HASH;
    }

    private ImageFingerprint imageFingerprint(FileEntity file) {
        return ImageFingerprint.builder()
                .file(file)
                .phash("fedcba9876543210")
                .build();
    }

    private FileEmbedding embedding(FileEntity file) {
        return FileEmbedding.builder()
                .file(file)
                .modelName("openai/clip-vit-large-patch14")
                .modelVersion("1")
                .dimension(768)
                .embedding(new float[768])
                .build();
    }

    private FileEntity userFile(String name, User ownerUser, int createdOrder) {
        return FileEntity.builder()
                .id(UUID.randomUUID())
                .name(name)
                .mimeType("image/jpeg")
                .size(10L)
                .ownerUser(ownerUser)
                .createdAt(OffsetDateTime.parse("2026-01-01T00:00:00Z").plusMinutes(createdOrder))
                .build();
    }

    private FileEntity orgFile(String name, Organization ownerOrganization, int createdOrder) {
        return FileEntity.builder()
                .id(UUID.randomUUID())
                .name(name)
                .mimeType("image/jpeg")
                .size(10L)
                .ownerOrganization(ownerOrganization)
                .createdAt(OffsetDateTime.parse("2026-01-01T00:00:00Z").plusMinutes(createdOrder))
                .build();
    }
}

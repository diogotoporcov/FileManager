package com.filemanager.api.duplicate.application;

import com.filemanager.api.auth.domain.Permission;
import com.filemanager.api.duplicate.domain.*;
import com.filemanager.api.duplicate.domain.DuplicateCandidate.DuplicateCandidateStatus;
import com.filemanager.api.duplicate.persistence.DuplicateCandidateRefreshRepository;
import com.filemanager.api.duplicate.persistence.DuplicateCandidateRepository;
import com.filemanager.api.duplicate.persistence.ExactDuplicateGroupRepository;
import com.filemanager.api.duplicate.web.DuplicateGroupSearchRequest;
import com.filemanager.api.duplicate.web.DuplicateGroupSearchResponse;
import com.filemanager.api.duplicate.web.DuplicateSearchResponse;
import com.filemanager.api.exception.AccessDeniedException;
import com.filemanager.api.file.domain.FileEntity;
import com.filemanager.api.file.application.FileService;
import com.filemanager.api.file.persistence.FileRepository;
import com.filemanager.api.folder.domain.FolderClosureEntity;
import com.filemanager.api.folder.domain.FolderClosureId;
import com.filemanager.api.folder.domain.FolderEntity;
import com.filemanager.api.folder.persistence.FolderClosureRepository;
import com.filemanager.api.folder.persistence.FolderRepository;
import com.filemanager.api.identity.domain.User;
import com.filemanager.api.identity.persistence.UserRepository;
import com.filemanager.api.processing.domain.result.FileFingerprint;
import com.filemanager.api.processing.domain.result.FileFingerprint.FingerprintAlgorithm;
import com.filemanager.api.processing.domain.result.AudioFingerprint;
import com.filemanager.api.processing.domain.result.ImageFingerprint;
import com.filemanager.api.processing.messaging.FileProcessingRequestedEvent;
import com.filemanager.api.processing.persistence.result.AudioFingerprintRepository;
import com.filemanager.api.processing.persistence.result.FileFingerprintRepository;
import com.filemanager.api.processing.persistence.result.ImageFingerprintRepository;
import com.filemanager.api.processing.persistence.result.VideoEmbeddingRepository;
import com.filemanager.api.sharing.domain.FileGrantEntity;
import com.filemanager.api.sharing.domain.FolderGrantEntity;
import com.filemanager.api.sharing.domain.FolderGrantScope;
import com.filemanager.api.sharing.persistence.FileGrantRepository;
import com.filemanager.api.sharing.persistence.FolderGrantRepository;
import io.minio.MinioClient;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DuplicateSearchServiceIntegrationTest {
    private static final String HASH_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String HASH_B = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    @Autowired
    private DuplicateSearchService duplicateSearchService;
    @Autowired
    private ExactDuplicateGroupMaintenanceService exactDuplicateGroupMaintenanceService;
    @Autowired
    private FileService fileService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private FileRepository fileRepository;
    @Autowired
    private FileFingerprintRepository fileFingerprintRepository;
    @Autowired
    private ImageFingerprintRepository imageFingerprintRepository;
    @Autowired
    private AudioFingerprintRepository audioFingerprintRepository;
    @Autowired
    private ExactDuplicateGroupRepository exactDuplicateGroupRepository;
    @Autowired
    private DuplicateCandidateRepository duplicateCandidateRepository;
    @Autowired
    private DuplicateCandidateRefreshRepository duplicateCandidateRefreshRepository;
    @Autowired
    private FolderRepository folderRepository;
    @Autowired
    private FolderClosureRepository folderClosureRepository;
    @Autowired
    private FileGrantRepository fileGrantRepository;
    @Autowired
    private FolderGrantRepository folderGrantRepository;
    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private MinioClient minioClient;
    @MockitoBean
    private KafkaTemplate<String, FileProcessingRequestedEvent> kafkaTemplate;
    @MockitoBean
    private JwtDecoder jwtDecoder;
    @MockitoBean
    private VideoEmbeddingRepository videoEmbeddingRepository;

    @Test
    void ownerCanRunExactDuplicateSearchForOwnFile() {
        User owner = saveUser("owner@example.com");
        FileEntity source = saveFile("source.txt", owner, null);
        FileEntity duplicate = saveFile("copy.txt", owner, null);
        saveFingerprint(source, HASH_A);
        saveFingerprint(duplicate, HASH_A);

        var response = duplicateSearchService.searchDuplicatesForFile(source.getId(), List.of(), owner.getId());

        assertThat(response.methods()).hasSize(1);
        assertThat(response.methods().getFirst().method()).isEqualTo(DuplicateSearchMethod.EXACT);
        assertThat(response.methods().getFirst().status()).isEqualTo(DuplicateMethodStatus.COMPLETED);
        assertThat(response.methods().getFirst().matches())
                .extracting(DuplicateSearchResponse.DuplicateMatchResponse::fileId)
                .containsExactly(duplicate.getId());
    }

    @Test
    void checksumSummaryIsMaintainedForNewAndExistingExactHashes() {
        User owner = saveUser("summary-owner@example.com");
        FileEntity first = saveFile("first.txt", owner, null);
        FileEntity second = saveFile("second.txt", owner, null);

        saveFingerprint(first, HASH_A);

        var oneFileGroup = exactDuplicateGroupRepository
                .findByOwnerUserIdAndAlgorithmAndHashValue(owner.getId(), FingerprintAlgorithm.SHA256, HASH_A)
                .orElseThrow();
        assertThat(oneFileGroup.getActiveFileCount()).isEqualTo(1);
        assertThat(oneFileGroup.getRepresentativeFileId()).isEqualTo(first.getId());

        saveFingerprint(second, HASH_A);
        clearPersistenceContext();

        var twoFileGroup = exactDuplicateGroupRepository
                .findByOwnerUserIdAndAlgorithmAndHashValue(owner.getId(), FingerprintAlgorithm.SHA256, HASH_A)
                .orElseThrow();
        assertThat(twoFileGroup.getActiveFileCount()).isEqualTo(2);
        assertThat(twoFileGroup.getRepresentativeFileId()).isEqualTo(first.getId());
    }

    @Test
    void checksumReplacementMovesFileBetweenExactSummaryGroups() {
        User owner = saveUser("replace-owner@example.com");
        FileEntity file = saveFile("replace.txt", owner, null);
        saveFingerprint(file, HASH_A);

        FileFingerprint fingerprint = fileFingerprintRepository
                .findByFileIdAndAlgorithm(file.getId(), FingerprintAlgorithm.SHA256)
                .orElseThrow();
        fingerprint.setHashValue(HASH_B);
        fileFingerprintRepository.saveAndFlush(fingerprint);
        exactDuplicateGroupMaintenanceService.refreshAfterFingerprintChange(
                owner.getId(),
                FingerprintAlgorithm.SHA256,
                HASH_A,
                HASH_B);

        assertThat(exactDuplicateGroupRepository
                .findByOwnerUserIdAndAlgorithmAndHashValue(owner.getId(), FingerprintAlgorithm.SHA256, HASH_A))
                .isEmpty();
        assertThat(exactDuplicateGroupRepository
                .findByOwnerUserIdAndAlgorithmAndHashValue(owner.getId(), FingerprintAlgorithm.SHA256, HASH_B))
                .get()
                .extracting(ExactDuplicateGroup::getActiveFileCount)
                .isEqualTo(1L);
    }

    @Test
    void deletingFileRefreshesExactSummary() {
        User owner = saveUser("delete-summary@example.com");
        FileEntity first = saveFile("first.txt", owner, null);
        FileEntity second = saveFile("second.txt", owner, null);
        saveFingerprint(first, HASH_A);
        saveFingerprint(second, HASH_A);

        fileService.deleteFile(second.getId(), owner.getId());
        clearPersistenceContext();

        var group = exactDuplicateGroupRepository
                .findByOwnerUserIdAndAlgorithmAndHashValue(owner.getId(), FingerprintAlgorithm.SHA256, HASH_A)
                .orElseThrow();
        assertThat(group.getActiveFileCount()).isEqualTo(1);
        assertThat(group.getRepresentativeFileId()).isEqualTo(first.getId());
    }

    @Test
    void refreshDeletesExactSummaryWhenNoEligibleFilesRemain() {
        User owner = saveUser("delete-empty-summary@example.com");
        FileEntity onlyFile = saveFile("only.txt", owner, null);
        saveFingerprint(onlyFile, HASH_A);

        fileService.deleteFile(onlyFile.getId(), owner.getId());
        clearPersistenceContext();

        assertThat(exactDuplicateGroupRepository
                .findByOwnerUserIdAndAlgorithmAndHashValue(owner.getId(), FingerprintAlgorithm.SHA256, HASH_A))
                .isEmpty();
    }

    @Test
    void refreshUpdatesExistingExactSummaryFromSourceTables() {
        User owner = saveUser("update-summary@example.com");
        FileEntity first = saveFile("first.txt", owner, null);
        FileEntity second = saveFile("second.txt", owner, null);
        saveFingerprint(first, HASH_A);
        saveFingerprint(second, HASH_A);
        var staleGroup = exactDuplicateGroupRepository
                .findByOwnerUserIdAndAlgorithmAndHashValue(owner.getId(), FingerprintAlgorithm.SHA256, HASH_A)
                .orElseThrow();
        staleGroup.setActiveFileCount(99);
        staleGroup.setRepresentativeFileId(second.getId());
        exactDuplicateGroupRepository.saveAndFlush(staleGroup);

        exactDuplicateGroupMaintenanceService.refreshGroup(owner.getId(), FingerprintAlgorithm.SHA256, HASH_A);
        clearPersistenceContext();

        var refreshedGroup = exactDuplicateGroupRepository
                .findByOwnerUserIdAndAlgorithmAndHashValue(owner.getId(), FingerprintAlgorithm.SHA256, HASH_A)
                .orElseThrow();
        assertThat(refreshedGroup.getActiveFileCount()).isEqualTo(2);
        assertThat(refreshedGroup.getRepresentativeFileId()).isEqualTo(first.getId());
    }

    @Test
    void repeatedExactSummaryRefreshIsIdempotent() {
        User owner = saveUser("idempotent-summary@example.com");
        FileEntity first = saveFile("first.txt", owner, null);
        FileEntity second = saveFile("second.txt", owner, null);
        saveFingerprint(first, HASH_A);
        saveFingerprint(second, HASH_A);

        exactDuplicateGroupMaintenanceService.refreshGroup(owner.getId(), FingerprintAlgorithm.SHA256, HASH_A);
        exactDuplicateGroupMaintenanceService.refreshGroup(owner.getId(), FingerprintAlgorithm.SHA256, HASH_A);
        clearPersistenceContext();

        var group = exactDuplicateGroupRepository
                .findByOwnerUserIdAndAlgorithmAndHashValue(owner.getId(), FingerprintAlgorithm.SHA256, HASH_A)
                .orElseThrow();
        assertThat(group.getActiveFileCount()).isEqualTo(2);
        assertThat(group.getRepresentativeFileId()).isEqualTo(first.getId());
    }

    @Test
    void sharedFileViewGranteeCannotRunDuplicateSearchForSourceFile() {
        User actor = saveUser("actor@example.com");
        User owner = saveUser("owner@example.com");
        FileEntity source = saveFile("shared.txt", owner, null);
        saveFileGrant(source, actor, owner, Permission.FILE_VIEW);

        assertThrows(AccessDeniedException.class,
                () -> duplicateSearchService.searchDuplicatesForFile(source.getId(), List.of(), actor.getId()));
    }

    @Test
    void directFolderGranteeCannotRunDuplicateSearchForSourceFile() {
        User actor = saveUser("actor@example.com");
        User owner = saveUser("owner@example.com");
        FolderEntity folder = saveFolder("folder", owner, null);
        FileEntity source = saveFile("shared.txt", owner, folder);
        saveFolderGrant(folder, actor, owner, Permission.FOLDER_VIEW, FolderGrantScope.DIRECT);

        assertThrows(AccessDeniedException.class,
                () -> duplicateSearchService.searchDuplicatesForFile(source.getId(), List.of(), actor.getId()));
    }

    @Test
    void recursiveFolderGranteeCannotRunDuplicateSearchForSourceFile() {
        User actor = saveUser("actor@example.com");
        User owner = saveUser("owner@example.com");
        FolderEntity parent = saveFolder("parent", owner, null);
        FolderEntity child = saveFolder("child", owner, parent);
        FileEntity source = saveFile("shared.txt", owner, child);
        saveFolderGrant(parent, actor, owner, Permission.FOLDER_VIEW, FolderGrantScope.RECURSIVE);

        assertThrows(AccessDeniedException.class,
                () -> duplicateSearchService.searchDuplicatesForFile(source.getId(), List.of(), actor.getId()));
    }

    @Test
    void folderOwnerCannotRunDuplicateSearchForGuestOwnedUploadedFile() {
        User folderOwner = saveUser("folder-owner@example.com");
        User guest = saveUser("guest@example.com");
        FolderEntity folder = saveFolder("folder", folderOwner, null);
        FileEntity source = saveFile("guest.txt", guest, folder);

        assertThrows(AccessDeniedException.class,
                () -> duplicateSearchService.searchDuplicatesForFile(source.getId(), List.of(), folderOwner.getId()));
    }

    @Test
    void exactCandidatesAreOwnerScopedAndActiveOnly() {
        User actor = saveUser("actor@example.com");
        User other = saveUser("other@example.com");
        FolderEntity deletedFolder = saveFolder("deleted", actor, null);
        deletedFolder.setDeletedAt(OffsetDateTime.now());
        folderRepository.saveAndFlush(deletedFolder);
        FileEntity source = saveFile("source.txt", actor, null);
        FileEntity returned = saveFile("returned.txt", actor, null);
        FileEntity otherOwned = saveFile("foreign.txt", other, null);
        FileEntity deleted = saveFile("deleted.txt", actor, null);
        FileEntity inDeletedFolder = saveFile("folder-deleted.txt", actor, deletedFolder);
        deleted.setDeletedAt(OffsetDateTime.now());
        fileRepository.saveAndFlush(deleted);
        saveFileGrant(otherOwned, actor, other, Permission.FILE_VIEW);
        List.of(source, returned, otherOwned, deleted, inDeletedFolder).forEach(file -> saveFingerprint(file, HASH_A));

        var response = duplicateSearchService.searchDuplicatesForFile(
                source.getId(),
                List.of(DuplicateSearchMethod.EXACT),
                actor.getId());

        assertThat(response.methods().getFirst().matches())
                .extracting(DuplicateSearchResponse.DuplicateMatchResponse::fileId)
                .containsExactly(returned.getId());
    }

    @Test
    void exactSourceWithoutFingerprintReturnsNotReady() {
        User actor = saveUser("actor@example.com");
        FileEntity source = saveFile("source.txt", actor, null);

        var response = duplicateSearchService.searchDuplicatesForFile(
                source.getId(),
                List.of(DuplicateSearchMethod.EXACT),
                actor.getId());

        assertThat(response.methods().getFirst().status())
                .isEqualTo(DuplicateMethodStatus.SOURCE_FINGERPRINT_NOT_READY);
        assertThat(response.methods().getFirst().matches()).isEmpty();
    }

    @Test
    void groupedExactReturnsOnlyActorOwnedGroups() {
        User actor = saveUser("actor@example.com");
        User other = saveUser("other@example.com");
        FileEntity first = saveFile("one.txt", actor, null);
        FileEntity second = saveFile("two.txt", actor, null);
        FileEntity foreign = saveFile("foreign.txt", other, null);
        saveFingerprint(first, HASH_A);
        saveFingerprint(second, HASH_A);
        saveFingerprint(foreign, HASH_A);
        saveFileGrant(foreign, actor, other, Permission.FILE_VIEW);

        var response = duplicateSearchService.searchGroups(null, actor.getId());

        assertThat(response.methods()).hasSize(1);
        assertThat(response.methods().getFirst().method()).isEqualTo(DuplicateSearchMethod.EXACT);
        assertThat(response.methods().getFirst().groups()).hasSize(1);
        assertThat(response.methods().getFirst().groups().getFirst().files())
                .extracting(DuplicateGroupSearchResponse.DuplicateGroupFileResponse::fileId)
                .containsExactlyInAnyOrder(first.getId(), second.getId());
    }

    @Test
    void groupedExactDoesNotReturnStaleSummaryGroupWithFewerThanTwoEligibleFiles() {
        User actor = saveUser("stale-summary@example.com");
        FileEntity onlyFile = saveFile("only.txt", actor, null);
        saveFingerprint(onlyFile, HASH_A);
        var group = exactDuplicateGroupRepository
                .findByOwnerUserIdAndAlgorithmAndHashValue(actor.getId(), FingerprintAlgorithm.SHA256, HASH_A)
                .orElseThrow();
        group.setActiveFileCount(2);
        exactDuplicateGroupRepository.saveAndFlush(group);

        var response = duplicateSearchService.searchGroups(null, actor.getId());

        assertThat(response.methods().getFirst().groups()).isEmpty();
    }

    @Test
    void perFilePhashSearchCanReadPersistedCandidatesFromBothPairDirections() {
        User actor = saveUser("candidate-owner@example.com");
        FileEntity lower = saveFile("lower.png", actor, null, "image/png");
        FileEntity higher = saveFile("higher.png", actor, null, "image/png");
        saveImageFingerprint(lower, "0000000000000000");
        saveImageFingerprint(higher, "0000000000000001");
        saveDuplicateCandidate(actor, lower, higher, DuplicateSearchMethod.IMAGE_PHASH, 1.0, "maxDistance=10;topN=100");

        var lowerResponse = duplicateSearchService.searchDuplicatesForFile(
                lower.getId(),
                List.of(DuplicateSearchMethod.IMAGE_PHASH),
                actor.getId());
        var higherResponse = duplicateSearchService.searchDuplicatesForFile(
                higher.getId(),
                List.of(DuplicateSearchMethod.IMAGE_PHASH),
                actor.getId());

        assertThat(lowerResponse.methods().getFirst().matches())
                .extracting(DuplicateSearchResponse.DuplicateMatchResponse::fileId)
                .containsExactly(higher.getId());
        assertThat(higherResponse.methods().getFirst().matches())
                .extracting(DuplicateSearchResponse.DuplicateMatchResponse::fileId)
                .containsExactly(lower.getId());
    }

    @Test
    void perFilePhashSearchReturnsEmptyWithoutFallbackWhenRefreshMarkerExists() {
        User actor = saveUser("phash-marker-owner@example.com");
        FileEntity source = saveFile("source.png", actor, null, "image/png");
        FileEntity directFallbackMatch = saveFile("direct-match.png", actor, null, "image/png");
        saveImageFingerprint(source, "0000000000000000");
        saveImageFingerprint(directFallbackMatch, "0000000000000001");
        saveDuplicateCandidateRefresh(
                actor,
                source,
                DuplicateSearchMethod.IMAGE_PHASH,
                DuplicateCandidate.NO_MODEL,
                DuplicateCandidate.NO_MODEL,
                "maxDistance=10;topN=100",
                0);

        var response = duplicateSearchService.searchDuplicatesForFile(
                source.getId(),
                List.of(DuplicateSearchMethod.IMAGE_PHASH),
                actor.getId());

        assertThat(response.methods().getFirst().matches()).isEmpty();
    }

    @Test
    void persistedCandidateRepositoryExcludesForeignAndDeletedFilesAtReadTime() {
        User actor = saveUser("stale-candidate-owner@example.com");
        User other = saveUser("stale-candidate-other@example.com");
        FileEntity source = saveFile("source.png", actor, null, "image/png");
        FileEntity deleted = saveFile("deleted.png", actor, null, "image/png");
        FileEntity foreign = saveFile("foreign.png", other, null, "image/png");
        deleted.setDeletedAt(OffsetDateTime.now());
        fileRepository.saveAndFlush(deleted);
        saveDuplicateCandidate(actor, source, deleted, DuplicateSearchMethod.IMAGE_PHASH, 1.0, "maxDistance=10;topN=100");
        saveDuplicateCandidate(actor, source, foreign, DuplicateSearchMethod.IMAGE_PHASH, 1.0, "maxDistance=10;topN=100");

        var candidates = duplicateCandidateRepository.findCandidatesForFile(
                actor.getId(),
                source.getId(),
                DuplicateSearchMethod.IMAGE_PHASH,
                DuplicateCandidate.NO_MODEL,
                DuplicateCandidate.NO_MODEL,
                "maxDistance=10;topN=100",
                DuplicateCandidateStatus.ACTIVE,
                org.springframework.data.domain.PageRequest.of(0, 100));

        assertThat(candidates).isEmpty();
    }

    @Test
    void groupedExactReturnsNoGroupForOneOwnedAndOneForeignOwnedMatch() {
        User actor = saveUser("actor@example.com");
        User other = saveUser("other@example.com");
        FileEntity owned = saveFile("owned.txt", actor, null);
        FileEntity foreign = saveFile("foreign.txt", other, null);
        saveFingerprint(owned, HASH_A);
        saveFingerprint(foreign, HASH_A);
        saveFileGrant(foreign, actor, other, Permission.FILE_VIEW);

        var response = duplicateSearchService.searchGroups(null, actor.getId());

        assertThat(response.methods().getFirst().groups()).isEmpty();
    }

    @Test
    void groupedExactExcludesDeletedFilesAndFilesInDeletedFolders() {
        User actor = saveUser("actor@example.com");
        FolderEntity deletedFolder = saveFolder("deleted", actor, null);
        deletedFolder.setDeletedAt(OffsetDateTime.now());
        folderRepository.saveAndFlush(deletedFolder);
        FileEntity active = saveFile("active.txt", actor, null);
        FileEntity deleted = saveFile("deleted.txt", actor, null);
        FileEntity inDeletedFolder = saveFile("folder-deleted.txt", actor, deletedFolder);
        deleted.setDeletedAt(OffsetDateTime.now());
        fileRepository.saveAndFlush(deleted);
        saveFingerprint(active, HASH_A);
        saveFingerprint(deleted, HASH_A);
        saveFingerprint(inDeletedFolder, HASH_A);

        var response = duplicateSearchService.searchGroups(null, actor.getId());

        assertThat(response.methods().getFirst().groups()).isEmpty();
    }

    @Test
    void onlyRequestedMethodsRun() {
        User actor = saveUser("actor@example.com");
        FileEntity source = saveFile("source.txt", actor, null);
        saveFingerprint(source, HASH_A);

        var response = duplicateSearchService.searchDuplicatesForFile(
                source.getId(),
                List.of(DuplicateSearchMethod.IMAGE_PHASH),
                actor.getId());

        assertThat(response.methods())
                .extracting(DuplicateSearchResponse.DuplicateMethodResultResponse::method)
                .containsExactly(DuplicateSearchMethod.IMAGE_PHASH);
        assertThat(response.methods().getFirst().status()).isEqualTo(DuplicateMethodStatus.UNSUPPORTED_FOR_FILE_TYPE);
    }

    @Test
    void duplicateSearchMethodExposesFinalV1MethodsOnly() {
        assertThat(DuplicateSearchMethod.values())
                .containsExactly(
                        DuplicateSearchMethod.EXACT,
                        DuplicateSearchMethod.IMAGE_PHASH,
                        DuplicateSearchMethod.IMAGE_EMBEDDING,
                        DuplicateSearchMethod.AUDIO_FINGERPRINT,
                        DuplicateSearchMethod.VIDEO_EMBEDDING);
    }

    @Test
    void videoFilesStillSupportExactDuplicateDetection() {
        User actor = saveUser("actor@example.com");
        FileEntity source = saveFile("source.mp4", actor, null, "video/mp4");
        FileEntity duplicate = saveFile("copy.mkv", actor, null, "video/x-matroska");
        saveFingerprint(source, HASH_A);
        saveFingerprint(duplicate, HASH_A);

        var response = duplicateSearchService.searchDuplicatesForFile(
                source.getId(),
                List.of(DuplicateSearchMethod.EXACT),
                actor.getId());

        assertThat(response.methods().getFirst().status()).isEqualTo(DuplicateMethodStatus.COMPLETED);
        assertThat(response.methods().getFirst().matches())
                .extracting(DuplicateSearchResponse.DuplicateMatchResponse::fileId)
                .containsExactly(duplicate.getId());
    }

    @Test
    void audioFingerprintSearchReturnsStandaloneAudioDuplicatesOnly() {
        User actor = saveUser("actor@example.com");
        FileEntity source = saveFile("source.mp3", actor, null, "audio/mpeg");
        FileEntity audioDuplicate = saveFile("copy.wav", actor, null, "audio/wav");
        FileEntity videoWithMatchingAudio = saveFile("video.mp4", actor, null, "video/mp4");
        saveAudioFingerprint(source, "same-audio");
        saveAudioFingerprint(audioDuplicate, "same-audio");
        saveAudioFingerprint(videoWithMatchingAudio, "same-audio");

        var response = duplicateSearchService.searchDuplicatesForFile(
                source.getId(),
                List.of(DuplicateSearchMethod.AUDIO_FINGERPRINT),
                actor.getId());

        assertThat(response.methods().getFirst().status()).isEqualTo(DuplicateMethodStatus.COMPLETED);
        assertThat(response.methods().getFirst().matches())
                .extracting(DuplicateSearchResponse.DuplicateMatchResponse::fileId)
                .containsExactly(audioDuplicate.getId());
    }

    @Test
    void videoEmbeddingReturnsUnsupportedForNonVideoSource() {
        User actor = saveUser("actor@example.com");
        FileEntity source = saveFile("source.txt", actor, null);

        var response = duplicateSearchService.searchDuplicatesForFile(
                source.getId(),
                List.of(DuplicateSearchMethod.VIDEO_EMBEDDING),
                actor.getId());

        assertThat(response.methods().getFirst().status()).isEqualTo(DuplicateMethodStatus.UNSUPPORTED_FOR_FILE_TYPE);
    }

    @Test
    void videoEmbeddingReturnsNotReadyWhenPooledEmbeddingIsMissing() {
        User actor = saveUser("actor@example.com");
        FileEntity source = saveFile("source.mp4", actor, null, "video/mp4");
        when(videoEmbeddingRepository.findByFileId(source.getId())).thenReturn(Optional.empty());

        var response = duplicateSearchService.searchDuplicatesForFile(
                source.getId(),
                List.of(DuplicateSearchMethod.VIDEO_EMBEDDING),
                actor.getId());

        assertThat(response.methods().getFirst().status())
                .isEqualTo(DuplicateMethodStatus.SOURCE_FINGERPRINT_NOT_READY);
    }

    @Test
    void groupedVideoEmbeddingIsDeferred() {
        User actor = saveUser("actor@example.com");
        DuplicateGroupSearchRequest request = new DuplicateGroupSearchRequest(
                List.of(DuplicateSearchMethod.VIDEO_EMBEDDING),
                10,
                null,
                null,
                null,
                null);

        var response = duplicateSearchService.searchGroups(request, actor.getId());

        assertThat(response.methods()).hasSize(1);
        assertThat(response.methods().getFirst().method()).isEqualTo(DuplicateSearchMethod.VIDEO_EMBEDDING);
        assertThat(response.methods().getFirst().status())
                .isEqualTo(DuplicateMethodStatus.NOT_SUPPORTED_FOR_GROUPED_SEARCH_YET);
        assertThat(response.methods().getFirst().groups()).isEmpty();
    }

    @Test
    void groupedAudioFingerprintUsesAlgorithmVersionHashCompositeKey() {
        User actor = saveUser("actor@example.com");
        FileEntity firstV1 = saveFile("first-v1.mp3", actor, null, "audio/mpeg");
        FileEntity secondV1 = saveFile("second-v1.mp3", actor, null, "audio/mpeg");
        FileEntity firstV2 = saveFile("first-v2.mp3", actor, null, "audio/mpeg");
        FileEntity secondV2 = saveFile("second-v2.mp3", actor, null, "audio/mpeg");
        saveAudioFingerprint(firstV1, "same-audio", "fpcalc-v1");
        saveAudioFingerprint(secondV1, "same-audio", "fpcalc-v1");
        saveAudioFingerprint(firstV2, "same-audio", "fpcalc-v2");
        saveAudioFingerprint(secondV2, "same-audio", "fpcalc-v2");
        DuplicateGroupSearchRequest request = new DuplicateGroupSearchRequest(
                List.of(DuplicateSearchMethod.AUDIO_FINGERPRINT),
                10,
                null,
                null,
                null,
                null);

        var response = duplicateSearchService.searchGroups(request, actor.getId());

        assertThat(response.methods()).hasSize(1);
        assertThat(response.methods().getFirst().groups()).hasSize(2);
        assertThat(response.methods().getFirst().groups())
                .extracting(group -> group.files().size())
                .containsExactly(2, 2);
        assertThat(response.methods().getFirst().groups())
                .extracting(DuplicateGroupSearchResponse.DuplicateGroupResponse::groupId)
                .doesNotHaveDuplicates();
    }

    @Test
    void groupedAudioFingerprintReturnsStandaloneAudioGroupsOnly() {
        User actor = saveUser("actor@example.com");
        FileEntity firstAudio = saveFile("first.mp3", actor, null, "audio/mpeg");
        FileEntity secondAudio = saveFile("second.wav", actor, null, "audio/wav");
        FileEntity firstVideo = saveFile("first.mp4", actor, null, "video/mp4");
        FileEntity secondVideo = saveFile("second.mkv", actor, null, "video/x-matroska");
        saveAudioFingerprint(firstAudio, "same-audio");
        saveAudioFingerprint(secondAudio, "same-audio");
        saveAudioFingerprint(firstVideo, "same-audio");
        saveAudioFingerprint(secondVideo, "same-audio");
        DuplicateGroupSearchRequest request = new DuplicateGroupSearchRequest(
                List.of(DuplicateSearchMethod.AUDIO_FINGERPRINT),
                10,
                null,
                null,
                null,
                null);

        var response = duplicateSearchService.searchGroups(request, actor.getId());

        assertThat(response.methods()).hasSize(1);
        assertThat(response.methods().getFirst().groups()).hasSize(1);
        assertThat(response.methods().getFirst().groups().getFirst().files())
                .extracting(DuplicateGroupSearchResponse.DuplicateGroupFileResponse::fileId)
                .containsExactlyInAnyOrder(firstAudio.getId(), secondAudio.getId());
    }

    @Test
    void differentChecksumOrAlgorithmDoesNotMatch() {
        User actor = saveUser("actor@example.com");
        FileEntity source = saveFile("source.txt", actor, null);
        FileEntity different = saveFile("different.txt", actor, null);
        saveFingerprint(source, HASH_A);
        saveFingerprint(different, HASH_B);

        var response = duplicateSearchService.searchDuplicatesForFile(
                source.getId(),
                List.of(DuplicateSearchMethod.EXACT),
                actor.getId());

        assertThat(response.methods().getFirst().matches()).isEmpty();
    }

    private User saveUser(String email) {
        return userRepository.saveAndFlush(User.builder().email(email).build());
    }

    private FolderEntity saveFolder(String name, User owner, FolderEntity parent) {
        FolderEntity folder = folderRepository.saveAndFlush(FolderEntity.builder()
                .name(name)
                .ownerUser(owner)
                .createdByUser(owner)
                .parentFolder(parent)
                .build());
        saveClosureRows(folder, parent);

        return folder;
    }

    private void saveClosureRows(FolderEntity folder, FolderEntity parent) {
        folderClosureRepository.saveAndFlush(FolderClosureEntity.builder()
                .id(new FolderClosureId(folder.getId(), folder.getId()))
                .ancestorFolder(folder)
                .descendantFolder(folder)
                .depth(0)
                .build());

        if (parent == null) {
            return;
        }

        List<FolderClosureEntity> parentClosureRows = folderClosureRepository.findByDescendantFolderOrderByDepthAsc(parent);
        folderClosureRepository.saveAllAndFlush(parentClosureRows.stream()
                .map(parentClosure -> FolderClosureEntity.builder()
                        .id(new FolderClosureId(parentClosure.getAncestorFolder().getId(), folder.getId()))
                        .ancestorFolder(parentClosure.getAncestorFolder())
                        .descendantFolder(folder)
                        .depth(parentClosure.getDepth() + 1)
                        .build())
                .toList());
    }

    private FileEntity saveFile(String name, User owner, FolderEntity folder) {
        return saveFile(name, owner, folder, "text/plain");
    }

    private FileEntity saveFile(String name, User owner, FolderEntity folder, String mimeType) {
        return fileRepository.saveAndFlush(FileEntity.builder()
                .name(name)
                .storagePath(UUID.randomUUID().toString())
                .mimeType(mimeType)
                .size(4L)
                .ownerUser(owner)
                .createdByUser(owner)
                .folder(folder)
                .build());
    }

    private void saveFingerprint(FileEntity file, String hashValue) {
        fileFingerprintRepository.saveAndFlush(FileFingerprint.builder()
                .file(file)
                .algorithm(FingerprintAlgorithm.SHA256)
                .hashValue(hashValue)
                .build());
        exactDuplicateGroupMaintenanceService.refreshGroup(
                file.getOwnerUser().getId(),
                FingerprintAlgorithm.SHA256,
                hashValue);
    }

    private void clearPersistenceContext() {
        entityManager.flush();
        entityManager.clear();
    }

    private void saveImageFingerprint(FileEntity file, String phash) {
        imageFingerprintRepository.saveAndFlush(ImageFingerprint.builder()
                .file(file)
                .phash(phash)
                .build());
    }

    private void saveDuplicateCandidate(
            User owner,
            FileEntity first,
            FileEntity second,
            DuplicateSearchMethod method,
            double distance,
            String thresholdVersion) {
        UUID low = first.getId().compareTo(second.getId()) < 0 ? first.getId() : second.getId();
        UUID high = first.getId().compareTo(second.getId()) < 0 ? second.getId() : first.getId();

        duplicateCandidateRepository.saveAndFlush(DuplicateCandidate.builder()
                .ownerUserId(owner.getId())
                .fileIdLow(low)
                .fileIdHigh(high)
                .method(method)
                .confidence(DuplicateConfidence.NEAR_DUPLICATE)
                .distance(distance)
                .score(Math.max(0.0, 1.0 - distance / 64.0))
                .evidenceType(DuplicateEvidenceType.IMAGE_PHASH)
                .modelName(DuplicateCandidate.NO_MODEL)
                .modelVersion(DuplicateCandidate.NO_MODEL)
                .thresholdVersion(thresholdVersion)
                .status(DuplicateCandidateStatus.ACTIVE)
                .build());
    }

    private void saveDuplicateCandidateRefresh(
            User owner,
            FileEntity source,
            DuplicateSearchMethod method,
            String modelName,
            String modelVersion,
            String thresholdVersion,
            int candidateCount) {
        duplicateCandidateRefreshRepository.saveAndFlush(DuplicateCandidateRefresh.builder()
                .ownerUserId(owner.getId())
                .sourceFileId(source.getId())
                .method(method)
                .modelName(modelName)
                .modelVersion(modelVersion)
                .thresholdVersion(thresholdVersion)
                .candidateCount(candidateCount)
                .refreshedAt(OffsetDateTime.now())
                .build());
    }

    private void saveAudioFingerprint(FileEntity file, String fingerprint) {
        saveAudioFingerprint(file, fingerprint, "fpcalc");
    }

    private void saveAudioFingerprint(FileEntity file, String fingerprint, String fingerprintVersion) {
        AudioFingerprint savedFingerprint = audioFingerprintRepository.saveAndFlush(AudioFingerprint.builder()
                .file(file)
                .durationMs(4000L)
                .codec("aac")
                .sampleRate(44100)
                .channels(2)
                .fingerprint(fingerprint)
                .fingerprintAlgorithm("chromaprint")
                .fingerprintVersion(fingerprintVersion)
                .fingerprintDurationSeconds(4)
                .build());
        assertThat(savedFingerprint.getId()).isNotNull();
    }

    private void saveFileGrant(FileEntity file, User grantee, User createdBy, Permission permission) {
        FileGrantEntity savedGrant = fileGrantRepository.saveAndFlush(FileGrantEntity.builder()
                .file(file)
                .granteeUser(grantee)
                .createdByUser(createdBy)
                .permission(permission)
                .build());
        assertThat(savedGrant.getId()).isNotNull();
    }

    private void saveFolderGrant(
            FolderEntity folder,
            User grantee,
            User createdBy,
            Permission permission,
            FolderGrantScope scope) {
        FolderGrantEntity savedGrant = folderGrantRepository.saveAndFlush(FolderGrantEntity.builder()
                .folder(folder)
                .granteeUser(grantee)
                .createdByUser(createdBy)
                .permission(permission)
                .scope(scope)
                .build());
        assertThat(savedGrant.getId()).isNotNull();
    }
}

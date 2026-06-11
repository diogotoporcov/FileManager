package com.filemanager.api.duplicate.application;

import com.filemanager.api.auth.domain.Permission;
import com.filemanager.api.duplicate.domain.DuplicateMethodStatus;
import com.filemanager.api.duplicate.domain.DuplicateSearchMethod;
import com.filemanager.api.duplicate.web.DuplicateGroupSearchRequest;
import com.filemanager.api.duplicate.web.DuplicateGroupSearchResponse;
import com.filemanager.api.duplicate.web.DuplicateSearchResponse;
import com.filemanager.api.exception.AccessDeniedException;
import com.filemanager.api.file.domain.FileEntity;
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
import com.filemanager.api.processing.messaging.FileProcessingRequestedEvent;
import com.filemanager.api.processing.persistence.result.AudioFingerprintRepository;
import com.filemanager.api.processing.persistence.result.FileFingerprintRepository;
import com.filemanager.api.processing.persistence.result.VideoEmbeddingRepository;
import com.filemanager.api.sharing.domain.FileGrantEntity;
import com.filemanager.api.sharing.domain.FolderGrantEntity;
import com.filemanager.api.sharing.domain.FolderGrantScope;
import com.filemanager.api.sharing.persistence.FileGrantRepository;
import com.filemanager.api.sharing.persistence.FolderGrantRepository;
import io.minio.MinioClient;
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
    private UserRepository userRepository;
    @Autowired
    private FileRepository fileRepository;
    @Autowired
    private FileFingerprintRepository fileFingerprintRepository;
    @Autowired
    private AudioFingerprintRepository audioFingerprintRepository;
    @Autowired
    private FolderRepository folderRepository;
    @Autowired
    private FolderClosureRepository folderClosureRepository;
    @Autowired
    private FileGrantRepository fileGrantRepository;
    @Autowired
    private FolderGrantRepository folderGrantRepository;

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

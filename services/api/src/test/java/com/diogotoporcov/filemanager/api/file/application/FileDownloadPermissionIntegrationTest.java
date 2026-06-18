package com.diogotoporcov.filemanager.api.file.application;

import com.diogotoporcov.filemanager.api.auth.domain.Permission;
import com.diogotoporcov.filemanager.api.exception.AccessDeniedException;
import com.diogotoporcov.filemanager.api.exception.ResourceNotFoundException;
import com.diogotoporcov.filemanager.api.file.domain.FileEntity;
import com.diogotoporcov.filemanager.api.file.persistence.FileRepository;
import com.diogotoporcov.filemanager.api.folder.domain.FolderClosureEntity;
import com.diogotoporcov.filemanager.api.folder.domain.FolderClosureId;
import com.diogotoporcov.filemanager.api.folder.domain.FolderEntity;
import com.diogotoporcov.filemanager.api.folder.persistence.FolderClosureRepository;
import com.diogotoporcov.filemanager.api.folder.persistence.FolderRepository;
import com.diogotoporcov.filemanager.api.identity.domain.User;
import com.diogotoporcov.filemanager.api.identity.persistence.UserRepository;
import com.diogotoporcov.filemanager.api.processing.messaging.FileProcessingRequestedEvent;
import com.diogotoporcov.filemanager.api.sharing.domain.FileGrantEntity;
import com.diogotoporcov.filemanager.api.sharing.domain.FolderGrantEntity;
import com.diogotoporcov.filemanager.api.sharing.domain.FolderGrantScope;
import com.diogotoporcov.filemanager.api.sharing.persistence.FileGrantRepository;
import com.diogotoporcov.filemanager.api.sharing.persistence.FolderGrantRepository;
import com.diogotoporcov.filemanager.api.storage.port.CreatePresignedDownloadUrlResponse;
import com.diogotoporcov.filemanager.api.storage.port.GetObjectResponse;
import com.diogotoporcov.filemanager.api.storage.port.ObjectStoragePort;
import io.minio.MinioClient;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FileDownloadPermissionIntegrationTest {
    @Autowired
    private FileService fileService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private FileRepository fileRepository;
    @Autowired
    private FolderRepository folderRepository;
    @Autowired
    private FolderClosureRepository folderClosureRepository;
    @Autowired
    private FileGrantRepository fileGrantRepository;
    @Autowired
    private FolderGrantRepository folderGrantRepository;

    @MockitoBean
    private ObjectStoragePort objectStoragePort;
    @MockitoBean
    private MinioClient minioClient;
    @MockitoBean
    private KafkaTemplate<String, FileProcessingRequestedEvent> kafkaTemplate;
    @MockitoBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        when(objectStoragePort.getObject(any())).thenReturn(GetObjectResponse.builder()
                .content(new ByteArrayInputStream("data".getBytes()))
                .contentLength(4L)
                .build());
        when(objectStoragePort.createPresignedDownloadUrl(any())).thenReturn(CreatePresignedDownloadUrlResponse.builder()
                .url("https://storage.example.test/presigned")
                .expiresAt(Instant.parse("2026-06-07T12:34:56Z"))
                .build());
    }

    @Test
    void fileOwnerCanDownload() {
        User owner = saveUser("owner@example.com");
        FileEntity file = saveFile("owned.txt", owner, null);

        FileDownload download = fileService.openDownload(file.getId(), owner.getId(), null);

        assertThat(download.getName()).isEqualTo("owned.txt");
        verify(objectStoragePort).getObject(any());
    }

    @Test
    void containingFolderOwnerCanDownloadGuestFile() {
        User actor = saveUser("actor@example.com");
        User guest = saveUser("guest@example.com");
        FolderEntity folder = saveFolder("shared", actor, null);
        FileEntity file = saveFile("guest.txt", guest, folder);

        FileDownload download = fileService.openDownload(file.getId(), actor.getId(), null);

        assertThat(download.getName()).isEqualTo("guest.txt");
    }

    @Test
    void fileViewGranteeCanDownload() {
        User actor = saveUser("actor@example.com");
        User owner = saveUser("owner@example.com");
        FileEntity file = saveFile("shared.txt", owner, null);
        saveFileGrant(file, actor, owner, Permission.FILE_VIEW);

        FileDownload download = fileService.openDownload(file.getId(), actor.getId(), null);

        assertThat(download.getName()).isEqualTo("shared.txt");
    }

    @Test
    void directFolderViewGranteeCanDownloadContainedFile() {
        User actor = saveUser("actor@example.com");
        User owner = saveUser("owner@example.com");
        FolderEntity folder = saveFolder("folder", owner, null);
        FileEntity file = saveFile("folder-file.txt", owner, folder);
        saveFolderGrant(folder, actor, owner, Permission.FOLDER_VIEW, FolderGrantScope.DIRECT);

        FileDownload download = fileService.openDownload(file.getId(), actor.getId(), null);

        assertThat(download.getName()).isEqualTo("folder-file.txt");
    }

    @Test
    void recursiveFolderViewGranteeCanDownloadDescendantFile() {
        User actor = saveUser("actor@example.com");
        User owner = saveUser("owner@example.com");
        FolderEntity parent = saveFolder("parent", owner, null);
        FolderEntity child = saveFolder("child", owner, parent);
        FileEntity file = saveFile("descendant.txt", owner, child);
        saveFolderGrant(parent, actor, owner, Permission.FOLDER_VIEW, FolderGrantScope.RECURSIVE);

        FileDownload download = fileService.openDownload(file.getId(), actor.getId(), null);

        assertThat(download.getName()).isEqualTo("descendant.txt");
    }

    @Test
    void userWithoutPermissionCannotDownload() {
        User actor = saveUser("actor@example.com");
        User owner = saveUser("owner@example.com");
        FileEntity file = saveFile("private.txt", owner, null);

        assertThrows(AccessDeniedException.class, () -> fileService.openDownload(file.getId(), actor.getId(), null));
    }

    @Test
    void revokedGrantCannotDownload() {
        User actor = saveUser("actor@example.com");
        User owner = saveUser("owner@example.com");
        FileEntity file = saveFile("revoked.txt", owner, null);
        FileGrantEntity grant = saveFileGrant(file, actor, owner, Permission.FILE_VIEW);
        grant.setRevokedAt(OffsetDateTime.now());
        fileGrantRepository.saveAndFlush(grant);

        assertThrows(AccessDeniedException.class, () -> fileService.openDownload(file.getId(), actor.getId(), null));
    }

    @Test
    void deletedFileCannotDownload() {
        User owner = saveUser("owner@example.com");
        FileEntity file = saveFile("deleted.txt", owner, null);
        file.setDeletedAt(OffsetDateTime.now());
        fileRepository.saveAndFlush(file);

        assertThrows(ResourceNotFoundException.class, () -> fileService.openDownload(file.getId(), owner.getId(), null));
    }

    @Test
    void deletedContainingFolderDoesNotAuthorizeDownload() {
        User actor = saveUser("actor@example.com");
        User guest = saveUser("guest@example.com");
        FolderEntity folder = saveFolder("deleted-folder", actor, null);
        FileEntity file = saveFile("guest.txt", guest, folder);
        folder.setDeletedAt(OffsetDateTime.now());
        folderRepository.saveAndFlush(folder);

        assertThrows(AccessDeniedException.class, () -> fileService.openDownload(file.getId(), actor.getId(), null));
    }

    @Test
    void fileOwnerCanCreatePresignedDownloadUrl() {
        User owner = saveUser("owner@example.com");
        FileEntity file = saveFile("owned.txt", owner, null);

        PresignedDownloadUrl url = fileService.createPresignedDownloadUrl(file.getId(), owner.getId());

        assertThat(url.getUrl()).isEqualTo("https://storage.example.test/presigned");
        assertThat(url.getMethod()).isEqualTo("GET");
        assertThat(url.getExpiresInSeconds()).isEqualTo(300L);
    }

    @Test
    void fileViewGranteeCanCreatePresignedDownloadUrl() {
        User actor = saveUser("actor@example.com");
        User owner = saveUser("owner@example.com");
        FileEntity file = saveFile("shared.txt", owner, null);
        saveFileGrant(file, actor, owner, Permission.FILE_VIEW);

        PresignedDownloadUrl url = fileService.createPresignedDownloadUrl(file.getId(), actor.getId());

        assertThat(url.getUrl()).doesNotContain(file.getStoragePath());
    }

    @Test
    void recursiveFolderViewGranteeCanCreatePresignedDownloadUrlForDescendantFile() {
        User actor = saveUser("actor@example.com");
        User owner = saveUser("owner@example.com");
        FolderEntity parent = saveFolder("parent", owner, null);
        FolderEntity child = saveFolder("child", owner, parent);
        FileEntity file = saveFile("descendant.txt", owner, child);
        saveFolderGrant(parent, actor, owner, Permission.FOLDER_VIEW, FolderGrantScope.RECURSIVE);

        PresignedDownloadUrl url = fileService.createPresignedDownloadUrl(file.getId(), actor.getId());

        assertThat(url.getUrl()).isEqualTo("https://storage.example.test/presigned");
    }

    @Test
    void unauthorizedUserCannotCreatePresignedDownloadUrl() {
        User actor = saveUser("actor@example.com");
        User owner = saveUser("owner@example.com");
        FileEntity file = saveFile("private.txt", owner, null);

        assertThrows(AccessDeniedException.class, () -> fileService.createPresignedDownloadUrl(file.getId(), actor.getId()));
    }

    @Test
    void deletedFileCannotCreatePresignedDownloadUrl() {
        User owner = saveUser("owner@example.com");
        FileEntity file = saveFile("deleted.txt", owner, null);
        file.setDeletedAt(OffsetDateTime.now());
        fileRepository.saveAndFlush(file);

        assertThrows(ResourceNotFoundException.class, () -> fileService.createPresignedDownloadUrl(file.getId(), owner.getId()));
    }

    @Test
    void revokedGrantCannotCreatePresignedDownloadUrl() {
        User actor = saveUser("actor@example.com");
        User owner = saveUser("owner@example.com");
        FileEntity file = saveFile("revoked.txt", owner, null);
        FileGrantEntity grant = saveFileGrant(file, actor, owner, Permission.FILE_VIEW);
        grant.setRevokedAt(OffsetDateTime.now());
        fileGrantRepository.saveAndFlush(grant);

        assertThrows(AccessDeniedException.class, () -> fileService.createPresignedDownloadUrl(file.getId(), actor.getId()));
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
        return fileRepository.saveAndFlush(FileEntity.builder()
                .name(name)
                .storagePath(UUID.randomUUID().toString())
                .mimeType("text/plain")
                .size(4L)
                .ownerUser(owner)
                .createdByUser(owner)
                .folder(folder)
                .build());
    }

    private FileGrantEntity saveFileGrant(FileEntity file, User grantee, User createdBy, Permission permission) {
        return fileGrantRepository.saveAndFlush(FileGrantEntity.builder()
                .file(file)
                .granteeUser(grantee)
                .createdByUser(createdBy)
                .permission(permission)
                .build());
    }

    private void saveFolderGrant(
            FolderEntity folder,
            User grantee,
            User createdBy,
            Permission permission,
            FolderGrantScope scope) {
        folderGrantRepository.saveAndFlush(FolderGrantEntity.builder()
                .folder(folder)
                .granteeUser(grantee)
                .createdByUser(createdBy)
                .permission(permission)
                .scope(scope)
                .build());
    }
}

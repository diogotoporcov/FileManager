package com.diogotoporcov.filemanager.api.sharing.application;

import com.diogotoporcov.filemanager.api.auth.domain.Permission;
import com.diogotoporcov.filemanager.api.exception.AccessDeniedException;
import com.diogotoporcov.filemanager.api.exception.ResourceNotFoundException;
import com.diogotoporcov.filemanager.api.file.domain.FileEntity;
import com.diogotoporcov.filemanager.api.file.persistence.FileRepository;
import com.diogotoporcov.filemanager.api.folder.domain.FolderEntity;
import com.diogotoporcov.filemanager.api.folder.persistence.FolderRepository;
import com.diogotoporcov.filemanager.api.identity.domain.User;
import com.diogotoporcov.filemanager.api.identity.persistence.UserRepository;
import com.diogotoporcov.filemanager.api.sharing.domain.FileGrantEntity;
import com.diogotoporcov.filemanager.api.sharing.domain.FolderGrantEntity;
import com.diogotoporcov.filemanager.api.sharing.domain.FolderGrantScope;
import com.diogotoporcov.filemanager.api.sharing.persistence.FileGrantRepository;
import com.diogotoporcov.filemanager.api.sharing.persistence.FolderGrantRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SharingServiceTest {
    @Mock
    private FileGrantRepository fileGrantRepository;
    @Mock
    private FolderGrantRepository folderGrantRepository;
    @Mock
    private FileRepository fileRepository;
    @Mock
    private FolderRepository folderRepository;
    @Mock
    private UserRepository userRepository;

    private SharingService sharingService;
    private UUID ownerId;
    private UUID granteeId;
    private UUID fileId;
    private UUID folderId;
    private User owner;
    private User grantee;

    @BeforeEach
    void setUp() {
        sharingService = new SharingService(
                fileGrantRepository,
                folderGrantRepository,
                fileRepository,
                folderRepository,
                userRepository);
        ownerId = UUID.randomUUID();
        granteeId = UUID.randomUUID();
        fileId = UUID.randomUUID();
        folderId = UUID.randomUUID();
        owner = user(ownerId);
        grantee = user(granteeId);
    }

    @Test
    void ownerCreatesFileGrant() {
        FileEntity file = FileEntity.builder().id(fileId).ownerUser(owner).build();
        when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.of(file));
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(owner));
        when(userRepository.findById(granteeId)).thenReturn(Optional.of(grantee));
        when(fileGrantRepository.save(any(FileGrantEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var grants = sharingService.createFileGrants(fileId, granteeId, List.of(Permission.FILE_VIEW), ownerId);

        assertEquals(1, grants.size());
        ArgumentCaptor<FileGrantEntity> captor = ArgumentCaptor.forClass(FileGrantEntity.class);
        verify(fileGrantRepository).save(captor.capture());
        assertSame(file, captor.getValue().getFile());
        assertSame(grantee, captor.getValue().getGranteeUser());
        assertEquals(Permission.FILE_VIEW, captor.getValue().getPermission());
    }

    @Test
    void ownerCreatesFolderGrant() {
        FolderEntity folder = FolderEntity.builder().id(folderId).ownerUser(owner).build();
        when(folderRepository.findByIdAndDeletedAtIsNull(folderId)).thenReturn(Optional.of(folder));
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(owner));
        when(userRepository.findById(granteeId)).thenReturn(Optional.of(grantee));
        when(folderGrantRepository.save(any(FolderGrantEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var grants = sharingService.createFolderGrants(folderId, granteeId, List.of(Permission.FOLDER_UPLOAD_FILE), ownerId);

        assertEquals(1, grants.size());
        verify(folderGrantRepository).save(any(FolderGrantEntity.class));
    }

    @Test
    void folderGrantDefaultsToDirectWhenScopeIsNull() {
        FolderEntity folder = FolderEntity.builder().id(folderId).ownerUser(owner).build();
        when(folderRepository.findByIdAndDeletedAtIsNull(folderId)).thenReturn(Optional.of(folder));
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(owner));
        when(userRepository.findById(granteeId)).thenReturn(Optional.of(grantee));
        when(folderGrantRepository.save(any(FolderGrantEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        sharingService.createFolderGrants(
                folderId,
                granteeId,
                List.of(Permission.FOLDER_VIEW),
                null,
                ownerId);

        ArgumentCaptor<FolderGrantEntity> captor = ArgumentCaptor.forClass(FolderGrantEntity.class);
        verify(folderGrantRepository).save(captor.capture());
        assertEquals(FolderGrantScope.DIRECT, captor.getValue().getScope());
    }

    @Test
    void ownerCreatesRecursiveFolderGrant() {
        FolderEntity folder = FolderEntity.builder().id(folderId).ownerUser(owner).build();
        when(folderRepository.findByIdAndDeletedAtIsNull(folderId)).thenReturn(Optional.of(folder));
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(owner));
        when(userRepository.findById(granteeId)).thenReturn(Optional.of(grantee));
        when(folderGrantRepository.save(any(FolderGrantEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var grants = sharingService.createFolderGrants(
                folderId,
                granteeId,
                List.of(Permission.FOLDER_VIEW),
                FolderGrantScope.RECURSIVE,
                ownerId);

        assertEquals(FolderGrantScope.RECURSIVE, grants.getFirst().getScope());
    }

    @Test
    void nonOwnerCannotCreateFileGrant() {
        FileEntity file = FileEntity.builder().id(fileId).ownerUser(user(UUID.randomUUID())).build();
        when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.of(file));
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(owner));
        when(userRepository.findById(granteeId)).thenReturn(Optional.of(grantee));

        assertThrows(AccessDeniedException.class,
                () -> sharingService.createFileGrants(fileId, granteeId, List.of(Permission.FILE_VIEW), ownerId));
        verify(fileGrantRepository, never()).save(any());
    }

    @Test
    void nonOwnerCannotCreateFolderGrant() {
        FolderEntity folder = FolderEntity.builder().id(folderId).ownerUser(user(UUID.randomUUID())).build();
        when(folderRepository.findByIdAndDeletedAtIsNull(folderId)).thenReturn(Optional.of(folder));
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(owner));
        when(userRepository.findById(granteeId)).thenReturn(Optional.of(grantee));

        assertThrows(AccessDeniedException.class,
                () -> sharingService.createFolderGrants(folderId, granteeId, List.of(Permission.FOLDER_VIEW), ownerId));
        verify(folderGrantRepository, never()).save(any());
    }

    @Test
    void nonOwnerCannotCreateRecursiveFolderGrant() {
        FolderEntity folder = FolderEntity.builder().id(folderId).ownerUser(user(UUID.randomUUID())).build();
        when(folderRepository.findByIdAndDeletedAtIsNull(folderId)).thenReturn(Optional.of(folder));
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(owner));
        when(userRepository.findById(granteeId)).thenReturn(Optional.of(grantee));

        assertThrows(AccessDeniedException.class,
                () -> sharingService.createFolderGrants(
                        folderId,
                        granteeId,
                        List.of(Permission.FOLDER_VIEW),
                        FolderGrantScope.RECURSIVE,
                        ownerId));
        verify(folderGrantRepository, never()).save(any());
    }

    @Test
    void duplicateActiveFileGrantReturnsExistingGrant() {
        FileEntity file = FileEntity.builder().id(fileId).ownerUser(owner).build();
        FileGrantEntity existing = FileGrantEntity.builder().id(UUID.randomUUID()).file(file).granteeUser(grantee).permission(Permission.FILE_VIEW).build();
        when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.of(file));
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(owner));
        when(userRepository.findById(granteeId)).thenReturn(Optional.of(grantee));
        when(fileGrantRepository.findByFileIdAndGranteeUserIdAndPermissionAndRevokedAtIsNull(fileId, granteeId, Permission.FILE_VIEW))
                .thenReturn(Optional.of(existing));

        var grants = sharingService.createFileGrants(fileId, granteeId, List.of(Permission.FILE_VIEW), ownerId);

        assertSame(existing, grants.getFirst());
        verify(fileGrantRepository, never()).save(any());
    }

    @Test
    void duplicateActiveFolderGrantUpdatesDirectScopeToRecursive() {
        FolderEntity folder = FolderEntity.builder().id(folderId).ownerUser(owner).build();
        FolderGrantEntity existing = FolderGrantEntity.builder()
                .id(UUID.randomUUID())
                .folder(folder)
                .granteeUser(grantee)
                .permission(Permission.FOLDER_VIEW)
                .scope(FolderGrantScope.DIRECT)
                .build();
        when(folderRepository.findByIdAndDeletedAtIsNull(folderId)).thenReturn(Optional.of(folder));
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(owner));
        when(userRepository.findById(granteeId)).thenReturn(Optional.of(grantee));
        when(folderGrantRepository.findByFolderIdAndGranteeUserIdAndPermissionAndRevokedAtIsNull(
                folderId,
                granteeId,
                Permission.FOLDER_VIEW)).thenReturn(Optional.of(existing));
        when(folderGrantRepository.save(existing)).thenReturn(existing);

        var grants = sharingService.createFolderGrants(
                folderId,
                granteeId,
                List.of(Permission.FOLDER_VIEW),
                FolderGrantScope.RECURSIVE,
                ownerId);

        assertSame(existing, grants.getFirst());
        assertEquals(FolderGrantScope.RECURSIVE, existing.getScope());
        verify(folderGrantRepository).save(existing);
    }

    @Test
    void duplicateActiveFolderGrantUpdatesRecursiveScopeToDirect() {
        FolderEntity folder = FolderEntity.builder().id(folderId).ownerUser(owner).build();
        FolderGrantEntity existing = FolderGrantEntity.builder()
                .id(UUID.randomUUID())
                .folder(folder)
                .granteeUser(grantee)
                .permission(Permission.FOLDER_VIEW)
                .scope(FolderGrantScope.RECURSIVE)
                .build();
        when(folderRepository.findByIdAndDeletedAtIsNull(folderId)).thenReturn(Optional.of(folder));
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(owner));
        when(userRepository.findById(granteeId)).thenReturn(Optional.of(grantee));
        when(folderGrantRepository.findByFolderIdAndGranteeUserIdAndPermissionAndRevokedAtIsNull(
                folderId,
                granteeId,
                Permission.FOLDER_VIEW)).thenReturn(Optional.of(existing));
        when(folderGrantRepository.save(existing)).thenReturn(existing);

        var grants = sharingService.createFolderGrants(
                folderId,
                granteeId,
                List.of(Permission.FOLDER_VIEW),
                FolderGrantScope.DIRECT,
                ownerId);

        assertSame(existing, grants.getFirst());
        assertEquals(FolderGrantScope.DIRECT, existing.getScope());
        verify(folderGrantRepository).save(existing);
    }

    @Test
    void missingGranteeIsRejected() {
        FileEntity file = FileEntity.builder().id(fileId).ownerUser(owner).build();
        when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.of(file));
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(owner));
        when(userRepository.findById(granteeId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> sharingService.createFileGrants(fileId, granteeId, List.of(Permission.FILE_VIEW), ownerId));
    }

    @Test
    void missingOrDeletedFileIsRejected() {
        when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> sharingService.createFileGrants(fileId, granteeId, List.of(Permission.FILE_VIEW), ownerId));
    }

    @Test
    void invalidFilePermissionIsRejected() {
        FileEntity file = FileEntity.builder().id(fileId).ownerUser(owner).build();
        when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.of(file));
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(owner));
        when(userRepository.findById(granteeId)).thenReturn(Optional.of(grantee));

        assertThrows(IllegalArgumentException.class,
                () -> sharingService.createFileGrants(fileId, granteeId, List.of(Permission.FOLDER_VIEW), ownerId));
    }

    @Test
    void fileSharePermissionIsRejectedForV1Grant() {
        FileEntity file = FileEntity.builder().id(fileId).ownerUser(owner).build();
        when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.of(file));
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(owner));
        when(userRepository.findById(granteeId)).thenReturn(Optional.of(grantee));

        assertThrows(IllegalArgumentException.class,
                () -> sharingService.createFileGrants(fileId, granteeId, List.of(Permission.FILE_SHARE), ownerId));
    }

    @Test
    void invalidFolderPermissionIsRejected() {
        FolderEntity folder = FolderEntity.builder().id(folderId).ownerUser(owner).build();
        when(folderRepository.findByIdAndDeletedAtIsNull(folderId)).thenReturn(Optional.of(folder));
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(owner));
        when(userRepository.findById(granteeId)).thenReturn(Optional.of(grantee));

        assertThrows(IllegalArgumentException.class,
                () -> sharingService.createFolderGrants(folderId, granteeId, List.of(Permission.FILE_VIEW), ownerId));
    }

    @Test
    void folderManagePermissionsGrantIsRejectedForV1Grant() {
        FolderEntity folder = FolderEntity.builder().id(folderId).ownerUser(owner).build();
        when(folderRepository.findByIdAndDeletedAtIsNull(folderId)).thenReturn(Optional.of(folder));
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(owner));
        when(userRepository.findById(granteeId)).thenReturn(Optional.of(grantee));

        assertThrows(IllegalArgumentException.class,
                () -> sharingService.createFolderGrants(folderId, granteeId, List.of(Permission.FOLDER_MANAGE_PERMISSIONS), ownerId));
    }

    @Test
    void selfFileGrantIsRejected() {
        FileEntity file = FileEntity.builder().id(fileId).ownerUser(owner).build();
        when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.of(file));
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(owner));

        assertThrows(IllegalArgumentException.class,
                () -> sharingService.createFileGrants(fileId, ownerId, List.of(Permission.FILE_VIEW), ownerId));
    }

    @Test
    void selfFolderGrantIsRejected() {
        FolderEntity folder = FolderEntity.builder().id(folderId).ownerUser(owner).build();
        when(folderRepository.findByIdAndDeletedAtIsNull(folderId)).thenReturn(Optional.of(folder));
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(owner));

        assertThrows(IllegalArgumentException.class,
                () -> sharingService.createFolderGrants(folderId, ownerId, List.of(Permission.FOLDER_VIEW), ownerId));
    }

    @Test
    void ownerRevokesFolderGrant() {
        FolderEntity folder = FolderEntity.builder().id(folderId).ownerUser(owner).build();
        UUID grantId = UUID.randomUUID();
        FolderGrantEntity grant = FolderGrantEntity.builder().id(grantId).folder(folder).build();
        when(folderRepository.findByIdAndDeletedAtIsNull(folderId)).thenReturn(Optional.of(folder));
        when(folderGrantRepository.findByIdAndFolderIdAndRevokedAtIsNull(grantId, folderId)).thenReturn(Optional.of(grant));

        sharingService.revokeFolderGrant(folderId, grantId, ownerId);

        assertNotNull(grant.getRevokedAt());
        verify(folderGrantRepository).save(grant);
    }

    @Test
    void nonOwnerCannotRevokeFileGrant() {
        FileEntity file = FileEntity.builder().id(fileId).ownerUser(user(UUID.randomUUID())).build();
        when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.of(file));

        assertThrows(AccessDeniedException.class,
                () -> sharingService.revokeFileGrant(fileId, UUID.randomUUID(), ownerId));
        verify(fileGrantRepository, never()).save(any());
    }

    private User user(UUID id) {
        return User.builder().id(id).email(id + "@example.com").build();
    }
}

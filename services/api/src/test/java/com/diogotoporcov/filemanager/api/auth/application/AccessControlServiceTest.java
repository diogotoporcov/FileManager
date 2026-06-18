package com.diogotoporcov.filemanager.api.auth.application;

import com.diogotoporcov.filemanager.api.auth.domain.Permission;
import com.diogotoporcov.filemanager.api.exception.AccessDeniedException;
import com.diogotoporcov.filemanager.api.exception.ResourceNotFoundException;
import com.diogotoporcov.filemanager.api.file.domain.FileEntity;
import com.diogotoporcov.filemanager.api.file.persistence.FileRepository;
import com.diogotoporcov.filemanager.api.folder.domain.FolderEntity;
import com.diogotoporcov.filemanager.api.folder.persistence.FolderRepository;
import com.diogotoporcov.filemanager.api.identity.domain.User;
import com.diogotoporcov.filemanager.api.sharing.persistence.FileGrantRepository;
import com.diogotoporcov.filemanager.api.sharing.persistence.FolderGrantRepository;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccessControlServiceTest {

    @Mock
    private FileRepository fileRepository;
    @Mock
    private FolderRepository folderRepository;
    @Mock
    private FileGrantRepository fileGrantRepository;
    @Mock
    private FolderGrantRepository folderGrantRepository;

    @InjectMocks
    private AccessControlService accessControlService;

    private UUID actorUserId;
    private UUID fileId;
    private UUID folderId;

    @BeforeEach
    void setUp() {
        actorUserId = UUID.randomUUID();
        fileId = UUID.randomUUID();
        folderId = UUID.randomUUID();
    }

    @Test
    void ownerHasAllFilePermissions() {
        FileEntity file = FileEntity.builder().id(fileId).ownerUser(user(actorUserId)).build();
        when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.of(file));

        assertDoesNotThrow(() -> accessControlService.assertCanAccessFile(actorUserId, fileId, Permission.FILE_VIEW));
        assertDoesNotThrow(() -> accessControlService.assertCanAccessFile(actorUserId, fileId, Permission.FILE_DELETE));
        assertTrue(accessControlService.hasFilePermission(actorUserId, file, Permission.FILE_SHARE));
    }

    @Test
    void nonOwnerCannotAccessFileWithoutGrant() {
        FileEntity file = FileEntity.builder().id(fileId).ownerUser(user(UUID.randomUUID())).build();
        when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.of(file));

        assertThrows(AccessDeniedException.class,
                () -> accessControlService.assertCanAccessFile(actorUserId, fileId, Permission.FILE_VIEW));
        assertFalse(accessControlService.hasFilePermission(actorUserId, file, Permission.FILE_VIEW));
    }

    @Test
    void deletedFileIsNotResolvedAsAccessible() {
        when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> accessControlService.assertCanAccessFile(actorUserId, fileId, Permission.FILE_VIEW));
    }

    @Test
    void ownerHasAllFolderPermissions() {
        FolderEntity folder = FolderEntity.builder().id(folderId).ownerUser(user(actorUserId)).build();
        when(folderRepository.findByIdAndDeletedAtIsNull(folderId)).thenReturn(Optional.of(folder));

        assertDoesNotThrow(() -> accessControlService.assertCanAccessFolder(actorUserId, folderId, Permission.FOLDER_VIEW));
        assertDoesNotThrow(() -> accessControlService.assertCanAccessFolder(actorUserId, folderId, Permission.FOLDER_MANAGE_PERMISSIONS));
        assertTrue(accessControlService.hasFolderPermission(actorUserId, folder, Permission.FOLDER_UPLOAD_FILE));
    }

    @Test
    void nonOwnerCannotAccessFolderWithoutGrant() {
        FolderEntity folder = FolderEntity.builder().id(folderId).ownerUser(user(UUID.randomUUID())).build();
        when(folderRepository.findByIdAndDeletedAtIsNull(folderId)).thenReturn(Optional.of(folder));

        assertThrows(AccessDeniedException.class,
                () -> accessControlService.assertCanAccessFolder(actorUserId, folderId, Permission.FOLDER_VIEW));
        assertFalse(accessControlService.hasFolderPermission(actorUserId, folder, Permission.FOLDER_VIEW));
    }

    @Test
    void deletedResourcePermissionChecksReturnFalse() {
        FileEntity file = FileEntity.builder().ownerUser(user(actorUserId)).deletedAt(OffsetDateTime.now()).build();
        FolderEntity folder = FolderEntity.builder().ownerUser(user(actorUserId)).deletedAt(OffsetDateTime.now()).build();

        assertFalse(accessControlService.hasFilePermission(actorUserId, file, Permission.FILE_VIEW));
        assertFalse(accessControlService.hasFolderPermission(actorUserId, folder, Permission.FOLDER_VIEW));
    }

    @Test
    void ownerContextChecksRequireActorOwner() {
        assertDoesNotThrow(() -> accessControlService.assertCanViewOwner(actorUserId, actorUserId));
        assertDoesNotThrow(() -> accessControlService.assertCanUploadToOwner(actorUserId, actorUserId));
        assertDoesNotThrow(() -> accessControlService.assertCanCreateFolderForOwner(actorUserId, actorUserId));
        assertThrows(AccessDeniedException.class,
                () -> accessControlService.assertCanViewOwner(actorUserId, UUID.randomUUID()));
    }

    @Test
    void folderViewGrantAllowsFolderViewOnly() {
        FolderEntity folder = FolderEntity.builder().id(folderId).ownerUser(user(UUID.randomUUID())).build();
        when(folderRepository.findByIdAndDeletedAtIsNull(folderId)).thenReturn(Optional.of(folder));
        when(folderGrantRepository.hasActiveDirectGrant(folderId, actorUserId, Permission.FOLDER_VIEW)).thenReturn(true);

        assertDoesNotThrow(() -> accessControlService.assertCanAccessFolder(actorUserId, folderId, Permission.FOLDER_VIEW));
        assertFalse(accessControlService.hasFolderPermission(actorUserId, folder, Permission.FOLDER_UPLOAD_FILE));
    }

    @Test
    void folderUploadGrantAllowsFolderUpload() {
        FolderEntity folder = FolderEntity.builder().id(folderId).ownerUser(user(UUID.randomUUID())).build();
        when(folderGrantRepository.hasActiveDirectGrant(folderId, actorUserId, Permission.FOLDER_UPLOAD_FILE)).thenReturn(true);

        assertTrue(accessControlService.hasFolderPermission(actorUserId, folder, Permission.FOLDER_UPLOAD_FILE));
    }

    @Test
    void fileViewGrantAllowsFileViewOnly() {
        FileEntity file = FileEntity.builder().id(fileId).ownerUser(user(UUID.randomUUID())).build();
        when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.of(file));
        when(fileGrantRepository.hasActiveGrant(fileId, actorUserId, Permission.FILE_VIEW)).thenReturn(true);

        assertDoesNotThrow(() -> accessControlService.assertCanAccessFile(actorUserId, fileId, Permission.FILE_VIEW));
        assertFalse(accessControlService.hasFilePermission(actorUserId, file, Permission.FILE_DELETE));
    }

    @Test
    void folderViewGrantAllowsDirectFileView() {
        FolderEntity folder = FolderEntity.builder().id(folderId).ownerUser(user(UUID.randomUUID())).build();
        FileEntity file = FileEntity.builder().id(fileId).folder(folder).ownerUser(user(UUID.randomUUID())).build();
        when(folderGrantRepository.hasActiveDirectGrant(folderId, actorUserId, Permission.FOLDER_VIEW)).thenReturn(true);

        assertTrue(accessControlService.hasFilePermission(actorUserId, file, Permission.FILE_VIEW));
        assertFalse(accessControlService.hasFilePermission(actorUserId, file, Permission.FILE_DELETE));
    }

    @Test
    void containingFolderOwnerCanDeleteGuestUpload() {
        FolderEntity folder = FolderEntity.builder().id(folderId).ownerUser(user(actorUserId)).build();
        FileEntity file = FileEntity.builder().id(fileId).folder(folder).ownerUser(user(UUID.randomUUID())).build();

        assertTrue(accessControlService.hasFilePermission(actorUserId, file, Permission.FILE_DELETE));
        assertFalse(accessControlService.hasFilePermission(actorUserId, file, Permission.FILE_SHARE));
    }

    @Test
    void directParentFolderOwnerCanManageGuestCreatedChildFolder() {
        FolderEntity parent = FolderEntity.builder().id(UUID.randomUUID()).ownerUser(user(actorUserId)).build();
        FolderEntity child = FolderEntity.builder()
                .id(folderId)
                .parentFolder(parent)
                .ownerUser(user(UUID.randomUUID()))
                .build();

        assertTrue(accessControlService.hasFolderPermission(actorUserId, child, Permission.FOLDER_VIEW));
        assertTrue(accessControlService.hasFolderPermission(actorUserId, child, Permission.FOLDER_RENAME));
        assertTrue(accessControlService.hasFolderPermission(actorUserId, child, Permission.FOLDER_DELETE));
        assertFalse(accessControlService.hasFolderPermission(actorUserId, child, Permission.FOLDER_CREATE));
        assertFalse(accessControlService.hasFolderPermission(actorUserId, child, Permission.FOLDER_MANAGE_PERMISSIONS));
    }

    @Test
    void parentFolderOwnerDoesNotAutomaticallyManageDeeperDescendant() {
        FolderEntity root = FolderEntity.builder().id(UUID.randomUUID()).ownerUser(user(actorUserId)).build();
        FolderEntity parent = FolderEntity.builder()
                .id(UUID.randomUUID())
                .parentFolder(root)
                .ownerUser(user(UUID.randomUUID()))
                .build();
        FolderEntity descendant = FolderEntity.builder()
                .id(folderId)
                .parentFolder(parent)
                .ownerUser(user(UUID.randomUUID()))
                .build();

        assertFalse(accessControlService.hasFolderPermission(actorUserId, descendant, Permission.FOLDER_RENAME));
        assertFalse(accessControlService.hasFolderPermission(actorUserId, descendant, Permission.FOLDER_DELETE));
    }

    @Test
    void recursiveFolderViewGrantAuthorizesDescendantFolders() {
        FolderEntity parent = FolderEntity.builder().id(UUID.randomUUID()).ownerUser(user(UUID.randomUUID())).build();
        FolderEntity child = FolderEntity.builder()
                .id(folderId)
                .parentFolder(parent)
                .ownerUser(user(UUID.randomUUID()))
                .build();
        when(folderGrantRepository.hasActiveRecursiveGrantOnFolderOrAncestor(folderId, actorUserId, Permission.FOLDER_VIEW))
                .thenReturn(true);

        assertTrue(accessControlService.hasFolderPermission(actorUserId, child, Permission.FOLDER_VIEW));
    }

    @Test
    void recursiveFolderViewGrantAuthorizesFilesInDescendantFolders() {
        FolderEntity parent = FolderEntity.builder().id(UUID.randomUUID()).ownerUser(user(UUID.randomUUID())).build();
        FolderEntity child = FolderEntity.builder().id(folderId).parentFolder(parent).ownerUser(user(UUID.randomUUID())).build();
        FileEntity file = FileEntity.builder().id(fileId).folder(child).ownerUser(user(UUID.randomUUID())).build();
        when(folderGrantRepository.hasActiveRecursiveGrantOnFolderOrAncestor(folderId, actorUserId, Permission.FOLDER_VIEW))
                .thenReturn(true);

        assertTrue(accessControlService.hasFilePermission(actorUserId, file, Permission.FILE_VIEW));
    }

    @Test
    void recursiveFolderUploadGrantAuthorizesDescendantUpload() {
        FolderEntity parent = FolderEntity.builder().id(UUID.randomUUID()).ownerUser(user(UUID.randomUUID())).build();
        FolderEntity child = FolderEntity.builder().id(folderId).parentFolder(parent).ownerUser(user(UUID.randomUUID())).build();
        when(folderGrantRepository.hasActiveRecursiveGrantOnFolderOrAncestor(folderId, actorUserId, Permission.FOLDER_UPLOAD_FILE))
                .thenReturn(true);

        assertTrue(accessControlService.hasFolderPermission(actorUserId, child, Permission.FOLDER_UPLOAD_FILE));
    }

    @Test
    void recursiveFolderCreateGrantAuthorizesDescendantFolderCreation() {
        FolderEntity parent = FolderEntity.builder().id(UUID.randomUUID()).ownerUser(user(UUID.randomUUID())).build();
        FolderEntity child = FolderEntity.builder().id(folderId).parentFolder(parent).ownerUser(user(UUID.randomUUID())).build();
        when(folderGrantRepository.hasActiveRecursiveGrantOnFolderOrAncestor(folderId, actorUserId, Permission.FOLDER_CREATE))
                .thenReturn(true);

        assertTrue(accessControlService.hasFolderPermission(actorUserId, child, Permission.FOLDER_CREATE));
    }

    @Test
    void recursiveRenameAndDeleteGrantsAuthorizeDescendantFolderManagement() {
        FolderEntity parent = FolderEntity.builder().id(UUID.randomUUID()).ownerUser(user(UUID.randomUUID())).build();
        FolderEntity child = FolderEntity.builder().id(folderId).parentFolder(parent).ownerUser(user(UUID.randomUUID())).build();
        when(folderGrantRepository.hasActiveRecursiveGrantOnFolderOrAncestor(folderId, actorUserId, Permission.FOLDER_RENAME))
                .thenReturn(true);
        when(folderGrantRepository.hasActiveRecursiveGrantOnFolderOrAncestor(folderId, actorUserId, Permission.FOLDER_DELETE))
                .thenReturn(true);

        assertTrue(accessControlService.hasFolderPermission(actorUserId, child, Permission.FOLDER_RENAME));
        assertTrue(accessControlService.hasFolderPermission(actorUserId, child, Permission.FOLDER_DELETE));
    }

    @Test
    void guestCannotManageAnotherGuestsChildFolderThroughParentViewGrant() {
        FolderEntity parent = FolderEntity.builder().id(UUID.randomUUID()).ownerUser(user(UUID.randomUUID())).build();
        FolderEntity child = FolderEntity.builder()
                .id(folderId)
                .parentFolder(parent)
                .ownerUser(user(UUID.randomUUID()))
                .build();

        assertFalse(accessControlService.hasFolderPermission(actorUserId, child, Permission.FOLDER_DELETE));
    }

    @Test
    void revokedGrantDoesNotAuthorizeAccess() {
        FileEntity file = FileEntity.builder().id(fileId).ownerUser(user(UUID.randomUUID())).build();
        when(fileGrantRepository.hasActiveGrant(fileId, actorUserId, Permission.FILE_VIEW)).thenReturn(false);

        assertFalse(accessControlService.hasFilePermission(actorUserId, file, Permission.FILE_VIEW));
    }

    @Test
    void parentFolderGrantDoesNotAuthorizeNestedFolderDirectly() {
        UUID parentId = UUID.randomUUID();
        FolderEntity parent = FolderEntity.builder().id(parentId).ownerUser(user(UUID.randomUUID())).build();
        FolderEntity child = FolderEntity.builder().id(folderId).parentFolder(parent).ownerUser(user(UUID.randomUUID())).build();

        assertFalse(accessControlService.hasFolderPermission(actorUserId, child, Permission.FOLDER_VIEW));
    }

    @Test
    void parentFolderGrantDoesNotAuthorizeNestedFile() {
        UUID parentId = UUID.randomUUID();
        FolderEntity parent = FolderEntity.builder().id(parentId).ownerUser(user(UUID.randomUUID())).build();
        FolderEntity child = FolderEntity.builder().id(folderId).parentFolder(parent).ownerUser(user(UUID.randomUUID())).build();
        FileEntity file = FileEntity.builder().id(fileId).folder(child).ownerUser(user(UUID.randomUUID())).build();
        when(fileGrantRepository.hasActiveGrant(fileId, actorUserId, Permission.FILE_VIEW)).thenReturn(false);

        assertFalse(accessControlService.hasFilePermission(actorUserId, file, Permission.FILE_VIEW));
    }

    private User user(UUID id) {
        return User.builder().id(id).email(id + "@example.com").build();
    }
}

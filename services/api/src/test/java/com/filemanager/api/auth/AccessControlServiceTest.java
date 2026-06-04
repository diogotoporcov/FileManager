package com.filemanager.api.auth;

import com.filemanager.api.entity.FileEntity;
import com.filemanager.api.entity.FolderEntity;
import com.filemanager.api.entity.User;
import com.filemanager.api.exception.AccessDeniedException;
import com.filemanager.api.exception.ResourceNotFoundException;
import com.filemanager.api.repository.FileRepository;
import com.filemanager.api.repository.FolderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

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
        FileEntity file = FileEntity.builder().ownerUser(user(actorUserId)).build();
        when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.of(file));

        assertDoesNotThrow(() -> accessControlService.assertCanAccessFile(actorUserId, fileId, Permission.FILE_VIEW));
        assertDoesNotThrow(() -> accessControlService.assertCanAccessFile(actorUserId, fileId, Permission.FILE_DELETE));
        assertTrue(accessControlService.hasFilePermission(actorUserId, file, Permission.FILE_SHARE));
    }

    @Test
    void nonOwnerCannotAccessFileWithoutGrant() {
        FileEntity file = FileEntity.builder().ownerUser(user(UUID.randomUUID())).build();
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
        FolderEntity folder = FolderEntity.builder().ownerUser(user(actorUserId)).build();
        when(folderRepository.findByIdAndDeletedAtIsNull(folderId)).thenReturn(Optional.of(folder));

        assertDoesNotThrow(() -> accessControlService.assertCanAccessFolder(actorUserId, folderId, Permission.FOLDER_VIEW));
        assertDoesNotThrow(() -> accessControlService.assertCanAccessFolder(actorUserId, folderId, Permission.FOLDER_MANAGE_PERMISSIONS));
        assertTrue(accessControlService.hasFolderPermission(actorUserId, folder, Permission.FOLDER_UPLOAD_FILE));
    }

    @Test
    void nonOwnerCannotAccessFolderWithoutGrant() {
        FolderEntity folder = FolderEntity.builder().ownerUser(user(UUID.randomUUID())).build();
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

    private User user(UUID id) {
        return User.builder().id(id).email(id + "@example.com").build();
    }
}
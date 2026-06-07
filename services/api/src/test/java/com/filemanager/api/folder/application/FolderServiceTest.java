package com.filemanager.api.folder.application;

import com.filemanager.api.auth.application.AccessControlService;
import com.filemanager.api.auth.domain.Permission;
import com.filemanager.api.exception.ConflictException;
import com.filemanager.api.file.persistence.FileRepository;
import com.filemanager.api.folder.web.CreateFolderRequest;
import com.filemanager.api.folder.web.FolderResponseMapper;
import com.filemanager.api.folder.web.UpdateFolderRequest;
import com.filemanager.api.folder.domain.FolderClosureEntity;
import com.filemanager.api.folder.domain.FolderClosureId;
import com.filemanager.api.folder.domain.FolderEntity;
import com.filemanager.api.folder.persistence.FolderClosureRepository;
import com.filemanager.api.folder.persistence.FolderRepository;
import com.filemanager.api.identity.domain.User;
import com.filemanager.api.identity.persistence.UserRepository;
import com.filemanager.api.tag.application.TagService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FolderServiceTest {
    @Mock
    private FolderRepository folderRepository;
    @Mock
    private FolderClosureRepository folderClosureRepository;
    @Mock
    private FileRepository fileRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AccessControlService accessControlService;
    @Mock
    private TagService tagService;

    private FolderService folderService;
    private UUID actorUserId;
    private User actorUser;

    @BeforeEach
    void setUp() {
        actorUserId = UUID.randomUUID();
        actorUser = User.builder().id(actorUserId).email("actor@example.com").build();
        folderService = new FolderService(
                folderRepository,
                folderClosureRepository,
                fileRepository,
                userRepository,
                accessControlService,
                new FolderResponseMapper(),
                tagService);
    }

    @Test
    void createRootFolderUsesAuthenticatedUserAsOwner() {
        CreateFolderRequest request = new CreateFolderRequest();
        request.setName(" Wedding Guests ");
        when(userRepository.findById(actorUserId)).thenReturn(Optional.of(actorUser));
        when(folderRepository.save(any(FolderEntity.class))).thenAnswer(invocation -> {
            FolderEntity folder = invocation.getArgument(0);
            folder.setId(UUID.randomUUID());

            return folder;
        });

        var response = folderService.createFolder(request, actorUserId);

        verify(accessControlService).assertCanCreateFolderForOwner(actorUserId, actorUserId);
        ArgumentCaptor<FolderEntity> folderCaptor = ArgumentCaptor.forClass(FolderEntity.class);
        verify(folderRepository).save(folderCaptor.capture());
        assertEquals("Wedding Guests", folderCaptor.getValue().getName());
        assertEquals(actorUser, folderCaptor.getValue().getOwnerUser());
        assertEquals(actorUser, folderCaptor.getValue().getCreatedByUser());
        assertEquals("Wedding Guests", response.getName());
    }

    @Test
    void createChildFolderUsesAuthenticatedUserAsOwner() {
        UUID parentId = UUID.randomUUID();
        User parentOwner = User.builder().id(UUID.randomUUID()).email("owner@example.com").build();
        FolderEntity parent = FolderEntity.builder().id(parentId).ownerUser(parentOwner).build();
        CreateFolderRequest request = new CreateFolderRequest();
        request.setName("Uploads");
        request.setParentFolderId(parentId);
        when(userRepository.findById(actorUserId)).thenReturn(Optional.of(actorUser));
        when(folderRepository.findByIdAndDeletedAtIsNull(parentId)).thenReturn(Optional.of(parent));
        when(folderRepository.save(any(FolderEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(folderClosureRepository.findByDescendantFolderOrderByDepthAsc(parent))
                .thenReturn(List.of(parentClosureRow(parent)));

        folderService.createFolder(request, actorUserId);

        verify(accessControlService).assertCanAccessFolder(actorUserId, parentId, Permission.FOLDER_CREATE);
        ArgumentCaptor<FolderEntity> folderCaptor = ArgumentCaptor.forClass(FolderEntity.class);
        verify(folderRepository).save(folderCaptor.capture());
        assertEquals(parent, folderCaptor.getValue().getParentFolder());
        assertEquals(actorUser, folderCaptor.getValue().getOwnerUser());
        assertEquals(actorUser, folderCaptor.getValue().getCreatedByUser());
    }

    @Test
    void createFolderRejectsDuplicateActiveSiblingName() {
        CreateFolderRequest request = new CreateFolderRequest();
        request.setName("Events");
        when(userRepository.findById(actorUserId)).thenReturn(Optional.of(actorUser));
        when(folderRepository.existsByNameIgnoreCaseAndOwnerUserAndParentFolderIsNullAndDeletedAtIsNull(
                "Events",
                actorUser)).thenReturn(true);

        assertThrows(ConflictException.class, () -> folderService.createFolder(request, actorUserId));
        verify(folderRepository, never()).save(any(FolderEntity.class));
    }

    @Test
    void differentUsersCanCreateSameRootFolderName() {
        CreateFolderRequest request = new CreateFolderRequest();
        request.setName("Events");
        when(userRepository.findById(actorUserId)).thenReturn(Optional.of(actorUser));
        when(folderRepository.existsByNameIgnoreCaseAndOwnerUserAndParentFolderIsNullAndDeletedAtIsNull(
                "Events",
                actorUser)).thenReturn(false);
        when(folderRepository.save(any(FolderEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        folderService.createFolder(request, actorUserId);

        verify(folderRepository).save(any(FolderEntity.class));
    }

    @Test
    void createChildFolderRejectsDuplicateNameUnderSameParentRegardlessOfOwner() {
        UUID parentId = UUID.randomUUID();
        FolderEntity parent = FolderEntity.builder().id(parentId).ownerUser(user(UUID.randomUUID())).build();
        CreateFolderRequest request = new CreateFolderRequest();
        request.setName("Uploads");
        request.setParentFolderId(parentId);
        when(userRepository.findById(actorUserId)).thenReturn(Optional.of(actorUser));
        when(folderRepository.findByIdAndDeletedAtIsNull(parentId)).thenReturn(Optional.of(parent));
        when(folderRepository.existsByNameIgnoreCaseAndParentFolderAndDeletedAtIsNull("Uploads", parent)).thenReturn(true);

        assertThrows(ConflictException.class, () -> folderService.createFolder(request, actorUserId));
        verify(folderRepository, never()).save(any(FolderEntity.class));
    }

    @Test
    void ownerAndGuestCannotCreateSameChildFolderNameUnderSameParent() {
        UUID parentId = UUID.randomUUID();
        FolderEntity parent = FolderEntity.builder().id(parentId).ownerUser(actorUser).build();
        CreateFolderRequest request = new CreateFolderRequest();
        request.setName("Shared");
        request.setParentFolderId(parentId);
        when(userRepository.findById(actorUserId)).thenReturn(Optional.of(actorUser));
        when(folderRepository.findByIdAndDeletedAtIsNull(parentId)).thenReturn(Optional.of(parent));
        when(folderRepository.existsByNameIgnoreCaseAndParentFolderAndDeletedAtIsNull("Shared", parent)).thenReturn(true);

        assertThrows(ConflictException.class, () -> folderService.createFolder(request, actorUserId));
        verify(folderRepository, never()).save(any(FolderEntity.class));
    }

    @Test
    void sameChildFolderNameUnderDifferentParentsIsAllowed() {
        UUID parentId = UUID.randomUUID();
        FolderEntity parent = FolderEntity.builder().id(parentId).ownerUser(user(UUID.randomUUID())).build();
        CreateFolderRequest request = new CreateFolderRequest();
        request.setName("Uploads");
        request.setParentFolderId(parentId);
        when(userRepository.findById(actorUserId)).thenReturn(Optional.of(actorUser));
        when(folderRepository.findByIdAndDeletedAtIsNull(parentId)).thenReturn(Optional.of(parent));
        when(folderRepository.existsByNameIgnoreCaseAndParentFolderAndDeletedAtIsNull("Uploads", parent)).thenReturn(false);
        when(folderRepository.save(any(FolderEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(folderClosureRepository.findByDescendantFolderOrderByDepthAsc(parent))
                .thenReturn(List.of(parentClosureRow(parent)));

        folderService.createFolder(request, actorUserId);

        verify(folderRepository).save(any(FolderEntity.class));
    }

    @Test
    void createFolderRejectsInvalidName() {
        CreateFolderRequest request = new CreateFolderRequest();
        request.setName("bad/name");

        assertThrows(IllegalArgumentException.class, () -> folderService.createFolder(request, actorUserId));
    }

    @Test
    void renameFolderRejectsDuplicateSiblingName() {
        UUID folderId = UUID.randomUUID();
        FolderEntity folder = FolderEntity.builder().id(folderId).name("Old").ownerUser(actorUser).build();
        UpdateFolderRequest request = new UpdateFolderRequest();
        request.setName("New");
        when(folderRepository.findByIdAndDeletedAtIsNull(folderId)).thenReturn(Optional.of(folder));
        when(folderRepository.existsByNameIgnoreCaseAndOwnerUserAndParentFolderIsNullAndDeletedAtIsNull(
                "New",
                actorUser)).thenReturn(true);

        assertThrows(ConflictException.class, () -> folderService.renameFolder(folderId, request, actorUserId));

        verify(accessControlService).assertCanAccessFolder(actorUserId, folderId, Permission.FOLDER_RENAME);
        verify(folderRepository, never()).save(any(FolderEntity.class));
    }

    @Test
    void deleteFolderSoftDeletesEmptyFolder() {
        UUID folderId = UUID.randomUUID();
        FolderEntity folder = FolderEntity.builder().id(folderId).name("Empty").ownerUser(actorUser).build();
        when(folderRepository.findByIdAndDeletedAtIsNull(folderId)).thenReturn(Optional.of(folder));

        folderService.deleteFolder(folderId, actorUserId);

        verify(accessControlService).assertCanAccessFolder(actorUserId, folderId, Permission.FOLDER_DELETE);
        ArgumentCaptor<FolderEntity> folderCaptor = ArgumentCaptor.forClass(FolderEntity.class);
        verify(folderRepository).save(folderCaptor.capture());
        org.junit.jupiter.api.Assertions.assertNotNull(folderCaptor.getValue().getDeletedAt());
    }

    @Test
    void deleteFolderRejectsNonEmptyFolder() {
        UUID folderId = UUID.randomUUID();
        FolderEntity folder = FolderEntity.builder().id(folderId).name("Parent").ownerUser(actorUser).build();
        when(folderRepository.findByIdAndDeletedAtIsNull(folderId)).thenReturn(Optional.of(folder));
        when(folderRepository.existsByParentFolderAndDeletedAtIsNull(folder)).thenReturn(true);

        assertThrows(ConflictException.class, () -> folderService.deleteFolder(folderId, actorUserId));
        verify(folderRepository, never()).save(any(FolderEntity.class));
    }

    @Test
    void listRootFoldersUsesAuthenticatedUserAndOptionalTag() {
        UUID tagId = UUID.randomUUID();
        FolderEntity folder = FolderEntity.builder()
                .id(UUID.randomUUID())
                .name("Tagged")
                .ownerUser(actorUser)
                .createdByUser(actorUser)
                .build();
        when(userRepository.findById(actorUserId)).thenReturn(Optional.of(actorUser));
        when(folderRepository.findVisibleTaggedRootFolders(actorUserId, tagId)).thenReturn(List.of(folder));

        var response = folderService.listRootFolders(tagId, actorUserId);

        verify(tagService).assertCanUseTagForFolderListing(tagId, actorUserId, null);
        assertEquals(1, response.size());
        assertEquals("Tagged", response.getFirst().getName());
    }

    @Test
    void listChildFoldersEnforcesViewPermissionAndUsesTagBoundary() {
        UUID parentId = UUID.randomUUID();
        UUID tagId = UUID.randomUUID();
        FolderEntity parent = FolderEntity.builder().id(parentId).ownerUser(actorUser).build();
        FolderEntity child = FolderEntity.builder()
                .id(UUID.randomUUID())
                .name("Child")
                .parentFolder(parent)
                .ownerUser(actorUser)
                .createdByUser(actorUser)
                .build();
        when(folderRepository.findByIdAndDeletedAtIsNull(parentId)).thenReturn(Optional.of(parent));
        when(folderRepository.findTaggedChildFolders(parent, tagId)).thenReturn(List.of(child));

        var response = folderService.listChildFolders(parentId, tagId, actorUserId);

        verify(accessControlService).assertCanAccessFolder(actorUserId, parentId, Permission.FOLDER_VIEW);
        verify(tagService).assertCanUseTagForFolderListing(tagId, actorUserId, parentId);
        assertEquals(1, response.getFolders().size());
    }

    private User user(UUID id) {
        return User.builder().id(id).email(id + "@example.com").build();
    }

    private FolderClosureEntity parentClosureRow(FolderEntity parent) {
        return FolderClosureEntity.builder()
                .id(new FolderClosureId(parent.getId(), parent.getId()))
                .ancestorFolder(parent)
                .descendantFolder(parent)
                .depth(0)
                .build();
    }
}

package com.filemanager.api.service;

import com.filemanager.api.auth.AccessControlService;
import com.filemanager.api.auth.Permission;
import com.filemanager.api.dto.CreateFolderRequest;
import com.filemanager.api.dto.UpdateFolderRequest;
import com.filemanager.api.entity.FolderEntity;
import com.filemanager.api.entity.Organization;
import com.filemanager.api.entity.User;
import com.filemanager.api.exception.ConflictException;
import com.filemanager.api.mapper.FolderResponseMapper;
import com.filemanager.api.repository.FileRepository;
import com.filemanager.api.repository.FolderRepository;
import com.filemanager.api.repository.OrganizationRepository;
import com.filemanager.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
    private FileRepository fileRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private AccessControlService accessControlService;

    private FolderService folderService;
    private UUID actorUserId;
    private User actorUser;

    @BeforeEach
    void setUp() {
        actorUserId = UUID.randomUUID();
        actorUser = User.builder().id(actorUserId).email("actor@example.com").build();
        folderService = new FolderService(
                folderRepository,
                fileRepository,
                userRepository,
                organizationRepository,
                accessControlService,
                new FolderResponseMapper());
    }

    @Test
    void createRootUserOwnedFolderSetsOwnerAndCreatedBy() {
        CreateFolderRequest request = new CreateFolderRequest();
        request.setName(" Wedding Guests ");
        request.setOwnerUserId(actorUserId);

        when(userRepository.findById(actorUserId)).thenReturn(Optional.of(actorUser));
        when(folderRepository.save(any(FolderEntity.class))).thenAnswer(invocation -> {
            FolderEntity folder = invocation.getArgument(0);
            folder.setId(UUID.randomUUID());
            return folder;
        });

        var response = folderService.createFolder(request, actorUserId);

        verify(accessControlService).assertCanCreateFolderInContext(actorUserId, actorUserId, null);
        ArgumentCaptor<FolderEntity> folderCaptor = ArgumentCaptor.forClass(FolderEntity.class);
        verify(folderRepository).save(folderCaptor.capture());
        assertEquals("Wedding Guests", folderCaptor.getValue().getName());
        assertEquals(actorUser, folderCaptor.getValue().getOwnerUser());
        assertEquals(actorUser, folderCaptor.getValue().getCreatedByUser());
        assertEquals("Wedding Guests", response.getName());
    }

    @Test
    void createRootOrganizationOwnedFolderRequiresOrganizationPermission() {
        UUID organizationId = UUID.randomUUID();
        Organization organization = Organization.builder().id(organizationId).name("Org").build();
        CreateFolderRequest request = new CreateFolderRequest();
        request.setName("Events");
        request.setOwnerOrganizationId(organizationId);

        when(userRepository.findById(actorUserId)).thenReturn(Optional.of(actorUser));
        when(organizationRepository.findById(organizationId)).thenReturn(Optional.of(organization));
        when(folderRepository.save(any(FolderEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        folderService.createFolder(request, actorUserId);

        verify(accessControlService).assertCanCreateFolderInContext(actorUserId, null, organizationId);
        verify(folderRepository).save(any(FolderEntity.class));
    }

    @Test
    void createChildFolderInheritsParentOwner() {
        UUID parentId = UUID.randomUUID();
        FolderEntity parent = FolderEntity.builder()
                .id(parentId)
                .ownerUser(actorUser)
                .build();
        CreateFolderRequest request = new CreateFolderRequest();
        request.setName("Uploads");
        request.setParentFolderId(parentId);

        when(userRepository.findById(actorUserId)).thenReturn(Optional.of(actorUser));
        when(folderRepository.findByIdAndDeletedAtIsNull(parentId)).thenReturn(Optional.of(parent));
        when(folderRepository.save(any(FolderEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        folderService.createFolder(request, actorUserId);

        verify(accessControlService).assertCanAccessFolder(actorUserId, parentId, Permission.FOLDER_CREATE);
        ArgumentCaptor<FolderEntity> folderCaptor = ArgumentCaptor.forClass(FolderEntity.class);
        verify(folderRepository).save(folderCaptor.capture());
        assertEquals(parent, folderCaptor.getValue().getParentFolder());
        assertEquals(actorUser, folderCaptor.getValue().getOwnerUser());
    }

    @Test
    void createChildFolderRejectsRequestedOwnerDifferentFromParent() {
        UUID parentId = UUID.randomUUID();
        FolderEntity parent = FolderEntity.builder()
                .id(parentId)
                .ownerUser(actorUser)
                .build();
        CreateFolderRequest request = new CreateFolderRequest();
        request.setName("Uploads");
        request.setParentFolderId(parentId);
        request.setOwnerUserId(UUID.randomUUID());

        when(userRepository.findById(actorUserId)).thenReturn(Optional.of(actorUser));
        when(folderRepository.findByIdAndDeletedAtIsNull(parentId)).thenReturn(Optional.of(parent));

        assertThrows(IllegalArgumentException.class, () -> folderService.createFolder(request, actorUserId));

        verify(folderRepository, never()).save(any(FolderEntity.class));
    }

    @Test
    void createFolderRejectsDuplicateActiveSiblingName() {
        CreateFolderRequest request = new CreateFolderRequest();
        request.setName("Events");
        request.setOwnerUserId(actorUserId);

        when(userRepository.findById(actorUserId)).thenReturn(Optional.of(actorUser));
        when(folderRepository.existsByNameIgnoreCaseAndOwnerUserAndParentFolderIsNullAndDeletedAtIsNull(
                "Events",
                actorUser)).thenReturn(true);

        assertThrows(ConflictException.class, () -> folderService.createFolder(request, actorUserId));

        verify(folderRepository, never()).save(any(FolderEntity.class));
    }

    @Test
    void createFolderRejectsInvalidName() {
        CreateFolderRequest request = new CreateFolderRequest();
        request.setName("bad/name");
        request.setOwnerUserId(actorUserId);

        assertThrows(IllegalArgumentException.class, () -> folderService.createFolder(request, actorUserId));
    }

    @Test
    void renameFolderRejectsDuplicateSiblingName() {
        UUID folderId = UUID.randomUUID();
        FolderEntity folder = FolderEntity.builder()
                .id(folderId)
                .name("Old")
                .ownerUser(actorUser)
                .build();
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
        FolderEntity folder = FolderEntity.builder()
                .id(folderId)
                .name("Empty")
                .ownerUser(actorUser)
                .build();
        when(folderRepository.findByIdAndDeletedAtIsNull(folderId)).thenReturn(Optional.of(folder));

        folderService.deleteFolder(folderId, actorUserId);

        verify(accessControlService).assertCanAccessFolder(actorUserId, folderId, Permission.FOLDER_DELETE);
        ArgumentCaptor<FolderEntity> folderCaptor = ArgumentCaptor.forClass(FolderEntity.class);
        verify(folderRepository).save(folderCaptor.capture());
        assertEquals(folder, folderCaptor.getValue());
        org.junit.jupiter.api.Assertions.assertNotNull(folderCaptor.getValue().getDeletedAt());
    }

    @Test
    void deleteFolderRejectsNonEmptyFolder() {
        UUID folderId = UUID.randomUUID();
        FolderEntity folder = FolderEntity.builder()
                .id(folderId)
                .name("Parent")
                .ownerUser(actorUser)
                .build();
        when(folderRepository.findByIdAndDeletedAtIsNull(folderId)).thenReturn(Optional.of(folder));
        when(folderRepository.existsByParentFolderAndDeletedAtIsNull(folder)).thenReturn(true);

        assertThrows(ConflictException.class, () -> folderService.deleteFolder(folderId, actorUserId));

        verify(folderRepository, never()).save(any(FolderEntity.class));
    }

    @Test
    void listChildFoldersEnforcesViewPermissionAndExcludesDeletedThroughRepository() {
        UUID parentId = UUID.randomUUID();
        FolderEntity parent = FolderEntity.builder()
                .id(parentId)
                .ownerUser(actorUser)
                .build();
        FolderEntity child = FolderEntity.builder()
                .id(UUID.randomUUID())
                .name("Child")
                .parentFolder(parent)
                .ownerUser(actorUser)
                .createdByUser(actorUser)
                .build();

        when(folderRepository.findByIdAndDeletedAtIsNull(parentId)).thenReturn(Optional.of(parent));
        when(folderRepository.findByParentFolderAndDeletedAtIsNullOrderByNameAsc(parent))
                .thenReturn(List.of(child));

        var response = folderService.listChildFolders(parentId, actorUserId);

        verify(accessControlService).assertCanAccessFolder(actorUserId, parentId, Permission.FOLDER_VIEW);
        assertEquals(1, response.getFolders().size());
        assertEquals("Child", response.getFolders().getFirst().getName());
    }
}

package com.filemanager.api.service;

import com.filemanager.api.auth.AccessControlService;
import com.filemanager.api.auth.Permission;
import com.filemanager.api.dto.CreateTagRequest;
import com.filemanager.api.entity.FileEntity;
import com.filemanager.api.entity.FileTagEntity;
import com.filemanager.api.entity.FileTagId;
import com.filemanager.api.entity.FolderEntity;
import com.filemanager.api.entity.FolderTagEntity;
import com.filemanager.api.entity.FolderTagId;
import com.filemanager.api.entity.Organization;
import com.filemanager.api.entity.TagEntity;
import com.filemanager.api.entity.TagScopeType;
import com.filemanager.api.entity.User;
import com.filemanager.api.exception.AccessDeniedException;
import com.filemanager.api.exception.ResourceNotFoundException;
import com.filemanager.api.mapper.TagResponseMapper;
import com.filemanager.api.repository.FileRepository;
import com.filemanager.api.repository.FileTagRepository;
import com.filemanager.api.repository.FolderRepository;
import com.filemanager.api.repository.FolderTagRepository;
import com.filemanager.api.repository.OrganizationRepository;
import com.filemanager.api.repository.TagRepository;
import com.filemanager.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

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
class TagServiceTest {
    @Mock
    private TagRepository tagRepository;
    @Mock
    private FileTagRepository fileTagRepository;
    @Mock
    private FolderTagRepository folderTagRepository;
    @Mock
    private FileRepository fileRepository;
    @Mock
    private FolderRepository folderRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private AccessControlService accessControlService;
    @Mock
    private TransactionTemplate transactionTemplate;

    private TagService tagService;
    private UUID actorUserId;
    private User actorUser;

    @BeforeEach
    void setUp() {
        actorUserId = UUID.randomUUID();
        actorUser = User.builder().id(actorUserId).email("actor@example.com").build();
        tagService = new TagService(
                tagRepository,
                fileTagRepository,
                folderTagRepository,
                fileRepository,
                folderRepository,
                userRepository,
                organizationRepository,
                accessControlService,
                new TagResponseMapper(),
                transactionTemplate);
        org.mockito.Mockito.lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
    }

    @Test
    void createOwnerUserTagNormalizesDisplayAndName() {
        CreateTagRequest request = ownerUserRequest("  Cat   Photos  ", actorUserId);
        when(userRepository.findById(actorUserId)).thenReturn(Optional.of(actorUser));
        when(tagRepository.saveAndFlush(any(TagEntity.class))).thenAnswer(invocation -> {
            TagEntity tag = invocation.getArgument(0);
            tag.setId(UUID.randomUUID());
            return tag;
        });

        var response = tagService.createOrGetTag(request, actorUserId);

        verify(accessControlService).assertCanUploadToContext(actorUserId, actorUserId, null);
        ArgumentCaptor<TagEntity> tagCaptor = ArgumentCaptor.forClass(TagEntity.class);
        verify(tagRepository).saveAndFlush(tagCaptor.capture());
        assertEquals("Cat Photos", tagCaptor.getValue().getDisplayName());
        assertEquals("cat photos", tagCaptor.getValue().getNormalizedName());
        assertEquals("cat photos", response.getNormalizedName());
    }

    @Test
    void createOrGetReturnsExistingForSameNormalizedOwnerScope() {
        CreateTagRequest request = ownerUserRequest(" CATS ", actorUserId);
        TagEntity existing = ownerUserTag("cats", actorUser);
        when(tagRepository.findByOwnerUserIdAndScopeTypeAndNormalizedNameAndDeletedAtIsNull(
                actorUserId,
                TagScopeType.OWNER,
                "cats")).thenReturn(Optional.of(existing));

        var response = tagService.createOrGetTag(request, actorUserId);

        assertEquals(existing.getId(), response.getId());
        verify(tagRepository, never()).saveAndFlush(any(TagEntity.class));
    }

    @Test
    void createOwnerOrganizationTagUsesOrganizationOwner() {
        UUID organizationId = UUID.randomUUID();
        Organization organization = Organization.builder().id(organizationId).name("Org").build();
        CreateTagRequest request = new CreateTagRequest();
        request.setName("Receipt");
        request.setScopeType(TagScopeType.OWNER);
        request.setOwnerOrganizationId(organizationId);
        when(userRepository.findById(actorUserId)).thenReturn(Optional.of(actorUser));
        when(organizationRepository.findById(organizationId)).thenReturn(Optional.of(organization));
        when(tagRepository.saveAndFlush(any(TagEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        tagService.createOrGetTag(request, actorUserId);

        verify(accessControlService).assertCanUploadToContext(actorUserId, null, organizationId);
        ArgumentCaptor<TagEntity> tagCaptor = ArgumentCaptor.forClass(TagEntity.class);
        verify(tagRepository).saveAndFlush(tagCaptor.capture());
        assertEquals(organization, tagCaptor.getValue().getOwnerOrganization());
    }

    @Test
    void createFolderScopedTagDerivesOwnerFromFolderAndKeepsCreatedByActor() {
        UUID folderOwnerId = UUID.randomUUID();
        User folderOwner = User.builder().id(folderOwnerId).email("owner@example.com").build();
        UUID folderId = UUID.randomUUID();
        FolderEntity folder = FolderEntity.builder().id(folderId).ownerUser(folderOwner).build();
        CreateTagRequest request = new CreateTagRequest();
        request.setName("Ceremony");
        request.setScopeType(TagScopeType.FOLDER);
        request.setScopeFolderId(folderId);
        when(userRepository.findById(actorUserId)).thenReturn(Optional.of(actorUser));
        when(folderRepository.findByIdAndDeletedAtIsNull(folderId)).thenReturn(Optional.of(folder));
        when(tagRepository.saveAndFlush(any(TagEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        tagService.createOrGetTag(request, actorUserId);

        verify(accessControlService).assertCanAccessFolder(actorUserId, folderId, Permission.FOLDER_UPLOAD_FILE);
        ArgumentCaptor<TagEntity> tagCaptor = ArgumentCaptor.forClass(TagEntity.class);
        verify(tagRepository).saveAndFlush(tagCaptor.capture());
        assertEquals(folderOwner, tagCaptor.getValue().getOwnerUser());
        assertEquals(actorUser, tagCaptor.getValue().getCreatedByUser());
        assertEquals(folder, tagCaptor.getValue().getScopeFolder());
    }

    @Test
    void sameNameAllowedInDifferentFolderScopes() {
        UUID firstFolderId = UUID.randomUUID();
        UUID secondFolderId = UUID.randomUUID();
        when(folderRepository.findByIdAndDeletedAtIsNull(firstFolderId))
                .thenReturn(Optional.of(FolderEntity.builder().id(firstFolderId).ownerUser(actorUser).build()));
        when(folderRepository.findByIdAndDeletedAtIsNull(secondFolderId))
                .thenReturn(Optional.of(FolderEntity.builder().id(secondFolderId).ownerUser(actorUser).build()));
        tagService.listTags(null, null, firstFolderId, "cats", 10, actorUserId);
        tagService.listTags(null, null, secondFolderId, "cats", 10, actorUserId);

        verify(tagRepository).listFolderTags(org.mockito.ArgumentMatchers.eq(firstFolderId),
                org.mockito.ArgumentMatchers.eq("cats"), org.mockito.ArgumentMatchers.any(Pageable.class));
        verify(tagRepository).listFolderTags(org.mockito.ArgumentMatchers.eq(secondFolderId),
                org.mockito.ArgumentMatchers.eq("cats"), org.mockito.ArgumentMatchers.any(Pageable.class));
    }

    @Test
    void createRejectsBlankControlAndOverlongNames() {
        assertThrows(IllegalArgumentException.class, () -> tagService.createOrGetTag(ownerUserRequest("   ", actorUserId), actorUserId));
        assertThrows(IllegalArgumentException.class, () -> tagService.createOrGetTag(ownerUserRequest("bad\nname", actorUserId), actorUserId));
        assertThrows(IllegalArgumentException.class, () -> tagService.createOrGetTag(ownerUserRequest("a".repeat(101), actorUserId), actorUserId));
    }

    @Test
    void listOwnerTagsNormalizesQueryAndEnforcesLimit() {
        when(userRepository.findById(actorUserId)).thenReturn(Optional.of(actorUser));

        tagService.listTags(actorUserId, null, null, " Cat ", 5, actorUserId);

        verify(accessControlService).assertCanViewContext(actorUserId, actorUserId, null);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(tagRepository).listOwnerUserTags(org.mockito.ArgumentMatchers.eq(actorUserId),
                org.mockito.ArgumentMatchers.eq("cat"), pageableCaptor.capture());
        assertEquals(5, pageableCaptor.getValue().getPageSize());
    }

    @Test
    void unauthorizedOwnerScopeCreateIsDenied() {
        CreateTagRequest request = ownerUserRequest("cats", UUID.randomUUID());
        org.mockito.Mockito.doThrow(new AccessDeniedException("denied"))
                .when(accessControlService)
                .assertCanUploadToContext(actorUserId, request.getOwnerUserId(), null);

        assertThrows(AccessDeniedException.class, () -> tagService.createOrGetTag(request, actorUserId));
        verify(tagRepository, never()).saveAndFlush(any(TagEntity.class));
    }

    @Test
    void applyTagToFileIsIdempotent() {
        UUID fileId = UUID.randomUUID();
        TagEntity tag = ownerUserTag("cats", actorUser);
        FileEntity file = FileEntity.builder().id(fileId).ownerUser(actorUser).build();
        when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.of(file));
        when(tagRepository.findByIdAndDeletedAtIsNull(tag.getId())).thenReturn(Optional.of(tag));
        when(userRepository.findById(actorUserId)).thenReturn(Optional.of(actorUser));
        when(fileTagRepository.existsById(new FileTagId(fileId, tag.getId()))).thenReturn(true);
        when(fileTagRepository.findActiveTagsByFileId(fileId)).thenReturn(List.of(tag));

        var tags = tagService.applyTagToFile(fileId, tag.getId(), actorUserId);

        verify(accessControlService).assertCanAccessFile(actorUserId, fileId, Permission.FILE_MODIFY);
        verify(fileTagRepository, never()).save(any(FileTagEntity.class));
        assertEquals(1, tags.size());
    }

    @Test
    void applyTagToFileRejectsCrossOwnerTag() {
        UUID fileId = UUID.randomUUID();
        User fileOwner = User.builder().id(actorUserId).email("actor@example.com").build();
        User tagOwner = User.builder().id(UUID.randomUUID()).email("other@example.com").build();
        TagEntity tag = ownerUserTag("cats", tagOwner);
        FileEntity file = FileEntity.builder().id(fileId).ownerUser(fileOwner).build();
        when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.of(file));
        when(tagRepository.findByIdAndDeletedAtIsNull(tag.getId())).thenReturn(Optional.of(tag));
        when(userRepository.findById(actorUserId)).thenReturn(Optional.of(actorUser));

        assertThrows(AccessDeniedException.class, () -> tagService.applyTagToFile(fileId, tag.getId(), actorUserId));
        verify(fileTagRepository, never()).save(any(FileTagEntity.class));
    }

    @Test
    void applyFolderScopedTagToFileOutsideScopeIsDenied() {
        UUID scopedFolderId = UUID.randomUUID();
        UUID otherFolderId = UUID.randomUUID();
        FolderEntity scopedFolder = FolderEntity.builder().id(scopedFolderId).ownerUser(actorUser).build();
        FolderEntity otherFolder = FolderEntity.builder().id(otherFolderId).ownerUser(actorUser).build();
        TagEntity tag = folderTag("ceremony", scopedFolder, actorUser);
        UUID fileId = UUID.randomUUID();
        FileEntity file = FileEntity.builder().id(fileId).ownerUser(actorUser).folder(otherFolder).build();
        when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.of(file));
        when(tagRepository.findByIdAndDeletedAtIsNull(tag.getId())).thenReturn(Optional.of(tag));
        when(userRepository.findById(actorUserId)).thenReturn(Optional.of(actorUser));

        assertThrows(AccessDeniedException.class, () -> tagService.applyTagToFile(fileId, tag.getId(), actorUserId));
    }

    @Test
    void inaccessibleFileCannotBeTagged() {
        UUID fileId = UUID.randomUUID();
        UUID tagId = UUID.randomUUID();
        org.mockito.Mockito.doThrow(new AccessDeniedException("denied"))
                .when(accessControlService)
                .assertCanAccessFile(actorUserId, fileId, Permission.FILE_MODIFY);

        assertThrows(AccessDeniedException.class, () -> tagService.applyTagToFile(fileId, tagId, actorUserId));
        verify(tagRepository, never()).findByIdAndDeletedAtIsNull(tagId);
    }

    @Test
    void removedOrDeletedTagCannotBeApplied() {
        UUID fileId = UUID.randomUUID();
        UUID tagId = UUID.randomUUID();
        FileEntity file = FileEntity.builder().id(fileId).ownerUser(actorUser).build();
        when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.of(file));
        when(tagRepository.findByIdAndDeletedAtIsNull(tagId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> tagService.applyTagToFile(fileId, tagId, actorUserId));
    }

    @Test
    void removeTagFromFileDeletesOnlyAssignment() {
        UUID fileId = UUID.randomUUID();
        UUID tagId = UUID.randomUUID();
        FileTagId assignmentId = new FileTagId(fileId, tagId);
        when(fileTagRepository.existsById(assignmentId)).thenReturn(true);
        when(fileTagRepository.findActiveTagsByFileId(fileId)).thenReturn(List.of());

        tagService.removeTagFromFile(fileId, tagId, actorUserId);

        verify(fileTagRepository).deleteById(assignmentId);
        verify(tagRepository, never()).deleteById(tagId);
    }

    @Test
    void applyTagToFolderIsIdempotent() {
        UUID folderId = UUID.randomUUID();
        TagEntity tag = ownerUserTag("family", actorUser);
        FolderEntity folder = FolderEntity.builder().id(folderId).ownerUser(actorUser).build();
        when(folderRepository.findByIdAndDeletedAtIsNull(folderId)).thenReturn(Optional.of(folder));
        when(tagRepository.findByIdAndDeletedAtIsNull(tag.getId())).thenReturn(Optional.of(tag));
        when(userRepository.findById(actorUserId)).thenReturn(Optional.of(actorUser));
        when(folderTagRepository.existsById(new FolderTagId(folderId, tag.getId()))).thenReturn(true);
        when(folderTagRepository.findActiveTagsByFolderId(folderId)).thenReturn(List.of(tag));

        var tags = tagService.applyTagToFolder(folderId, tag.getId(), actorUserId);

        verify(accessControlService).assertCanAccessFolder(actorUserId, folderId, Permission.FOLDER_RENAME);
        verify(folderTagRepository, never()).save(any(FolderTagEntity.class));
        assertEquals(1, tags.size());
    }

    @Test
    void applyFolderScopedTagToUnrelatedFolderIsDenied() {
        FolderEntity scopedFolder = FolderEntity.builder().id(UUID.randomUUID()).ownerUser(actorUser).build();
        FolderEntity unrelatedFolder = FolderEntity.builder().id(UUID.randomUUID()).ownerUser(actorUser).build();
        TagEntity tag = folderTag("guests", scopedFolder, actorUser);
        when(folderRepository.findByIdAndDeletedAtIsNull(unrelatedFolder.getId())).thenReturn(Optional.of(unrelatedFolder));
        when(tagRepository.findByIdAndDeletedAtIsNull(tag.getId())).thenReturn(Optional.of(tag));
        when(userRepository.findById(actorUserId)).thenReturn(Optional.of(actorUser));

        assertThrows(AccessDeniedException.class,
                () -> tagService.applyTagToFolder(unrelatedFolder.getId(), tag.getId(), actorUserId));
        verify(folderTagRepository, never()).save(any(FolderTagEntity.class));
    }

    @Test
    void inaccessibleFolderCannotBeTagged() {
        UUID folderId = UUID.randomUUID();
        UUID tagId = UUID.randomUUID();
        org.mockito.Mockito.doThrow(new AccessDeniedException("denied"))
                .when(accessControlService)
                .assertCanAccessFolder(actorUserId, folderId, Permission.FOLDER_RENAME);

        assertThrows(AccessDeniedException.class, () -> tagService.applyTagToFolder(folderId, tagId, actorUserId));
        verify(tagRepository, never()).findByIdAndDeletedAtIsNull(tagId);
    }

    @Test
    void fileSearchRejectsUnauthorizedTagScope() {
        FolderEntity scopeFolder = FolderEntity.builder().id(UUID.randomUUID()).ownerUser(actorUser).build();
        TagEntity tag = folderTag("wedding", scopeFolder, actorUser);
        UUID tagId = tag.getId();
        when(tagRepository.findByIdAndDeletedAtIsNull(tagId)).thenReturn(Optional.of(tag));
        org.mockito.Mockito.doThrow(new AccessDeniedException("denied"))
                .when(accessControlService)
                .assertCanAccessFolder(actorUserId, scopeFolder.getId(), Permission.FOLDER_VIEW);

        assertThrows(AccessDeniedException.class,
                () -> tagService.assertCanUseTagForFileSearch(tagId, actorUserId, actorUserId, null, null));
    }

    @Test
    void fileSearchAllowsFolderScopedTagWithMatchingFolderContext() {
        FolderEntity scopeFolder = FolderEntity.builder().id(UUID.randomUUID()).ownerUser(actorUser).build();
        TagEntity tag = folderTag("ceremony", scopeFolder, actorUser);
        when(tagRepository.findByIdAndDeletedAtIsNull(tag.getId())).thenReturn(Optional.of(tag));

        tagService.assertCanUseTagForFileSearch(
                tag.getId(),
                actorUserId,
                actorUserId,
                null,
                scopeFolder.getId());
    }

    @Test
    void fileSearchRejectsFolderScopedTagWithoutFolderContext() {
        FolderEntity scopeFolder = FolderEntity.builder().id(UUID.randomUUID()).ownerUser(actorUser).build();
        TagEntity tag = folderTag("ceremony", scopeFolder, actorUser);
        when(tagRepository.findByIdAndDeletedAtIsNull(tag.getId())).thenReturn(Optional.of(tag));

        assertThrows(AccessDeniedException.class, () -> tagService.assertCanUseTagForFileSearch(
                tag.getId(),
                actorUserId,
                actorUserId,
                null,
                null));
    }

    @Test
    void fileSearchRejectsFolderScopedTagWithDifferentFolderContext() {
        FolderEntity scopeFolder = FolderEntity.builder().id(UUID.randomUUID()).ownerUser(actorUser).build();
        TagEntity tag = folderTag("ceremony", scopeFolder, actorUser);
        when(tagRepository.findByIdAndDeletedAtIsNull(tag.getId())).thenReturn(Optional.of(tag));

        assertThrows(AccessDeniedException.class, () -> tagService.assertCanUseTagForFileSearch(
                tag.getId(),
                actorUserId,
                actorUserId,
                null,
                UUID.randomUUID()));
    }

    @Test
    void fileSearchAllowsOwnerScopedTagInOwnerContext() {
        TagEntity tag = ownerUserTag("receipt", actorUser);
        when(tagRepository.findByIdAndDeletedAtIsNull(tag.getId())).thenReturn(Optional.of(tag));

        tagService.assertCanUseTagForFileSearch(tag.getId(), actorUserId, actorUserId, null, null);
    }

    @Test
    void fileSearchAllowsOwnerScopedTagInFolderContext() {
        TagEntity tag = ownerUserTag("receipt", actorUser);
        when(tagRepository.findByIdAndDeletedAtIsNull(tag.getId())).thenReturn(Optional.of(tag));

        tagService.assertCanUseTagForFileSearch(tag.getId(), actorUserId, actorUserId, null, UUID.randomUUID());
    }

    @Test
    void fileSearchRejectsDeletedTag() {
        UUID tagId = UUID.randomUUID();
        when(tagRepository.findByIdAndDeletedAtIsNull(tagId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> tagService.assertCanUseTagForFileSearch(tagId, actorUserId, actorUserId, null, null));
    }

    @Test
    void fileSearchRejectsCrossOwnerTag() {
        User otherOwner = User.builder().id(UUID.randomUUID()).email("other@example.com").build();
        TagEntity tag = ownerUserTag("receipt", otherOwner);
        when(tagRepository.findByIdAndDeletedAtIsNull(tag.getId())).thenReturn(Optional.of(tag));

        assertThrows(AccessDeniedException.class,
                () -> tagService.assertCanUseTagForFileSearch(tag.getId(), actorUserId, actorUserId, null, null));
    }

    @Test
    void rootFolderListingAllowsOwnerScopedTag() {
        TagEntity tag = ownerUserTag("archive", actorUser);
        when(tagRepository.findByIdAndDeletedAtIsNull(tag.getId())).thenReturn(Optional.of(tag));

        tagService.assertCanUseTagForFolderListing(tag.getId(), actorUserId, actorUserId, null, null);
    }

    @Test
    void rootFolderListingRejectsFolderScopedTag() {
        FolderEntity scopeFolder = FolderEntity.builder().id(UUID.randomUUID()).ownerUser(actorUser).build();
        TagEntity tag = folderTag("family", scopeFolder, actorUser);
        when(tagRepository.findByIdAndDeletedAtIsNull(tag.getId())).thenReturn(Optional.of(tag));

        assertThrows(AccessDeniedException.class,
                () -> tagService.assertCanUseTagForFolderListing(tag.getId(), actorUserId, actorUserId, null, null));
    }

    @Test
    void rootFolderListingRejectsCrossOwnerTag() {
        User otherOwner = User.builder().id(UUID.randomUUID()).email("other@example.com").build();
        TagEntity tag = ownerUserTag("archive", otherOwner);
        when(tagRepository.findByIdAndDeletedAtIsNull(tag.getId())).thenReturn(Optional.of(tag));

        assertThrows(AccessDeniedException.class,
                () -> tagService.assertCanUseTagForFolderListing(tag.getId(), actorUserId, actorUserId, null, null));
    }

    @Test
    void rootFolderListingRejectsDeletedTag() {
        UUID tagId = UUID.randomUUID();
        when(tagRepository.findByIdAndDeletedAtIsNull(tagId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> tagService.assertCanUseTagForFolderListing(tagId, actorUserId, actorUserId, null, null));
    }

    @Test
    void childFolderListingAllowsFolderScopedTagForSameParent() {
        FolderEntity scopeFolder = FolderEntity.builder().id(UUID.randomUUID()).ownerUser(actorUser).build();
        TagEntity tag = folderTag("guests", scopeFolder, actorUser);
        when(tagRepository.findByIdAndDeletedAtIsNull(tag.getId())).thenReturn(Optional.of(tag));

        tagService.assertCanUseTagForFolderListing(
                tag.getId(),
                actorUserId,
                actorUserId,
                null,
                scopeFolder.getId());
    }

    @Test
    void childFolderListingRejectsFolderScopedTagFromAnotherFolder() {
        FolderEntity scopeFolder = FolderEntity.builder().id(UUID.randomUUID()).ownerUser(actorUser).build();
        TagEntity tag = folderTag("guests", scopeFolder, actorUser);
        when(tagRepository.findByIdAndDeletedAtIsNull(tag.getId())).thenReturn(Optional.of(tag));

        assertThrows(AccessDeniedException.class, () -> tagService.assertCanUseTagForFolderListing(
                tag.getId(),
                actorUserId,
                actorUserId,
                null,
                UUID.randomUUID()));
    }

    @Test
    void childFolderListingAllowsOwnerScopedTag() {
        TagEntity tag = ownerUserTag("archive", actorUser);
        when(tagRepository.findByIdAndDeletedAtIsNull(tag.getId())).thenReturn(Optional.of(tag));

        tagService.assertCanUseTagForFolderListing(tag.getId(), actorUserId, actorUserId, null, UUID.randomUUID());
    }

    @Test
    void childFolderListingRejectsCrossOwnerTag() {
        User otherOwner = User.builder().id(UUID.randomUUID()).email("other@example.com").build();
        TagEntity tag = ownerUserTag("archive", otherOwner);
        when(tagRepository.findByIdAndDeletedAtIsNull(tag.getId())).thenReturn(Optional.of(tag));

        assertThrows(AccessDeniedException.class,
                () -> tagService.assertCanUseTagForFolderListing(tag.getId(), actorUserId, actorUserId, null, UUID.randomUUID()));
    }

    @Test
    void childFolderListingRejectsDeletedTag() {
        UUID tagId = UUID.randomUUID();
        when(tagRepository.findByIdAndDeletedAtIsNull(tagId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> tagService.assertCanUseTagForFolderListing(tagId, actorUserId, actorUserId, null, UUID.randomUUID()));
    }

    private CreateTagRequest ownerUserRequest(String name, UUID ownerUserId) {
        CreateTagRequest request = new CreateTagRequest();
        request.setName(name);
        request.setScopeType(TagScopeType.OWNER);
        request.setOwnerUserId(ownerUserId);
        return request;
    }

    private TagEntity ownerUserTag(String normalizedName, User ownerUser) {
        return TagEntity.builder()
                .id(UUID.randomUUID())
                .displayName(normalizedName)
                .normalizedName(normalizedName)
                .scopeType(TagScopeType.OWNER)
                .ownerUser(ownerUser)
                .createdByUser(actorUser)
                .build();
    }

    private TagEntity folderTag(String normalizedName, FolderEntity scopeFolder, User ownerUser) {
        return TagEntity.builder()
                .id(UUID.randomUUID())
                .displayName(normalizedName)
                .normalizedName(normalizedName)
                .scopeType(TagScopeType.FOLDER)
                .scopeFolder(scopeFolder)
                .ownerUser(ownerUser)
                .createdByUser(actorUser)
                .build();
    }
}

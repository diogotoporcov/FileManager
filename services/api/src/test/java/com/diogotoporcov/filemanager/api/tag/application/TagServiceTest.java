package com.diogotoporcov.filemanager.api.tag.application;

import com.diogotoporcov.filemanager.api.auth.application.AccessControlService;
import com.diogotoporcov.filemanager.api.auth.domain.Permission;
import com.diogotoporcov.filemanager.api.exception.AccessDeniedException;
import com.diogotoporcov.filemanager.api.exception.ResourceNotFoundException;
import com.diogotoporcov.filemanager.api.file.domain.FileEntity;
import com.diogotoporcov.filemanager.api.file.persistence.FileRepository;
import com.diogotoporcov.filemanager.api.folder.domain.FolderEntity;
import com.diogotoporcov.filemanager.api.folder.persistence.FolderRepository;
import com.diogotoporcov.filemanager.api.identity.domain.User;
import com.diogotoporcov.filemanager.api.identity.persistence.UserRepository;
import com.diogotoporcov.filemanager.api.tag.domain.FileTagEntity;
import com.diogotoporcov.filemanager.api.tag.domain.FileTagId;
import com.diogotoporcov.filemanager.api.tag.domain.FolderTagEntity;
import com.diogotoporcov.filemanager.api.tag.domain.FolderTagId;
import com.diogotoporcov.filemanager.api.tag.domain.TagEntity;
import com.diogotoporcov.filemanager.api.tag.domain.TagScopeType;
import com.diogotoporcov.filemanager.api.tag.persistence.FileTagRepository;
import com.diogotoporcov.filemanager.api.tag.persistence.FolderTagRepository;
import com.diogotoporcov.filemanager.api.tag.persistence.TagRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionStatus;
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
                accessControlService,
                transactionTemplate);
        org.mockito.Mockito.lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);

            return callback.doInTransaction(org.mockito.Mockito.mock(TransactionStatus.class));
        });
    }

    @Test
    void createOwnerTagUsesAuthenticatedUserAsOwner() {
        CreateTagCommand request = ownerRequest("  Cat   Photos  ");
        when(userRepository.findById(actorUserId)).thenReturn(Optional.of(actorUser));
        when(tagRepository.saveAndFlush(any(TagEntity.class))).thenAnswer(invocation -> {
            TagEntity tag = invocation.getArgument(0);
            tag.setId(UUID.randomUUID());

            return tag;
        });

        var response = tagService.createOrGetTag(request, actorUserId);

        verify(accessControlService).assertCanUploadToOwner(actorUserId, actorUserId);
        ArgumentCaptor<TagEntity> tagCaptor = ArgumentCaptor.forClass(TagEntity.class);
        verify(tagRepository).saveAndFlush(tagCaptor.capture());
        assertEquals("Cat Photos", tagCaptor.getValue().getDisplayName());
        assertEquals("cat photos", tagCaptor.getValue().getNormalizedName());
        assertEquals(actorUser, tagCaptor.getValue().getOwnerUser());
        assertEquals("cat photos", response.getNormalizedName());
    }

    @Test
    void createOrGetReturnsExistingOwnerTag() {
        CreateTagCommand request = ownerRequest(" CATS ");
        TagEntity existing = ownerTag("cats", actorUser);
        when(tagRepository.findByOwnerUserIdAndScopeTypeAndNormalizedNameAndDeletedAtIsNull(
                actorUserId,
                TagScopeType.OWNER,
                "cats")).thenReturn(Optional.of(existing));

        var response = tagService.createOrGetTag(request, actorUserId);

        assertEquals(existing.getId(), response.getId());
        verify(tagRepository, never()).saveAndFlush(any(TagEntity.class));
    }

    @Test
    void createFolderScopedTagDerivesOwnerFromFolder() {
        User folderOwner = User.builder().id(UUID.randomUUID()).email("owner@example.com").build();
        UUID folderId = UUID.randomUUID();
        FolderEntity folder = FolderEntity.builder().id(folderId).ownerUser(folderOwner).build();
        CreateTagCommand request = folderRequest("Ceremony", folderId);
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
    void listOwnerTagsUsesAuthenticatedUser() {
        when(userRepository.findById(actorUserId)).thenReturn(Optional.of(actorUser));

        tagService.listTags(null, " Cat ", 5, actorUserId);

        verify(accessControlService).assertCanViewOwner(actorUserId, actorUserId);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(tagRepository).listOwnerUserTags(
                org.mockito.ArgumentMatchers.eq(actorUserId),
                org.mockito.ArgumentMatchers.eq("cat"),
                pageableCaptor.capture());
        assertEquals(5, pageableCaptor.getValue().getPageSize());
    }

    @Test
    void listFolderTagsChecksFolderAccess() {
        UUID folderId = UUID.randomUUID();
        when(folderRepository.findByIdAndDeletedAtIsNull(folderId))
                .thenReturn(Optional.of(FolderEntity.builder().id(folderId).ownerUser(actorUser).build()));

        tagService.listTags(folderId, "cats", 10, actorUserId);

        verify(accessControlService).assertCanAccessFolder(actorUserId, folderId, Permission.FOLDER_VIEW);
        verify(tagRepository).listFolderTags(
                org.mockito.ArgumentMatchers.eq(folderId),
                org.mockito.ArgumentMatchers.eq("cats"),
                org.mockito.ArgumentMatchers.any(Pageable.class));
    }

    @Test
    void invalidTagNamesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> tagService.createOrGetTag(ownerRequest("   "), actorUserId));
        assertThrows(IllegalArgumentException.class, () -> tagService.createOrGetTag(ownerRequest("bad\nname"), actorUserId));
        assertThrows(IllegalArgumentException.class, () -> tagService.createOrGetTag(ownerRequest("a".repeat(101)), actorUserId));
    }

    @Test
    void applyOwnerTagToFileCreatesAssignment() {
        UUID fileId = UUID.randomUUID();
        TagEntity tag = ownerTag("cats", actorUser);
        FileEntity file = FileEntity.builder().id(fileId).ownerUser(actorUser).build();
        when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.of(file));
        when(tagRepository.findByIdAndDeletedAtIsNull(tag.getId())).thenReturn(Optional.of(tag));
        when(userRepository.findById(actorUserId)).thenReturn(Optional.of(actorUser));
        when(fileTagRepository.existsById(new FileTagId(fileId, tag.getId()))).thenReturn(false);
        when(fileTagRepository.findActiveTagsByFileId(fileId)).thenReturn(List.of(tag));

        tagService.applyTagToFile(fileId, tag.getId(), actorUserId);

        verify(accessControlService).assertCanAccessFile(actorUserId, fileId, Permission.FILE_MODIFY);
        ArgumentCaptor<FileTagEntity> assignmentCaptor = ArgumentCaptor.forClass(FileTagEntity.class);
        verify(fileTagRepository).save(assignmentCaptor.capture());
        assertEquals(file, assignmentCaptor.getValue().getFile());
        assertEquals(tag, assignmentCaptor.getValue().getTag());
        assertEquals(actorUser, assignmentCaptor.getValue().getTaggedByUser());
    }

    @Test
    void applyOwnerTagToFileRejectsCrossOwnerTag() {
        UUID fileId = UUID.randomUUID();
        User otherOwner = User.builder().id(UUID.randomUUID()).email("other@example.com").build();
        TagEntity tag = ownerTag("cats", otherOwner);
        FileEntity file = FileEntity.builder().id(fileId).ownerUser(actorUser).build();
        when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.of(file));
        when(tagRepository.findByIdAndDeletedAtIsNull(tag.getId())).thenReturn(Optional.of(tag));
        when(userRepository.findById(actorUserId)).thenReturn(Optional.of(actorUser));

        assertThrows(AccessDeniedException.class, () -> tagService.applyTagToFile(fileId, tag.getId(), actorUserId));
        verify(fileTagRepository, never()).save(any(FileTagEntity.class));
    }

    @Test
    void folderScopedTagCanApplyToFileOwnedByDifferentUploaderInsideScope() {
        User folderOwner = User.builder().id(UUID.randomUUID()).email("owner@example.com").build();
        FolderEntity scopeFolder = FolderEntity.builder().id(UUID.randomUUID()).ownerUser(folderOwner).build();
        TagEntity tag = folderTag("ceremony", scopeFolder, folderOwner);
        UUID fileId = UUID.randomUUID();
        FileEntity file = FileEntity.builder().id(fileId).ownerUser(actorUser).folder(scopeFolder).build();
        when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.of(file));
        when(tagRepository.findByIdAndDeletedAtIsNull(tag.getId())).thenReturn(Optional.of(tag));
        when(userRepository.findById(actorUserId)).thenReturn(Optional.of(actorUser));
        when(fileTagRepository.existsById(new FileTagId(fileId, tag.getId()))).thenReturn(false);
        when(fileTagRepository.findActiveTagsByFileId(fileId)).thenReturn(List.of(tag));

        tagService.applyTagToFile(fileId, tag.getId(), actorUserId);

        verify(accessControlService).assertCanAccessFolder(actorUserId, scopeFolder.getId(), Permission.FOLDER_VIEW);
        verify(fileTagRepository).save(any(FileTagEntity.class));
    }

    @Test
    void folderScopedTagCannotApplyToFileOutsideScope() {
        FolderEntity scopeFolder = FolderEntity.builder().id(UUID.randomUUID()).ownerUser(actorUser).build();
        FolderEntity otherFolder = FolderEntity.builder().id(UUID.randomUUID()).ownerUser(actorUser).build();
        TagEntity tag = folderTag("ceremony", scopeFolder, actorUser);
        UUID fileId = UUID.randomUUID();
        FileEntity file = FileEntity.builder().id(fileId).ownerUser(actorUser).folder(otherFolder).build();
        when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.of(file));
        when(tagRepository.findByIdAndDeletedAtIsNull(tag.getId())).thenReturn(Optional.of(tag));
        when(userRepository.findById(actorUserId)).thenReturn(Optional.of(actorUser));

        assertThrows(AccessDeniedException.class, () -> tagService.applyTagToFile(fileId, tag.getId(), actorUserId));
    }

    @Test
    void applyFolderScopedTagToChildFolderCreatesAssignment() {
        FolderEntity scopeFolder = FolderEntity.builder().id(UUID.randomUUID()).ownerUser(actorUser).build();
        FolderEntity childFolder = FolderEntity.builder()
                .id(UUID.randomUUID())
                .ownerUser(actorUser)
                .parentFolder(scopeFolder)
                .build();
        TagEntity tag = folderTag("guests", scopeFolder, actorUser);
        when(folderRepository.findByIdAndDeletedAtIsNull(childFolder.getId())).thenReturn(Optional.of(childFolder));
        when(tagRepository.findByIdAndDeletedAtIsNull(tag.getId())).thenReturn(Optional.of(tag));
        when(userRepository.findById(actorUserId)).thenReturn(Optional.of(actorUser));
        when(folderTagRepository.existsById(new FolderTagId(childFolder.getId(), tag.getId()))).thenReturn(false);
        when(folderTagRepository.findActiveTagsByFolderId(childFolder.getId())).thenReturn(List.of(tag));

        tagService.applyTagToFolder(childFolder.getId(), tag.getId(), actorUserId);

        verify(accessControlService).assertCanAccessFolder(actorUserId, childFolder.getId(), Permission.FOLDER_RENAME);
        verify(folderTagRepository).save(any(FolderTagEntity.class));
    }

    @Test
    void fileSearchAllowsOwnerTagForActorAndRejectsCrossOwnerTag() {
        TagEntity allowed = ownerTag("receipt", actorUser);
        User otherOwner = User.builder().id(UUID.randomUUID()).email("other@example.com").build();
        TagEntity denied = ownerTag("other", otherOwner);
        when(tagRepository.findByIdAndDeletedAtIsNull(allowed.getId())).thenReturn(Optional.of(allowed));
        when(tagRepository.findByIdAndDeletedAtIsNull(denied.getId())).thenReturn(Optional.of(denied));

        tagService.assertCanUseTagForFileSearch(allowed.getId(), actorUserId, null);
        assertThrows(AccessDeniedException.class,
                () -> tagService.assertCanUseTagForFileSearch(denied.getId(), actorUserId, null));
    }

    @Test
    void fileSearchRequiresMatchingFolderForFolderScopedTag() {
        FolderEntity scopeFolder = FolderEntity.builder().id(UUID.randomUUID()).ownerUser(actorUser).build();
        TagEntity tag = folderTag("ceremony", scopeFolder, actorUser);
        when(tagRepository.findByIdAndDeletedAtIsNull(tag.getId())).thenReturn(Optional.of(tag));

        tagService.assertCanUseTagForFileSearch(tag.getId(), actorUserId, scopeFolder.getId());
        assertThrows(AccessDeniedException.class,
                () -> tagService.assertCanUseTagForFileSearch(tag.getId(), actorUserId, null));
        assertThrows(AccessDeniedException.class,
                () -> tagService.assertCanUseTagForFileSearch(tag.getId(), actorUserId, UUID.randomUUID()));
    }

    @Test
    void folderListingAllowsOwnerTagAndRejectsFolderTagAtRoot() {
        TagEntity ownerTag = ownerTag("archive", actorUser);
        FolderEntity scopeFolder = FolderEntity.builder().id(UUID.randomUUID()).ownerUser(actorUser).build();
        TagEntity folderTag = folderTag("guests", scopeFolder, actorUser);
        when(tagRepository.findByIdAndDeletedAtIsNull(ownerTag.getId())).thenReturn(Optional.of(ownerTag));
        when(tagRepository.findByIdAndDeletedAtIsNull(folderTag.getId())).thenReturn(Optional.of(folderTag));

        tagService.assertCanUseTagForFolderListing(ownerTag.getId(), actorUserId, null);
        assertThrows(AccessDeniedException.class,
                () -> tagService.assertCanUseTagForFolderListing(folderTag.getId(), actorUserId, null));
    }

    @Test
    void missingTagInPermissionBoundaryThrowsNotFound() {
        UUID tagId = UUID.randomUUID();
        when(tagRepository.findByIdAndDeletedAtIsNull(tagId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> tagService.assertCanUseTagForFileSearch(tagId, actorUserId, null));
    }

    private CreateTagCommand ownerRequest(String name) {
        return new CreateTagCommand(name, TagScopeType.OWNER, null);
    }

    private CreateTagCommand folderRequest(String name, UUID folderId) {
        return new CreateTagCommand(name, TagScopeType.FOLDER, folderId);
    }

    private TagEntity ownerTag(String normalizedName, User ownerUser) {
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

package com.diogotoporcov.filemanager.api.file.application;

import com.diogotoporcov.filemanager.api.auth.domain.Permission;
import com.diogotoporcov.filemanager.api.exception.AccessDeniedException;
import com.diogotoporcov.filemanager.api.file.domain.FileEntity;
import com.diogotoporcov.filemanager.api.file.persistence.FileRepository;
import com.diogotoporcov.filemanager.api.file.web.FileResponse;
import com.diogotoporcov.filemanager.api.file.web.search.FileSearchQuery;
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
import com.diogotoporcov.filemanager.api.tag.domain.FileTagEntity;
import com.diogotoporcov.filemanager.api.tag.domain.FileTagId;
import com.diogotoporcov.filemanager.api.tag.domain.TagEntity;
import com.diogotoporcov.filemanager.api.tag.domain.TagScopeType;
import com.diogotoporcov.filemanager.api.tag.persistence.FileTagRepository;
import com.diogotoporcov.filemanager.api.tag.persistence.TagRepository;
import io.minio.MinioClient;
import java.time.OffsetDateTime;
import java.util.List;
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

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FileVisibilityIntegrationTest {
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
    @Autowired
    private TagRepository tagRepository;
    @Autowired
    private FileTagRepository fileTagRepository;

    @MockitoBean
    private MinioClient minioClient;
    @MockitoBean
    private KafkaTemplate<String, FileProcessingRequestedEvent> kafkaTemplate;
    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void searchReturnsOnlyActorVisibleFiles() {
        User actor = saveUser("actor@example.com");
        User other = saveUser("other@example.com");
        FolderEntity actorFolder = saveFolder("actor-folder", actor, null);
        FolderEntity sharedFolder = saveFolder("shared-folder", other, null);
        FolderEntity unsharedFolder = saveFolder("unshared-folder", other, null);
        FileEntity owned = saveFile("owned.txt", actor, null);
        FileEntity privateOther = saveFile("private.txt", other, null);
        FileEntity directGrant = saveFile("direct-grant.txt", other, null);
        FileEntity folderGrant = saveFile("folder-grant.txt", other, sharedFolder);
        FileEntity unsharedFolderFile = saveFile("unshared-folder.txt", other, unsharedFolder);
        FileEntity guestOwnedInActorFolder = saveFile("guest-owned.txt", other, actorFolder);
        saveFileGrant(directGrant, actor, other, Permission.FILE_VIEW);
        saveFolderGrant(sharedFolder, actor, other, Permission.FOLDER_VIEW);

        List<String> names = searchNames(actor, query("name,asc", null));

        assertThat(names).containsExactly(
                directGrant.getName(),
                folderGrant.getName(),
                guestOwnedInActorFolder.getName(),
                owned.getName());
        assertThat(names).doesNotContain(privateOther.getName(), unsharedFolderFile.getName());
    }

    @Test
    void folderIdSearchRequiresFolderVisibility() {
        User actor = saveUser("actor@example.com");
        User other = saveUser("other@example.com");
        FolderEntity unsharedFolder = saveFolder("unshared", other, null);
        FileSearchQuery query = query("name,asc", null);
        query.setFolderId(unsharedFolder.getId());

        assertThrows(AccessDeniedException.class, () -> fileService.searchFiles(query, actor.getId()));
    }

    @Test
    void tagFilterDoesNotLeakInaccessibleFiles() {
        User actor = saveUser("actor@example.com");
        User other = saveUser("other@example.com");
        FileEntity visible = saveFile("visible.txt", actor, null);
        FileEntity inaccessible = saveFile("inaccessible.txt", other, null);
        TagEntity tag = saveOwnerTag("wedding", actor);
        saveFileTag(visible, tag, actor);
        saveFileTag(inaccessible, tag, actor);
        FileSearchQuery query = query("name,asc", null);
        query.setTagId(tag.getId());

        List<String> names = searchNames(actor, query);

        assertThat(names).containsExactly("visible.txt");
    }

    @Test
    void limitAppliesAfterVisibilityAndSortRemainsDeterministic() {
        User actor = saveUser("actor@example.com");
        User other = saveUser("other@example.com");
        saveFile("a-private.txt", other, null);
        saveFile("b-private.txt", other, null);
        saveFile("c-visible.txt", actor, null);
        saveFile("d-visible.txt", actor, null);
        saveFile("e-visible.txt", actor, null);
        FileSearchQuery query = query("name,asc", 2);

        var page = fileService.searchFiles(query, actor.getId());

        assertThat(page.getItems()).extracting("name").containsExactly("c-visible.txt", "d-visible.txt");
        assertThat(page.isHasMore()).isTrue();
    }

    @Test
    void fileInChildFolderIsVisibleThroughRecursiveFolderGrantOnAncestor() {
        User actor = saveUser("actor@example.com");
        User owner = saveUser("owner@example.com");
        FolderEntity parent = saveFolder("photos", owner, null);
        FolderEntity child = saveFolder("wedding", owner, parent);
        FileEntity visible = saveFile("child-visible.txt", owner, child);
        saveFolderGrant(parent, actor, owner, Permission.FOLDER_VIEW, FolderGrantScope.RECURSIVE);

        List<String> names = searchNames(actor, query("name,asc", null));

        assertThat(names).containsExactly(visible.getName());
    }

    @Test
    void fileInGrandchildFolderIsVisibleThroughRecursiveFolderGrantOnAncestor() {
        User actor = saveUser("actor@example.com");
        User owner = saveUser("owner@example.com");
        FolderEntity parent = saveFolder("photos", owner, null);
        FolderEntity child = saveFolder("wedding", owner, parent);
        FolderEntity grandchild = saveFolder("raw", owner, child);
        FileEntity visible = saveFile("grandchild-visible.txt", owner, grandchild);
        saveFolderGrant(parent, actor, owner, Permission.FOLDER_VIEW, FolderGrantScope.RECURSIVE);

        List<String> names = searchNames(actor, query("name,asc", null));

        assertThat(names).containsExactly(visible.getName());
    }

    @Test
    void directFolderGrantOnAncestorDoesNotExposeGrandchildFiles() {
        User actor = saveUser("actor@example.com");
        User owner = saveUser("owner@example.com");
        FolderEntity parent = saveFolder("photos", owner, null);
        FolderEntity child = saveFolder("wedding", owner, parent);
        FolderEntity grandchild = saveFolder("raw", owner, child);
        saveFile("hidden.txt", owner, grandchild);
        saveFolderGrant(parent, actor, owner, Permission.FOLDER_VIEW, FolderGrantScope.DIRECT);

        List<String> names = searchNames(actor, query("name,asc", null));

        assertThat(names).isEmpty();
    }

    @Test
    void tagFilterDoesNotLeakFilesOutsideRecursiveGrantScope() {
        User actor = saveUser("actor@example.com");
        User owner = saveUser("owner@example.com");
        FolderEntity sharedParent = saveFolder("shared", owner, null);
        FolderEntity sharedChild = saveFolder("shared-child", owner, sharedParent);
        FolderEntity unsharedParent = saveFolder("unshared", owner, null);
        FileEntity visible = saveFile("visible-tagged.txt", owner, sharedChild);
        FileEntity hidden = saveFile("hidden-tagged.txt", owner, unsharedParent);
        TagEntity tag = saveOwnerTag("wedding", actor);
        saveFileTag(visible, tag, actor);
        saveFileTag(hidden, tag, actor);
        saveFolderGrant(sharedParent, actor, owner, Permission.FOLDER_VIEW, FolderGrantScope.RECURSIVE);
        FileSearchQuery query = query("name,asc", null);
        query.setTagId(tag.getId());

        List<String> names = searchNames(actor, query);

        assertThat(names).containsExactly(visible.getName());
    }

    @Test
    void limitAndSortApplyAfterRecursiveVisibility() {
        User actor = saveUser("actor@example.com");
        User owner = saveUser("owner@example.com");
        FolderEntity parent = saveFolder("shared", owner, null);
        FolderEntity child = saveFolder("child", owner, parent);
        saveFile("a-private.txt", owner, null);
        saveFile("b-private.txt", owner, null);
        saveFile("c-visible.txt", owner, child);
        saveFile("d-visible.txt", owner, child);
        saveFile("e-visible.txt", owner, child);
        saveFolderGrant(parent, actor, owner, Permission.FOLDER_VIEW, FolderGrantScope.RECURSIVE);
        FileSearchQuery query = query("name,asc", 2);

        var page = fileService.searchFiles(query, actor.getId());

        assertThat(page.getItems()).extracting("name").containsExactly("c-visible.txt", "d-visible.txt");
        assertThat(page.isHasMore()).isTrue();
    }

    @Test
    void revokedRecursiveFolderGrantRemovesSearchVisibility() {
        User actor = saveUser("actor@example.com");
        User owner = saveUser("owner@example.com");
        FolderEntity parent = saveFolder("shared", owner, null);
        FolderEntity child = saveFolder("child", owner, parent);
        saveFile("revoked-hidden.txt", owner, child);
        FolderGrantEntity grant = saveFolderGrant(parent, actor, owner, Permission.FOLDER_VIEW, FolderGrantScope.RECURSIVE);
        grant.setRevokedAt(OffsetDateTime.now());
        folderGrantRepository.saveAndFlush(grant);

        List<String> names = searchNames(actor, query("name,asc", null));

        assertThat(names).isEmpty();
    }

    private List<String> searchNames(User actor, FileSearchQuery query) {
        return fileService.searchFiles(query, actor.getId()).getItems().stream()
                .map(FileResponse::getName)
                .toList();
    }

    private FileSearchQuery query(String sort, Integer limit) {
        FileSearchQuery query = new FileSearchQuery();
        query.setSort(sort);
        query.setLimit(limit);

        return query;
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
                .size(1L)
                .ownerUser(owner)
                .createdByUser(owner)
                .folder(folder)
                .build());
    }

    private void saveFileGrant(FileEntity file, User grantee, User createdBy, Permission permission) {
        fileGrantRepository.saveAndFlush(FileGrantEntity.builder()
                .file(file)
                .granteeUser(grantee)
                .createdByUser(createdBy)
                .permission(permission)
                .build());
    }

    private void saveFolderGrant(FolderEntity folder, User grantee, User createdBy, Permission permission) {
        saveFolderGrant(folder, grantee, createdBy, permission, FolderGrantScope.DIRECT);
    }

    private FolderGrantEntity saveFolderGrant(
            FolderEntity folder,
            User grantee,
            User createdBy,
            Permission permission,
            FolderGrantScope scope) {
        return folderGrantRepository.saveAndFlush(FolderGrantEntity.builder()
                .folder(folder)
                .granteeUser(grantee)
                .createdByUser(createdBy)
                .permission(permission)
                .scope(scope)
                .build());
    }

    private TagEntity saveOwnerTag(String normalizedName, User owner) {
        return tagRepository.saveAndFlush(TagEntity.builder()
                .displayName(normalizedName)
                .normalizedName(normalizedName)
                .scopeType(TagScopeType.OWNER)
                .ownerUser(owner)
                .createdByUser(owner)
                .build());
    }

    private void saveFileTag(FileEntity file, TagEntity tag, User taggedBy) {
        fileTagRepository.saveAndFlush(FileTagEntity.builder()
                .id(new FileTagId(file.getId(), tag.getId()))
                .file(file)
                .tag(tag)
                .taggedByUser(taggedBy)
                .build());
    }
}

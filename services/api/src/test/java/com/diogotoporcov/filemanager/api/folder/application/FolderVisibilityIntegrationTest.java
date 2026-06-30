package com.diogotoporcov.filemanager.api.folder.application;

import com.diogotoporcov.filemanager.api.auth.domain.Permission;
import com.diogotoporcov.filemanager.api.exception.AccessDeniedException;
import com.diogotoporcov.filemanager.api.folder.domain.FolderClosureEntity;
import com.diogotoporcov.filemanager.api.folder.domain.FolderClosureId;
import com.diogotoporcov.filemanager.api.folder.domain.FolderEntity;
import com.diogotoporcov.filemanager.api.folder.persistence.FolderClosureRepository;
import com.diogotoporcov.filemanager.api.folder.persistence.FolderRepository;
import com.diogotoporcov.filemanager.api.identity.domain.User;
import com.diogotoporcov.filemanager.api.identity.persistence.UserRepository;
import com.diogotoporcov.filemanager.api.processing.messaging.FileProcessingRequestedEvent;
import com.diogotoporcov.filemanager.api.sharing.domain.FolderGrantEntity;
import com.diogotoporcov.filemanager.api.sharing.domain.FolderGrantScope;
import com.diogotoporcov.filemanager.api.sharing.persistence.FolderGrantRepository;
import io.minio.MinioClient;
import java.time.OffsetDateTime;
import java.util.List;
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
class FolderVisibilityIntegrationTest {
    @Autowired
    private FolderService folderService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private FolderRepository folderRepository;
    @Autowired
    private FolderClosureRepository folderClosureRepository;
    @Autowired
    private FolderGrantRepository folderGrantRepository;

    @MockitoBean
    private MinioClient minioClient;
    @MockitoBean
    private KafkaTemplate<String, FileProcessingRequestedEvent> kafkaTemplate;
    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void rootListingReturnsOwnedAndDirectlySharedRootsOnly() {
        User actor = saveUser("actor@example.com");
        User other = saveUser("other@example.com");
        FolderEntity owned = saveFolder("owned", actor, null);
        FolderEntity shared = saveFolder("shared", other, null);
        FolderEntity unshared = saveFolder("unshared", other, null);
        saveFolderGrant(shared, actor, other, Permission.FOLDER_VIEW);

        List<String> names = folderService.listRootFolders(null, actor.getId()).stream()
                .map(FolderEntity::getName)
                .toList();

        assertThat(names).containsExactly("owned", "shared");
        assertThat(names).doesNotContain(unshared.getName());
        assertThat(names).contains(owned.getName());
    }

    @Test
    void childListingRequiresParentVisibility() {
        User actor = saveUser("actor@example.com");
        User other = saveUser("other@example.com");
        FolderEntity unsharedParent = saveFolder("unshared-parent", other, null);
        saveFolder("child", other, unsharedParent);

        assertThrows(AccessDeniedException.class,
                () -> folderService.listChildFolders(unsharedParent.getId(), null, actor.getId()));
    }

    @Test
    void parentViewGrantExposesOnlyDirectChildFolders() {
        User actor = saveUser("actor@example.com");
        User owner = saveUser("owner@example.com");
        FolderEntity parent = saveFolder("parent", owner, null);
        FolderEntity child = saveFolder("child", owner, parent);
        FolderEntity grandchild = saveFolder("grandchild", owner, child);
        saveFolderGrant(parent, actor, owner, Permission.FOLDER_VIEW);

        List<String> directChildNames = folderService.listChildFolders(parent.getId(), null, actor.getId()).stream()
                .map(FolderEntity::getName)
                .toList();

        assertThat(directChildNames).containsExactly("child");
        assertThat(directChildNames).doesNotContain(grandchild.getName());
        assertThrows(AccessDeniedException.class,
                () -> folderService.listChildFolders(child.getId(), null, actor.getId()));
    }

    @Test
    void rootListingIncludesRecursivelySharedRootFolder() {
        User actor = saveUser("actor@example.com");
        User owner = saveUser("owner@example.com");
        FolderEntity shared = saveFolder("shared", owner, null);
        saveFolderGrant(shared, actor, owner, Permission.FOLDER_VIEW, FolderGrantScope.RECURSIVE);

        List<String> names = folderService.listRootFolders(null, actor.getId()).stream()
                .map(FolderEntity::getName)
                .toList();

        assertThat(names).containsExactly("shared");
    }

    @Test
    void recursiveViewGrantExposesChildAndGrandchildFolders() {
        User actor = saveUser("actor@example.com");
        User owner = saveUser("owner@example.com");
        FolderEntity parent = saveFolder("parent", owner, null);
        FolderEntity child = saveFolder("child", owner, parent);
        FolderEntity grandchild = saveFolder("grandchild", owner, child);
        saveFolderGrant(parent, actor, owner, Permission.FOLDER_VIEW, FolderGrantScope.RECURSIVE);

        List<String> childNames = folderService.listChildFolders(parent.getId(), null, actor.getId()).stream()
                .map(FolderEntity::getName)
                .toList();
        List<String> grandchildNames = folderService.listChildFolders(child.getId(), null, actor.getId()).stream()
                .map(FolderEntity::getName)
                .toList();

        assertThat(childNames).containsExactly(child.getName());
        assertThat(grandchildNames).containsExactly(grandchild.getName());
    }

    @Test
    void revokedRecursiveViewGrantRemovesDescendantFolderVisibility() {
        User actor = saveUser("actor@example.com");
        User owner = saveUser("owner@example.com");
        FolderEntity parent = saveFolder("parent", owner, null);
        FolderEntity child = saveFolder("child", owner, parent);
        FolderGrantEntity grant = saveFolderGrant(parent, actor, owner, Permission.FOLDER_VIEW, FolderGrantScope.RECURSIVE);
        grant.setRevokedAt(OffsetDateTime.now());
        folderGrantRepository.saveAndFlush(grant);

        assertThrows(AccessDeniedException.class,
                () -> folderService.listChildFolders(child.getId(), null, actor.getId()));
    }

    @Test
    void recursiveCreateGrantAllowsCreatingChildFolderUnderDescendant() {
        User actor = saveUser("actor@example.com");
        User owner = saveUser("owner@example.com");
        FolderEntity parent = saveFolder("parent", owner, null);
        FolderEntity child = saveFolder("child", owner, parent);
        saveFolderGrant(parent, actor, owner, Permission.FOLDER_CREATE, FolderGrantScope.RECURSIVE);
        CreateFolderCommand request = new CreateFolderCommand("guest-created", child.getId());

        var response = folderService.createFolder(request, actor.getId());

        assertThat(response.getParentFolder().getId()).isEqualTo(child.getId());
        assertThat(response.getOwnerUser().getId()).isEqualTo(actor.getId());
    }

    @Test
    void recursiveUploadRenameAndDeleteGrantsAuthorizeDescendantFolderPermissions() {
        User actor = saveUser("actor@example.com");
        User owner = saveUser("owner@example.com");
        FolderEntity parent = saveFolder("parent", owner, null);
        FolderEntity child = saveFolder("child", owner, parent);
        saveFolderGrant(parent, actor, owner, Permission.FOLDER_UPLOAD_FILE, FolderGrantScope.RECURSIVE);
        saveFolderGrant(parent, actor, owner, Permission.FOLDER_RENAME, FolderGrantScope.RECURSIVE);
        saveFolderGrant(parent, actor, owner, Permission.FOLDER_DELETE, FolderGrantScope.RECURSIVE);

        assertThat(folderService.findAccessibleFolder(child.getId(), actor.getId(), Permission.FOLDER_UPLOAD_FILE).getId())
                .isEqualTo(child.getId());
        assertThat(folderService.findAccessibleFolder(child.getId(), actor.getId(), Permission.FOLDER_RENAME).getId())
                .isEqualTo(child.getId());
        assertThat(folderService.findAccessibleFolder(child.getId(), actor.getId(), Permission.FOLDER_DELETE).getId())
                .isEqualTo(child.getId());
    }

    @Test
    void createRootFolderWritesSelfClosureRow() {
        User actor = saveUser("root-owner@example.com");
        CreateFolderCommand request = new CreateFolderCommand("root", null);

        var response = folderService.createFolder(request, actor.getId());

        assertThat(folderClosureRepository.existsById(new FolderClosureId(response.getId(), response.getId()))).isTrue();
    }

    @Test
    void createChildFolderWritesSelfAndParentAncestorClosureRows() {
        User actor = saveUser("child-owner@example.com");
        FolderEntity parent = createFolderThroughService("parent", null, actor);

        FolderEntity child = createFolderThroughService("child", parent, actor);

        List<FolderClosureEntity> childClosureRows = folderClosureRepository.findByDescendantFolderOrderByDepthAsc(child);
        assertThat(childClosureRows).extracting(FolderClosureEntity::getDepth).containsExactly(0, 1);
        assertThat(childClosureRows).extracting(row -> row.getAncestorFolder().getId())
                .containsExactly(child.getId(), parent.getId());
    }

    @Test
    void createGrandchildFolderWritesRootParentAndSelfClosureRows() {
        User actor = saveUser("grandchild-owner@example.com");
        FolderEntity root = createFolderThroughService("root", null, actor);
        FolderEntity parent = createFolderThroughService("parent", root, actor);

        FolderEntity grandchild = createFolderThroughService("grandchild", parent, actor);

        List<FolderClosureEntity> rows = folderClosureRepository.findByDescendantFolderOrderByDepthAsc(grandchild);
        assertThat(rows).extracting(FolderClosureEntity::getDepth).containsExactly(0, 1, 2);
        assertThat(rows).extracting(row -> row.getAncestorFolder().getId())
                .containsExactly(grandchild.getId(), parent.getId(), root.getId());
    }

    @Test
    void deletedFolderDoesNotAuthorizeRecursiveAccessThroughClosure() {
        User actor = saveUser("actor-deleted@example.com");
        User owner = saveUser("owner-deleted@example.com");
        FolderEntity parent = saveFolder("parent", owner, null);
        FolderEntity child = saveFolder("child", owner, parent);
        child.setDeletedAt(OffsetDateTime.now());
        folderRepository.saveAndFlush(child);
        saveFolderGrant(parent, actor, owner, Permission.FOLDER_VIEW, FolderGrantScope.RECURSIVE);

        assertThat(folderGrantRepository.hasActiveRecursiveGrantOnFolderOrAncestor(
                child.getId(),
                actor.getId(),
                Permission.FOLDER_VIEW)).isFalse();
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

    private FolderEntity createFolderThroughService(String name, FolderEntity parent, User actor) {
        CreateFolderCommand request = new CreateFolderCommand(name, parent == null ? null : parent.getId());

        var response = folderService.createFolder(request, actor.getId());

        return folderRepository.findByIdAndDeletedAtIsNull(response.getId()).orElseThrow();
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
}

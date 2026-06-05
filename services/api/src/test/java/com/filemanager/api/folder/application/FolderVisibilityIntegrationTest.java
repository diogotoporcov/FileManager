package com.filemanager.api.folder.application;

import com.filemanager.api.auth.domain.Permission;
import com.filemanager.api.exception.AccessDeniedException;
import com.filemanager.api.folder.domain.FolderEntity;
import com.filemanager.api.folder.persistence.FolderRepository;
import com.filemanager.api.folder.web.FolderSummaryResponse;
import com.filemanager.api.identity.domain.User;
import com.filemanager.api.identity.persistence.UserRepository;
import com.filemanager.api.processing.messaging.FileProcessingRequestedEvent;
import com.filemanager.api.sharing.domain.FolderGrantEntity;
import com.filemanager.api.sharing.persistence.FolderGrantRepository;
import io.minio.MinioClient;
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
                .map(FolderSummaryResponse::getName)
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

        List<String> directChildNames = folderService.listChildFolders(parent.getId(), null, actor.getId()).getFolders().stream()
                .map(FolderSummaryResponse::getName)
                .toList();

        assertThat(directChildNames).containsExactly("child");
        assertThat(directChildNames).doesNotContain(grandchild.getName());
        assertThrows(AccessDeniedException.class,
                () -> folderService.listChildFolders(child.getId(), null, actor.getId()));
    }

    private User saveUser(String email) {
        return userRepository.saveAndFlush(User.builder().email(email).build());
    }

    private FolderEntity saveFolder(String name, User owner, FolderEntity parent) {
        return folderRepository.saveAndFlush(FolderEntity.builder()
                .name(name)
                .ownerUser(owner)
                .createdByUser(owner)
                .parentFolder(parent)
                .build());
    }

    private void saveFolderGrant(FolderEntity folder, User grantee, User createdBy, Permission permission) {
        folderGrantRepository.saveAndFlush(FolderGrantEntity.builder()
                .folder(folder)
                .granteeUser(grantee)
                .createdByUser(createdBy)
                .permission(permission)
                .build());
    }
}

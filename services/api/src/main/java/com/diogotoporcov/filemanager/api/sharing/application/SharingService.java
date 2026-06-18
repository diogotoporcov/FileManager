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
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SharingService {
    private static final Set<Permission> FILE_GRANT_PERMISSIONS = EnumSet.of(
            Permission.FILE_VIEW,
            Permission.FILE_MODIFY,
            Permission.FILE_DELETE);
    private static final Set<Permission> FOLDER_GRANT_PERMISSIONS = EnumSet.of(
            Permission.FOLDER_VIEW,
            Permission.FOLDER_CREATE,
            Permission.FOLDER_RENAME,
            Permission.FOLDER_DELETE,
            Permission.FOLDER_UPLOAD_FILE);

    private final FileGrantRepository fileGrantRepository;
    private final FolderGrantRepository folderGrantRepository;
    private final FileRepository fileRepository;
    private final FolderRepository folderRepository;
    private final UserRepository userRepository;

    @Transactional
    public List<FileGrantEntity> createFileGrants(UUID fileId, UUID granteeUserId, List<Permission> permissions, UUID actorUserId) {
        FileEntity file = findActiveFile(fileId);
        User actor = findUser(actorUserId);
        User grantee = findUser(granteeUserId);
        assertOwnsFile(actorUserId, file);
        rejectSelfGrant(actorUserId, granteeUserId);
        List<Permission> normalizedPermissions = normalizePermissions(permissions, FILE_GRANT_PERMISSIONS, "file");

        return normalizedPermissions.stream()
                .map(permission -> fileGrantRepository.findByFileIdAndGranteeUserIdAndPermissionAndRevokedAtIsNull(
                                fileId,
                                granteeUserId,
                                permission)
                        .orElseGet(() -> fileGrantRepository.save(FileGrantEntity.builder()
                                .file(file)
                                .granteeUser(grantee)
                                .permission(permission)
                                .createdByUser(actor)
                                .build())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FileGrantEntity> listFileGrants(UUID fileId, UUID actorUserId) {
        FileEntity file = findActiveFile(fileId);
        assertOwnsFile(actorUserId, file);

        return fileGrantRepository.findByFileIdAndRevokedAtIsNullOrderByCreatedAtAscIdAsc(fileId);
    }

    @Transactional
    public void revokeFileGrant(UUID fileId, UUID grantId, UUID actorUserId) {
        FileEntity file = findActiveFile(fileId);
        assertOwnsFile(actorUserId, file);
        FileGrantEntity grant = fileGrantRepository.findByIdAndFileIdAndRevokedAtIsNull(grantId, fileId)
                .orElseThrow(() -> new ResourceNotFoundException("File grant not found: " + grantId));
        grant.setRevokedAt(OffsetDateTime.now());
        fileGrantRepository.save(grant);
    }

    @Transactional
    public List<FolderGrantEntity> createFolderGrants(UUID folderId, UUID granteeUserId, List<Permission> permissions, UUID actorUserId) {
        return createFolderGrants(folderId, granteeUserId, permissions, FolderGrantScope.DIRECT, actorUserId);
    }

    @Transactional
    public List<FolderGrantEntity> createFolderGrants(
            UUID folderId,
            UUID granteeUserId,
            List<Permission> permissions,
            FolderGrantScope scope,
            UUID actorUserId) {
        FolderEntity folder = findActiveFolder(folderId);
        User actor = findUser(actorUserId);
        User grantee = findUser(granteeUserId);
        assertOwnsFolder(actorUserId, folder);
        rejectSelfGrant(actorUserId, granteeUserId);
        List<Permission> normalizedPermissions = normalizePermissions(permissions, FOLDER_GRANT_PERMISSIONS, "folder");
        FolderGrantScope normalizedScope = normalizeFolderGrantScope(scope);

        return normalizedPermissions.stream()
                .map(permission -> folderGrantRepository.findByFolderIdAndGranteeUserIdAndPermissionAndRevokedAtIsNull(
                                folderId,
                                granteeUserId,
                                permission)
                        .map(existingGrant -> updateFolderGrantScope(existingGrant, normalizedScope))
                        .orElseGet(() -> folderGrantRepository.save(FolderGrantEntity.builder()
                                .folder(folder)
                                .granteeUser(grantee)
                                .permission(permission)
                                .scope(normalizedScope)
                                .createdByUser(actor)
                                .build())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FolderGrantEntity> listFolderGrants(UUID folderId, UUID actorUserId) {
        FolderEntity folder = findActiveFolder(folderId);
        assertOwnsFolder(actorUserId, folder);

        return folderGrantRepository.findByFolderIdAndRevokedAtIsNullOrderByCreatedAtAscIdAsc(folderId);
    }

    @Transactional
    public void revokeFolderGrant(UUID folderId, UUID grantId, UUID actorUserId) {
        FolderEntity folder = findActiveFolder(folderId);
        assertOwnsFolder(actorUserId, folder);
        FolderGrantEntity grant = folderGrantRepository.findByIdAndFolderIdAndRevokedAtIsNull(grantId, folderId)
                .orElseThrow(() -> new ResourceNotFoundException("Folder grant not found: " + grantId));
        grant.setRevokedAt(OffsetDateTime.now());
        folderGrantRepository.save(grant);
    }

    private List<Permission> normalizePermissions(List<Permission> permissions, Set<Permission> allowed, String resourceType) {
        if (permissions == null || permissions.isEmpty()) {
            throw new IllegalArgumentException("At least one permission is required.");
        }

        return permissions.stream()
                .peek(permission -> {
                    if (permission == null) {
                        throw new IllegalArgumentException("Permission is required.");
                    }
                    if (!allowed.contains(permission)) {
                        throw new IllegalArgumentException("Permission " + permission + " is not valid for " + resourceType + " grants.");
                    }
                })
                .distinct()
                .toList();
    }

    private FolderGrantScope normalizeFolderGrantScope(FolderGrantScope scope) {
        return scope == null ? FolderGrantScope.DIRECT : scope;
    }

    private FolderGrantEntity updateFolderGrantScope(FolderGrantEntity grant, FolderGrantScope requestedScope) {
        FolderGrantScope currentScope = normalizeFolderGrantScope(grant.getScope());
        if (currentScope == requestedScope) {
            return grant;
        }

        grant.setScope(requestedScope);

        return folderGrantRepository.save(grant);
    }

    private FileEntity findActiveFile(UUID fileId) {
        return fileRepository.findByIdAndDeletedAtIsNull(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found: " + fileId));
    }

    private FolderEntity findActiveFolder(UUID folderId) {
        return folderRepository.findByIdAndDeletedAtIsNull(folderId)
                .orElseThrow(() -> new ResourceNotFoundException("Folder not found: " + folderId));
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }

    private void assertOwnsFile(UUID actorUserId, FileEntity file) {
        if (!file.isOwnedBy(actorUserId)) {
            throw new AccessDeniedException("Only the file owner can manage file grants.");
        }
    }

    private void assertOwnsFolder(UUID actorUserId, FolderEntity folder) {
        if (!folder.isOwnedBy(actorUserId)) {
            throw new AccessDeniedException("Only the folder owner can manage folder grants.");
        }
    }

    private void rejectSelfGrant(UUID actorUserId, UUID granteeUserId) {
        if (actorUserId != null && actorUserId.equals(granteeUserId)) {
            throw new IllegalArgumentException("Resource owners already have access and cannot grant resources to themselves.");
        }
    }
}

package com.filemanager.api.auth.application;

import com.filemanager.api.auth.domain.Permission;
import com.filemanager.api.exception.AccessDeniedException;
import com.filemanager.api.exception.ResourceNotFoundException;
import com.filemanager.api.file.domain.FileEntity;
import com.filemanager.api.file.persistence.FileRepository;
import com.filemanager.api.folder.domain.FolderEntity;
import com.filemanager.api.folder.persistence.FolderRepository;
import com.filemanager.api.sharing.persistence.FileGrantRepository;
import com.filemanager.api.sharing.persistence.FolderGrantRepository;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccessControlService {

    private final FileRepository fileRepository;
    private final FolderRepository folderRepository;
    private final FileGrantRepository fileGrantRepository;
    private final FolderGrantRepository folderGrantRepository;

    public void assertCanAccessFile(UUID actorUserId, UUID fileId, Permission permission) {
        FileEntity file = fileRepository.findByIdAndDeletedAtIsNull(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found with id: " + fileId));

        if (!hasFilePermission(actorUserId, file, permission)) {
            throw new AccessDeniedException("You do not have permission to access this file.");
        }
    }

    public void assertCanAccessFolder(UUID actorUserId, UUID folderId, Permission permission) {
        FolderEntity folder = folderRepository.findByIdAndDeletedAtIsNull(folderId)
                .orElseThrow(() -> new ResourceNotFoundException("Folder not found with id: " + folderId));

        if (!hasFolderPermission(actorUserId, folder, permission)) {
            throw new AccessDeniedException("You do not have permission to access this folder.");
        }
    }

    public boolean hasFilePermission(UUID actorUserId, FileEntity file, Permission permission) {
        Objects.requireNonNull(permission, "permission must not be null");
        if (actorUserId == null || file == null || file.getDeletedAt() != null) {
            return false;
        }

        if (file.isOwnedBy(actorUserId)) {
            return true;
        }

        if (isContainingFolderOwnedBy(file, actorUserId)) {
            return switch (permission) {
                case FILE_VIEW, FILE_MODIFY, FILE_DELETE -> true;
                default -> false;
            };
        }

        return switch (permission) {
            case FILE_VIEW -> hasActiveFileGrant(file, actorUserId, Permission.FILE_VIEW)
                    || hasActiveContainingFolderGrant(file, actorUserId, Permission.FOLDER_VIEW)
                    || hasActiveRecursiveContainingFolderGrant(file, actorUserId, Permission.FOLDER_VIEW);
            case FILE_MODIFY -> hasActiveFileGrant(file, actorUserId, Permission.FILE_MODIFY);
            case FILE_DELETE -> hasActiveFileGrant(file, actorUserId, Permission.FILE_DELETE);
            case FILE_SHARE -> false;
            default -> false;
        };
    }

    public boolean hasFolderPermission(UUID actorUserId, FolderEntity folder, Permission permission) {
        Objects.requireNonNull(permission, "permission must not be null");
        if (actorUserId == null || folder == null || folder.getDeletedAt() != null) {
            return false;
        }

        if (folder.isOwnedBy(actorUserId)) {
            return true;
        }

        if (isDirectParentFolderOwnedBy(folder, actorUserId)) {
            return switch (permission) {
                case FOLDER_VIEW, FOLDER_RENAME, FOLDER_DELETE -> true;
                default -> false;
            };
        }

        return switch (permission) {
            case FOLDER_VIEW -> hasActiveFolderGrant(folder, actorUserId, Permission.FOLDER_VIEW);
            case FOLDER_UPLOAD_FILE -> hasActiveFolderGrant(folder, actorUserId, Permission.FOLDER_UPLOAD_FILE);
            case FOLDER_CREATE -> hasActiveFolderGrant(folder, actorUserId, Permission.FOLDER_CREATE);
            case FOLDER_RENAME -> hasActiveFolderGrant(folder, actorUserId, Permission.FOLDER_RENAME);
            case FOLDER_DELETE -> hasActiveFolderGrant(folder, actorUserId, Permission.FOLDER_DELETE);
            case FOLDER_MANAGE_PERMISSIONS -> false;
            default -> false;
        };
    }

    public void assertCanUploadToOwner(UUID actorUserId, UUID ownerUserId) {
        assertActorOwnsContext(actorUserId, ownerUserId, "You can only upload files to your own user account.");
    }

    public void assertCanViewOwner(UUID actorUserId, UUID ownerUserId) {
        assertActorOwnsContext(actorUserId, ownerUserId, "You can only view your own user account.");
    }

    public void assertCanCreateFolderForOwner(UUID actorUserId, UUID ownerUserId) {
        assertActorOwnsContext(actorUserId, ownerUserId, "You can only create folders in your own user account.");
    }

    private boolean isContainingFolderOwnedBy(FileEntity file, UUID actorUserId) {
        return file.getFolder() != null && file.getFolder().isOwnedBy(actorUserId) && file.getFolder().getDeletedAt() == null;
    }

    private boolean hasActiveFileGrant(FileEntity file, UUID actorUserId, Permission permission) {
        return file.getId() != null && fileGrantRepository.hasActiveGrant(file.getId(), actorUserId, permission);
    }

    private boolean hasActiveContainingFolderGrant(FileEntity file, UUID actorUserId, Permission permission) {
        return file.getFolder() != null
                && file.getFolder().getId() != null
                && file.getFolder().getDeletedAt() == null
                && folderGrantRepository.hasActiveDirectGrant(file.getFolder().getId(), actorUserId, permission);
    }

    private boolean hasActiveRecursiveContainingFolderGrant(FileEntity file, UUID actorUserId, Permission permission) {
        return file.getFolder() != null
                && file.getFolder().getId() != null
                && file.getFolder().getDeletedAt() == null
                && folderGrantRepository.hasActiveRecursiveGrantOnFolderOrAncestor(
                        file.getFolder().getId(),
                        actorUserId,
                        permission);
    }

    private boolean hasActiveFolderGrant(FolderEntity folder, UUID actorUserId, Permission permission) {
        return folder.getId() != null
                && (folderGrantRepository.hasActiveDirectGrant(folder.getId(), actorUserId, permission)
                || folderGrantRepository.hasActiveRecursiveGrantOnFolderOrAncestor(folder.getId(), actorUserId, permission));
    }

    private boolean isDirectParentFolderOwnedBy(FolderEntity folder, UUID actorUserId) {
        return folder.getParentFolder() != null
                && folder.getParentFolder().getDeletedAt() == null
                && folder.getParentFolder().isOwnedBy(actorUserId);
    }

    private void assertActorOwnsContext(UUID actorUserId, UUID ownerUserId, String deniedMessage) {
        if (actorUserId == null) {
            throw new AccessDeniedException("Actor user ID is required.");
        }

        if (ownerUserId == null) {
            throw new IllegalArgumentException("ownerUserId must be provided.");
        }

        if (!Objects.equals(ownerUserId, actorUserId)) {
            throw new AccessDeniedException(deniedMessage);
        }
    }
}

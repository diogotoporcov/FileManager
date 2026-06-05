package com.filemanager.api.auth;

import com.filemanager.api.entity.FileEntity;
import com.filemanager.api.entity.FolderEntity;
import com.filemanager.api.exception.AccessDeniedException;
import com.filemanager.api.exception.ResourceNotFoundException;
import com.filemanager.api.repository.FileRepository;
import com.filemanager.api.repository.FolderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccessControlService {

    private final FileRepository fileRepository;
    private final FolderRepository folderRepository;

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

        return file.isOwnedBy(actorUserId);
    }

    public boolean hasFolderPermission(UUID actorUserId, FolderEntity folder, Permission permission) {
        Objects.requireNonNull(permission, "permission must not be null");
        if (actorUserId == null || folder == null || folder.getDeletedAt() != null) {
            return false;
        }

        return folder.isOwnedBy(actorUserId);
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

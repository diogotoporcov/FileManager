package com.filemanager.api.auth;

import com.filemanager.api.entity.DuplicateCandidate;
import com.filemanager.api.entity.FileEntity;
import com.filemanager.api.entity.FolderEntity;
import com.filemanager.api.entity.OrganizationMember;
import com.filemanager.api.exception.AccessDeniedException;
import com.filemanager.api.exception.ResourceNotFoundException;
import com.filemanager.api.port.RolePermissionPolicyPort;
import com.filemanager.api.repository.DuplicateCandidateRepository;
import com.filemanager.api.repository.FileRepository;
import com.filemanager.api.repository.FolderRepository;
import com.filemanager.api.repository.OrganizationMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccessControlService {

    private final FileRepository fileRepository;
    private final FolderRepository folderRepository;
    private final DuplicateCandidateRepository duplicateCandidateRepository;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final RolePermissionPolicyPort rolePermissionPolicyPort;

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

    public void assertCanManageDuplicate(UUID actorUserId, UUID duplicateCandidateId) {
        DuplicateCandidate candidate = duplicateCandidateRepository.findById(duplicateCandidateId)
                .orElseThrow(() -> new ResourceNotFoundException("Duplicate candidate not found with id: " + duplicateCandidateId));

        if (hasFilePermission(actorUserId, candidate.getSourceFile(), Permission.DUPLICATE_MANAGE)) {
            return;
        }

        if (hasFilePermission(actorUserId, candidate.getCandidateFile(), Permission.DUPLICATE_MANAGE)) {
            return;
        }

        throw new AccessDeniedException("You do not have permission to manage this duplicate candidate.");
    }

    private boolean hasFilePermission(UUID actorUserId, FileEntity file, Permission permission) {
        return hasOwnedResourcePermission(
                actorUserId,
                file.getOwnerUser() != null ? file.getOwnerUser().getId() : null,
                file.getOwnerOrganization() != null ? file.getOwnerOrganization().getId() : null,
                file.getDeletedAt(),
                permission);
    }

    private boolean hasFolderPermission(UUID actorUserId, FolderEntity folder, Permission permission) {
        return hasOwnedResourcePermission(
                actorUserId,
                folder.getOwnerUser() != null ? folder.getOwnerUser().getId() : null,
                folder.getOwnerOrganization() != null ? folder.getOwnerOrganization().getId() : null,
                folder.getDeletedAt(),
                permission);
    }

    private boolean hasOwnedResourcePermission(
            UUID actorUserId,
            UUID ownerUserId,
            UUID ownerOrganizationId,
            OffsetDateTime deletedAt,
            Permission permission) {
        if (deletedAt != null) {
            return false;
        }

        if (ownerUserId != null) {
            return Objects.equals(ownerUserId, actorUserId);
        }

        if (ownerOrganizationId != null) {
            return organizationMemberRepository.findByOrganizationIdAndUserId(ownerOrganizationId, actorUserId)
                    .map(member -> rolePermissionPolicyPort.hasPermission(member.getRole(), permission))
                    .orElse(false);
        }

        return false;
    }

    public void assertOrganizationPermission(UUID actorUserId, UUID organizationId, Permission permission) {
        if (actorUserId == null) {
             throw new AccessDeniedException("Actor user ID is required.");
        }
        
        OrganizationMember member = organizationMemberRepository.findByOrganizationIdAndUserId(organizationId, actorUserId)
                .orElseThrow(() -> new AccessDeniedException("User is not a member of the organization."));

        if (!rolePermissionPolicyPort.hasPermission(member.getRole(), permission)) {
            throw new AccessDeniedException("User does not have required permission: " + permission);
        }
    }

    public void assertCanUploadToContext(UUID actorUserId, UUID ownerUserId, UUID ownerOrganizationId) {
        assertOwnershipContext(actorUserId, ownerUserId, ownerOrganizationId, Permission.FILE_UPLOAD, "You can only upload files to your own user account.");
    }

    public void assertCanViewContext(UUID actorUserId, UUID ownerUserId, UUID ownerOrganizationId) {
        assertOwnershipContext(actorUserId, ownerUserId, ownerOrganizationId, Permission.FOLDER_VIEW, "You can only view your own user account.");
    }

    public void assertCanCreateFolderInContext(UUID actorUserId, UUID ownerUserId, UUID ownerOrganizationId) {
        assertOwnershipContext(actorUserId, ownerUserId, ownerOrganizationId, Permission.FOLDER_CREATE, "You can only create folders in your own user account.");
    }

    public void assertCanViewDuplicates(UUID actorUserId, UUID ownerUserId, UUID ownerOrganizationId) {
        assertOwnershipContext(actorUserId, ownerUserId, ownerOrganizationId, Permission.DUPLICATE_VIEW, "You can only view your own duplicates.");
    }

    private void assertOwnershipContext(UUID actorUserId, UUID ownerUserId, UUID ownerOrganizationId, Permission orgPermission, String userDeniedMessage) {
        if (ownerUserId != null) {
            if (!ownerUserId.equals(actorUserId)) {
                throw new AccessDeniedException(userDeniedMessage);
            }

            return;
        }

        if (ownerOrganizationId != null) {
            assertOrganizationPermission(actorUserId, ownerOrganizationId, orgPermission);
            return;
        }

        throw new IllegalArgumentException("Either ownerUserId or ownerOrganizationId must be provided.");
    }
}

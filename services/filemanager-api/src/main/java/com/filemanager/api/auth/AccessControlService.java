package com.filemanager.api.auth;

import com.filemanager.api.entity.DuplicateCandidate;
import com.filemanager.api.entity.FileEntity;
import com.filemanager.api.entity.OrganizationMember;
import com.filemanager.api.exception.AccessDeniedException;
import com.filemanager.api.exception.ResourceNotFoundException;
import com.filemanager.api.repository.DuplicateCandidateRepository;
import com.filemanager.api.repository.FileRepository;
import com.filemanager.api.repository.OrganizationMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccessControlService {

    private final FileRepository fileRepository;
    private final DuplicateCandidateRepository duplicateCandidateRepository;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final RolePermissionResolver rolePermissionResolver;

    public void assertCanAccessFile(UUID actorUserId, UUID fileId, Permission permission) {
        FileEntity file = fileRepository.findByIdAndDeletedAtIsNull(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found with id: " + fileId));

        if (!hasFilePermission(actorUserId, file, permission)) {
            throw new AccessDeniedException("You do not have permission to access this file.");
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
        if (file.getDeletedAt() != null) {
            return false;
        }

        if (file.getOwnerUser() != null) {
            return Objects.equals(file.getOwnerUser().getId(), actorUserId);
        }

        if (file.getOwnerOrganization() != null) {
            return organizationMemberRepository.findByOrganizationIdAndUserId(file.getOwnerOrganization().getId(), actorUserId)
                    .map(member -> rolePermissionResolver.hasPermission(member.getRole(), permission))
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

        if (!rolePermissionResolver.hasPermission(member.getRole(), permission)) {
            throw new AccessDeniedException("User does not have required permission: " + permission);
        }
    }

    public void assertCanUploadToContext(UUID actorUserId, UUID ownerUserId, UUID ownerOrganizationId) {
        assertOwnershipContext(actorUserId, ownerUserId, ownerOrganizationId, Permission.FILE_UPLOAD, "You can only upload files to your own user account.");
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

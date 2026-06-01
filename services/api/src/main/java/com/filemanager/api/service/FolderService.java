package com.filemanager.api.service;

import com.filemanager.api.auth.AccessControlService;
import com.filemanager.api.auth.Permission;
import com.filemanager.api.dto.CreateFolderRequest;
import com.filemanager.api.dto.FolderChildrenResponse;
import com.filemanager.api.dto.FolderResponse;
import com.filemanager.api.dto.FolderSummaryResponse;
import com.filemanager.api.dto.UpdateFolderRequest;
import com.filemanager.api.entity.FolderEntity;
import com.filemanager.api.entity.Organization;
import com.filemanager.api.entity.User;
import com.filemanager.api.exception.ConflictException;
import com.filemanager.api.exception.ResourceNotFoundException;
import com.filemanager.api.mapper.FolderResponseMapper;
import com.filemanager.api.repository.FileRepository;
import com.filemanager.api.repository.FolderRepository;
import com.filemanager.api.repository.OrganizationRepository;
import com.filemanager.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FolderService {
    private static final int MAX_FOLDER_NAME_LENGTH = 255;

    private final FolderRepository folderRepository;
    private final FileRepository fileRepository;
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final AccessControlService accessControlService;
    private final FolderResponseMapper folderResponseMapper;
    private final TagService tagService;

    @Transactional
    public FolderResponse createFolder(CreateFolderRequest request, UUID actorUserId) {
        Objects.requireNonNull(request, "request must not be null");
        String name = normalizeFolderName(request.getName());
        User createdByUser = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + actorUserId));

        FolderEntity parentFolder = null;
        User ownerUser;
        Organization ownerOrganization;
        if (request.getParentFolderId() != null) {
            parentFolder = folderRepository.findByIdAndDeletedAtIsNull(request.getParentFolderId())
                    .orElseThrow(() -> new ResourceNotFoundException("Folder not found: " + request.getParentFolderId()));
            accessControlService.assertCanAccessFolder(actorUserId, parentFolder.getId(), Permission.FOLDER_CREATE);
            ownerUser = parentFolder.getOwnerUser();
            ownerOrganization = parentFolder.getOwnerOrganization();
            validateParentOwnerRequest(parentFolder, request.getOwnerUserId(), request.getOwnerOrganizationId());
        } else {
            validateExactlyOneOwner(request.getOwnerUserId(), request.getOwnerOrganizationId());
            accessControlService.assertCanCreateFolderInContext(
                    actorUserId,
                    request.getOwnerUserId(),
                    request.getOwnerOrganizationId());
            ownerUser = resolveOwnerUser(request.getOwnerUserId());
            ownerOrganization = resolveOwnerOrganization(request.getOwnerOrganizationId());
        }

        rejectDuplicateActiveSibling(name, ownerUser, ownerOrganization, parentFolder);

        FolderEntity folder = FolderEntity.builder()
                .name(name)
                .parentFolder(parentFolder)
                .ownerUser(ownerUser)
                .ownerOrganization(ownerOrganization)
                .createdByUser(createdByUser)
                .build();

        return folderResponseMapper.toResponse(folderRepository.save(folder));
    }

    @Transactional(readOnly = true)
    public FolderResponse getFolder(UUID folderId, UUID actorUserId) {
        FolderEntity folder = findAccessibleFolder(folderId, actorUserId, Permission.FOLDER_VIEW);
        return folderResponseMapper.toResponse(folder);
    }

    @Transactional
    public FolderResponse renameFolder(UUID folderId, UpdateFolderRequest request, UUID actorUserId) {
        Objects.requireNonNull(request, "request must not be null");
        FolderEntity folder = findAccessibleFolder(folderId, actorUserId, Permission.FOLDER_RENAME);
        String name = normalizeFolderName(request.getName());

        if (!folder.getName().equalsIgnoreCase(name)) {
            rejectDuplicateActiveSibling(name, folder.getOwnerUser(), folder.getOwnerOrganization(), folder.getParentFolder());
        }

        folder.setName(name);
        return folderResponseMapper.toResponse(folderRepository.save(folder));
    }

    @Transactional
    public void deleteFolder(UUID folderId, UUID actorUserId) {
        FolderEntity folder = findAccessibleFolder(folderId, actorUserId, Permission.FOLDER_DELETE);
        if (folderRepository.existsByParentFolderAndDeletedAtIsNull(folder)) {
            throw new ConflictException("Folder is not empty");
        }
        if (fileRepository.existsByFolderAndDeletedAtIsNull(folder)) {
            throw new ConflictException("Folder is not empty");
        }

        folder.setDeletedAt(OffsetDateTime.now());
        folderRepository.save(folder);
    }

    @Transactional(readOnly = true)
    public List<FolderSummaryResponse> listRootFolders(
            UUID ownerUserId,
            UUID ownerOrganizationId,
            UUID tagId,
            UUID actorUserId) {
        validateExactlyOneOwner(ownerUserId, ownerOrganizationId);
        accessControlService.assertCanViewContext(actorUserId, ownerUserId, ownerOrganizationId);
        tagService.assertCanUseTagForFolderListing(tagId, actorUserId, ownerUserId, ownerOrganizationId, null);

        if (ownerUserId != null) {
            User ownerUser = resolveOwnerUser(ownerUserId);
            List<FolderEntity> folders = tagId == null
                    ? folderRepository.findByOwnerUserAndParentFolderIsNullAndDeletedAtIsNullOrderByNameAsc(ownerUser)
                    : folderRepository.findTaggedRootFoldersByOwnerUser(ownerUser, tagId);
            return folders
                    .stream()
                    .map(folderResponseMapper::toSummary)
                    .toList();
        }

        Organization ownerOrganization = resolveOwnerOrganization(ownerOrganizationId);
        List<FolderEntity> folders = tagId == null
                ? folderRepository
                        .findByOwnerOrganizationAndParentFolderIsNullAndDeletedAtIsNullOrderByNameAsc(ownerOrganization)
                : folderRepository.findTaggedRootFoldersByOwnerOrganization(ownerOrganization, tagId);
        return folders
                .stream()
                .map(folderResponseMapper::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public FolderChildrenResponse listChildFolders(UUID folderId, UUID tagId, UUID actorUserId) {
        FolderEntity parentFolder = findAccessibleFolder(folderId, actorUserId, Permission.FOLDER_VIEW);
        tagService.assertCanUseTagForFolderListing(
                tagId,
                actorUserId,
                parentFolder.getOwnerUser() != null ? parentFolder.getOwnerUser().getId() : null,
                parentFolder.getOwnerOrganization() != null ? parentFolder.getOwnerOrganization().getId() : null,
                folderId);
        List<FolderEntity> folderEntities = tagId == null
                ? folderRepository.findByParentFolderAndDeletedAtIsNullOrderByNameAsc(parentFolder)
                : folderRepository.findTaggedChildFolders(parentFolder, tagId);
        List<FolderSummaryResponse> folders = folderEntities
                .stream()
                .map(folderResponseMapper::toSummary)
                .toList();
        return FolderChildrenResponse.builder().folders(folders).build();
    }

    @Transactional(readOnly = true)
    public FolderUploadTarget getUploadTarget(UUID folderId, UUID actorUserId) {
        FolderEntity folder = findAccessibleFolder(folderId, actorUserId, Permission.FOLDER_UPLOAD_FILE);
        return new FolderUploadTarget(
                folder.getOwnerUser() != null ? folder.getOwnerUser().getId() : null,
                folder.getOwnerOrganization() != null ? folder.getOwnerOrganization().getId() : null);
    }

    @Transactional(readOnly = true)
    public FolderEntity findAccessibleFolder(UUID folderId, UUID actorUserId, Permission permission) {
        accessControlService.assertCanAccessFolder(actorUserId, folderId, permission);
        return folderRepository.findByIdAndDeletedAtIsNull(folderId)
                .orElseThrow(() -> new ResourceNotFoundException("Folder not found: " + folderId));
    }

    public record FolderUploadTarget(UUID ownerUserId, UUID ownerOrganizationId) {
    }

    private String normalizeFolderName(String rawName) {
        if (rawName == null) {
            throw new IllegalArgumentException("Folder name is required");
        }

        String name = rawName.trim();
        if (name.isBlank()) {
            throw new IllegalArgumentException("Folder name must not be blank");
        }
        if (name.length() > MAX_FOLDER_NAME_LENGTH) {
            throw new IllegalArgumentException("Folder name must not exceed " + MAX_FOLDER_NAME_LENGTH + " characters");
        }
        if (name.contains("/") || name.contains("\\")) {
            throw new IllegalArgumentException("Folder name must not contain path separators");
        }
        if (name.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Folder name must not contain control characters");
        }
        return name;
    }

    private void validateExactlyOneOwner(UUID ownerUserId, UUID ownerOrganizationId) {
        if ((ownerUserId != null && ownerOrganizationId != null) || (ownerUserId == null && ownerOrganizationId == null)) {
            throw new IllegalArgumentException("Exactly one owner (user or organization) must be provided");
        }
    }

    private User resolveOwnerUser(UUID ownerUserId) {
        if (ownerUserId == null) {
            return null;
        }
        return userRepository.findById(ownerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + ownerUserId));
    }

    private Organization resolveOwnerOrganization(UUID ownerOrganizationId) {
        if (ownerOrganizationId == null) {
            return null;
        }
        return organizationRepository.findById(ownerOrganizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found: " + ownerOrganizationId));
    }

    private void validateParentOwnerRequest(
            FolderEntity parentFolder,
            UUID requestedOwnerUserId,
            UUID requestedOwnerOrganizationId) {
        if (requestedOwnerUserId == null && requestedOwnerOrganizationId == null) {
            return;
        }

        UUID parentOwnerUserId = parentFolder.getOwnerUser() != null ? parentFolder.getOwnerUser().getId() : null;
        UUID parentOwnerOrganizationId = parentFolder.getOwnerOrganization() != null
                ? parentFolder.getOwnerOrganization().getId()
                : null;
        if (!Objects.equals(parentOwnerUserId, requestedOwnerUserId)
                || !Objects.equals(parentOwnerOrganizationId, requestedOwnerOrganizationId)) {
            throw new IllegalArgumentException("Child folder owner context must match parent folder");
        }
    }

    private void rejectDuplicateActiveSibling(
            String name,
            User ownerUser,
            Organization ownerOrganization,
            FolderEntity parentFolder) {
        boolean exists;
        if (ownerUser != null && parentFolder != null) {
            exists = folderRepository.existsByNameIgnoreCaseAndOwnerUserAndParentFolderAndDeletedAtIsNull(
                    name,
                    ownerUser,
                    parentFolder);
        } else if (ownerUser != null) {
            exists = folderRepository.existsByNameIgnoreCaseAndOwnerUserAndParentFolderIsNullAndDeletedAtIsNull(
                    name,
                    ownerUser);
        } else if (parentFolder != null) {
            exists = folderRepository.existsByNameIgnoreCaseAndOwnerOrganizationAndParentFolderAndDeletedAtIsNull(
                    name,
                    ownerOrganization,
                    parentFolder);
        } else {
            exists = folderRepository.existsByNameIgnoreCaseAndOwnerOrganizationAndParentFolderIsNullAndDeletedAtIsNull(
                    name,
                    ownerOrganization);
        }

        if (exists) {
            throw new ConflictException("An active sibling folder with this name already exists");
        }
    }
}

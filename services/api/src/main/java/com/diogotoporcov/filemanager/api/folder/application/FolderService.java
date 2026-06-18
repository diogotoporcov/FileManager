package com.diogotoporcov.filemanager.api.folder.application;

import com.diogotoporcov.filemanager.api.auth.application.AccessControlService;
import com.diogotoporcov.filemanager.api.auth.domain.Permission;
import com.diogotoporcov.filemanager.api.exception.ConflictException;
import com.diogotoporcov.filemanager.api.exception.ResourceNotFoundException;
import com.diogotoporcov.filemanager.api.file.persistence.FileRepository;
import com.diogotoporcov.filemanager.api.folder.web.CreateFolderRequest;
import com.diogotoporcov.filemanager.api.folder.web.FolderChildrenResponse;
import com.diogotoporcov.filemanager.api.folder.web.FolderResponse;
import com.diogotoporcov.filemanager.api.folder.web.FolderResponseMapper;
import com.diogotoporcov.filemanager.api.folder.web.FolderSummaryResponse;
import com.diogotoporcov.filemanager.api.folder.web.UpdateFolderRequest;
import com.diogotoporcov.filemanager.api.folder.domain.FolderClosureEntity;
import com.diogotoporcov.filemanager.api.folder.domain.FolderClosureId;
import com.diogotoporcov.filemanager.api.folder.domain.FolderEntity;
import com.diogotoporcov.filemanager.api.folder.persistence.FolderClosureRepository;
import com.diogotoporcov.filemanager.api.folder.persistence.FolderRepository;
import com.diogotoporcov.filemanager.api.identity.domain.User;
import com.diogotoporcov.filemanager.api.identity.persistence.UserRepository;
import com.diogotoporcov.filemanager.api.tag.application.TagService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FolderService {
    private static final int MAX_FOLDER_NAME_LENGTH = 255;

    private final FolderRepository folderRepository;
    private final FolderClosureRepository folderClosureRepository;
    private final FileRepository fileRepository;
    private final UserRepository userRepository;
    private final AccessControlService accessControlService;
    private final FolderResponseMapper folderResponseMapper;
    private final TagService tagService;

    @Transactional
    public FolderResponse createFolder(CreateFolderRequest request, UUID actorUserId) {
        Objects.requireNonNull(request, "request must not be null");
        String name = normalizeFolderName(request.getName());
        User ownerUser = findUser(actorUserId);

        FolderEntity parentFolder = null;
        if (request.getParentFolderId() != null) {
            parentFolder = folderRepository.findByIdAndDeletedAtIsNull(request.getParentFolderId())
                    .orElseThrow(() -> new ResourceNotFoundException("Folder not found: " + request.getParentFolderId()));
            accessControlService.assertCanAccessFolder(actorUserId, parentFolder.getId(), Permission.FOLDER_CREATE);
        } else {
            accessControlService.assertCanCreateFolderForOwner(actorUserId, actorUserId);
        }

        rejectDuplicateActiveSibling(name, ownerUser, parentFolder);

        FolderEntity folder = FolderEntity.builder()
                .name(name)
                .parentFolder(parentFolder)
                .ownerUser(ownerUser)
                .createdByUser(ownerUser)
                .build();

        FolderEntity savedFolder = folderRepository.save(folder);
        createClosureRows(savedFolder, parentFolder);

        return folderResponseMapper.toResponse(savedFolder);
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
            rejectDuplicateActiveSibling(name, folder.getOwnerUser(), folder.getParentFolder());
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
    public List<FolderSummaryResponse> listRootFolders(UUID tagId, UUID actorUserId) {
        findUser(actorUserId);
        tagService.assertCanUseTagForFolderListing(tagId, actorUserId, null);
        List<FolderEntity> folders = tagId == null
                ? folderRepository.findVisibleRootFolders(actorUserId)
                : folderRepository.findVisibleTaggedRootFolders(actorUserId, tagId);

        return folders
                .stream()
                .map(folderResponseMapper::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public FolderChildrenResponse listChildFolders(UUID folderId, UUID tagId, UUID actorUserId) {
        FolderEntity parentFolder = findAccessibleFolder(folderId, actorUserId, Permission.FOLDER_VIEW);
        tagService.assertCanUseTagForFolderListing(tagId, actorUserId, folderId);
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
    public FolderEntity findAccessibleFolder(UUID folderId, UUID actorUserId, Permission permission) {
        accessControlService.assertCanAccessFolder(actorUserId, folderId, permission);

        return folderRepository.findByIdAndDeletedAtIsNull(folderId)
                .orElseThrow(() -> new ResourceNotFoundException("Folder not found: " + folderId));
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

    private User findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }

    private void rejectDuplicateActiveSibling(String name, User ownerUser, FolderEntity parentFolder) {
        boolean exists = parentFolder != null
                ? folderRepository.existsByNameIgnoreCaseAndParentFolderAndDeletedAtIsNull(
                        name,
                        parentFolder)
                : folderRepository.existsByNameIgnoreCaseAndOwnerUserAndParentFolderIsNullAndDeletedAtIsNull(
                        name,
                        ownerUser);

        if (exists) {
            throw new ConflictException("An active sibling folder with this name already exists");
        }
    }

    private void createClosureRows(FolderEntity folder, FolderEntity parentFolder) {
        folderClosureRepository.save(FolderClosureEntity.builder()
                .id(new FolderClosureId(folder.getId(), folder.getId()))
                .ancestorFolder(folder)
                .descendantFolder(folder)
                .depth(0)
                .build());

        if (parentFolder == null) {
            return;
        }

        List<FolderClosureEntity> parentClosureRows = folderClosureRepository.findByDescendantFolderOrderByDepthAsc(parentFolder);
        List<FolderClosureEntity> ancestorRows = parentClosureRows.stream()
                .map(parentClosure -> FolderClosureEntity.builder()
                        .id(new FolderClosureId(parentClosure.getAncestorFolder().getId(), folder.getId()))
                        .ancestorFolder(parentClosure.getAncestorFolder())
                        .descendantFolder(folder)
                        .depth(parentClosure.getDepth() + 1)
                        .build())
                .toList();
        folderClosureRepository.saveAll(ancestorRows);
    }
}

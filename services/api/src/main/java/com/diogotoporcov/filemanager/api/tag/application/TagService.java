package com.diogotoporcov.filemanager.api.tag.application;

import com.diogotoporcov.filemanager.api.auth.application.AccessControlService;
import com.diogotoporcov.filemanager.api.auth.domain.Permission;
import com.diogotoporcov.filemanager.api.exception.AccessDeniedException;
import com.diogotoporcov.filemanager.api.exception.ResourceNotFoundException;
import com.diogotoporcov.filemanager.api.file.domain.FileEntity;
import com.diogotoporcov.filemanager.api.file.persistence.FileRepository;
import com.diogotoporcov.filemanager.api.folder.domain.FolderEntity;
import com.diogotoporcov.filemanager.api.folder.persistence.FolderRepository;
import com.diogotoporcov.filemanager.api.identity.domain.User;
import com.diogotoporcov.filemanager.api.identity.persistence.UserRepository;
import com.diogotoporcov.filemanager.api.tag.web.CreateTagRequest;
import com.diogotoporcov.filemanager.api.tag.web.TagResponse;
import com.diogotoporcov.filemanager.api.tag.web.TagResponseMapper;
import com.diogotoporcov.filemanager.api.tag.domain.FileTagEntity;
import com.diogotoporcov.filemanager.api.tag.domain.FileTagId;
import com.diogotoporcov.filemanager.api.tag.domain.FolderTagEntity;
import com.diogotoporcov.filemanager.api.tag.domain.FolderTagId;
import com.diogotoporcov.filemanager.api.tag.domain.TagEntity;
import com.diogotoporcov.filemanager.api.tag.domain.TagScopeType;
import com.diogotoporcov.filemanager.api.tag.persistence.FileTagRepository;
import com.diogotoporcov.filemanager.api.tag.persistence.FolderTagRepository;
import com.diogotoporcov.filemanager.api.tag.persistence.TagRepository;
import java.util.function.Supplier;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class TagService {
    private static final int MAX_TAG_NAME_LENGTH = 100;
    private static final int MAX_RAW_TAG_NAME_LENGTH = 500;
    private static final int DEFAULT_TAG_LIMIT = 50;
    private static final int MAX_TAG_LIMIT = 100;

    private final TagRepository tagRepository;
    private final FileTagRepository fileTagRepository;
    private final FolderTagRepository folderTagRepository;
    private final FileRepository fileRepository;
    private final FolderRepository folderRepository;
    private final UserRepository userRepository;
    private final AccessControlService accessControlService;
    private final TagResponseMapper tagResponseMapper;
    private final TransactionTemplate transactionTemplate;

    public TagResponse createOrGetTag(CreateTagRequest request, UUID actorUserId) {
        Objects.requireNonNull(request, "request must not be null");
        NormalizedTagName tagName = normalizeTagName(request.getName());
        assertCanCreateTagInRequestedScope(request, actorUserId);
        Optional<TagResponse> existing = executeInTransaction(() -> findExistingTag(request, actorUserId, tagName.normalizedName())
                .map(tagResponseMapper::toResponse));
        if (existing.isPresent()) {
            return existing.get();
        }

        try {
            return executeInTransaction(() -> {
                TagEntity unsaved = buildTag(request, actorUserId, tagName);

                return tagResponseMapper.toResponse(tagRepository.saveAndFlush(unsaved));
            });
        } catch (DataIntegrityViolationException ex) {
            Optional<TagResponse> concurrentlyCreated = executeInTransaction(() -> findExistingTag(
                            request,
                            actorUserId,
                            tagName.normalizedName())
                    .map(tagResponseMapper::toResponse));

            return concurrentlyCreated.orElseThrow(() -> ex);
        }
    }

    private <T> T executeInTransaction(Supplier<T> action) {
        return transactionTemplate.execute(status -> {
            if (status.isRollbackOnly()) {
                throw new IllegalStateException("Transaction is marked rollback-only");
            }

            return action.get();
        });
    }

    @Transactional(readOnly = true)
    public List<TagResponse> listTags(
            UUID scopeFolderId,
            String query,
            Integer requestedLimit,
            UUID actorUserId) {
        int limit = normalizeLimit(requestedLimit);
        String normalizedQuery = normalizeOptionalQuery(query);

        if (scopeFolderId != null) {
            accessControlService.assertCanAccessFolder(actorUserId, scopeFolderId, Permission.FOLDER_VIEW);
            folderRepository.findByIdAndDeletedAtIsNull(scopeFolderId)
                    .orElseThrow(() -> new ResourceNotFoundException("Folder not found: " + scopeFolderId));

            return tagRepository.listFolderTags(scopeFolderId, normalizedQuery, PageRequest.of(0, limit))
                    .stream()
                    .map(tagResponseMapper::toResponse)
                    .toList();
        }

        accessControlService.assertCanViewOwner(actorUserId, actorUserId);
        findUser(actorUserId);

        return tagRepository.listOwnerUserTags(actorUserId, normalizedQuery, PageRequest.of(0, limit))
                .stream()
                .map(tagResponseMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TagResponse> listFileTags(UUID fileId, UUID actorUserId) {
        accessControlService.assertCanAccessFile(actorUserId, fileId, Permission.FILE_VIEW);

        return fileTagRepository.findActiveTagsByFileId(fileId)
                .stream()
                .map(tagResponseMapper::toResponse)
                .toList();
    }

    @Transactional
    public List<TagResponse> applyTagToFile(UUID fileId, UUID tagId, UUID actorUserId) {
        accessControlService.assertCanAccessFile(actorUserId, fileId, Permission.FILE_MODIFY);
        FileEntity file = fileRepository.findByIdAndDeletedAtIsNull(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found: " + fileId));
        TagEntity tag = findActiveTag(tagId);
        User taggedBy = findUser(actorUserId);
        assertTagApplicableToFile(tag, file, actorUserId);

        FileTagId assignmentId = new FileTagId(fileId, tagId);
        if (!fileTagRepository.existsById(assignmentId)) {
            fileTagRepository.save(FileTagEntity.builder()
                    .id(assignmentId)
                    .file(file)
                    .tag(tag)
                    .taggedByUser(taggedBy)
                    .build());
        }

        return listFileTags(fileId, actorUserId);
    }

    @Transactional
    public List<TagResponse> removeTagFromFile(UUID fileId, UUID tagId, UUID actorUserId) {
        accessControlService.assertCanAccessFile(actorUserId, fileId, Permission.FILE_MODIFY);
        FileTagId assignmentId = new FileTagId(fileId, tagId);
        if (fileTagRepository.existsById(assignmentId)) {
            fileTagRepository.deleteById(assignmentId);
        }

        return listFileTags(fileId, actorUserId);
    }

    @Transactional(readOnly = true)
    public List<TagResponse> listFolderTags(UUID folderId, UUID actorUserId) {
        accessControlService.assertCanAccessFolder(actorUserId, folderId, Permission.FOLDER_VIEW);

        return folderTagRepository.findActiveTagsByFolderId(folderId)
                .stream()
                .map(tagResponseMapper::toResponse)
                .toList();
    }

    @Transactional
    public List<TagResponse> applyTagToFolder(UUID folderId, UUID tagId, UUID actorUserId) {
        accessControlService.assertCanAccessFolder(actorUserId, folderId, Permission.FOLDER_RENAME);
        FolderEntity folder = folderRepository.findByIdAndDeletedAtIsNull(folderId)
                .orElseThrow(() -> new ResourceNotFoundException("Folder not found: " + folderId));
        TagEntity tag = findActiveTag(tagId);
        User taggedBy = findUser(actorUserId);
        assertTagApplicableToFolder(tag, folder, actorUserId);

        FolderTagId assignmentId = new FolderTagId(folderId, tagId);
        if (!folderTagRepository.existsById(assignmentId)) {
            folderTagRepository.save(FolderTagEntity.builder()
                    .id(assignmentId)
                    .folder(folder)
                    .tag(tag)
                    .taggedByUser(taggedBy)
                    .build());
        }

        return listFolderTags(folderId, actorUserId);
    }

    @Transactional
    public List<TagResponse> removeTagFromFolder(UUID folderId, UUID tagId, UUID actorUserId) {
        accessControlService.assertCanAccessFolder(actorUserId, folderId, Permission.FOLDER_RENAME);
        FolderTagId assignmentId = new FolderTagId(folderId, tagId);
        if (folderTagRepository.existsById(assignmentId)) {
            folderTagRepository.deleteById(assignmentId);
        }

        return listFolderTags(folderId, actorUserId);
    }

    @Transactional(readOnly = true)
    public void assertCanUseTagForFileSearch(UUID tagId, UUID actorUserId, UUID folderId) {
        if (tagId == null) {
            return;
        }

        TagEntity tag = findActiveTag(tagId);
        assertCanViewTag(actorUserId, tag);
        if (tag.getScopeType() == TagScopeType.OWNER) {
            assertTagOwnedBy(tag, actorUserId, "Tag owner context must match file search context");

            return;
        }

        if (folderId == null) {
            throw new AccessDeniedException("Folder-scoped tag requires an explicit matching folder search context.");
        }

        if (!folderId.equals(tag.getScopeFolder().getId())) {
            throw new AccessDeniedException("Folder-scoped tag cannot be used outside its folder scope.");
        }
    }

    @Transactional(readOnly = true)
    public void assertCanUseTagForFolderListing(UUID tagId, UUID actorUserId, UUID parentFolderId) {
        if (tagId == null) {
            return;
        }
        TagEntity tag = findActiveTag(tagId);
        assertCanViewTag(actorUserId, tag);
        if (tag.getScopeType() == TagScopeType.OWNER) {
            assertTagOwnedBy(tag, actorUserId, "Tag owner context must match folder listing context");

            return;
        }

        if (parentFolderId == null) {
            throw new AccessDeniedException("Folder-scoped tag cannot be used for root folder listing.");
        }

        if (!parentFolderId.equals(tag.getScopeFolder().getId())) {
            throw new AccessDeniedException("Folder-scoped tag cannot be used outside its folder scope.");
        }
    }

    private TagEntity buildTag(CreateTagRequest request, UUID actorUserId, NormalizedTagName tagName) {
        User createdByUser = findUser(actorUserId);
        if (request.getScopeType() == TagScopeType.OWNER) {
            if (request.getScopeFolderId() != null) {
                throw new IllegalArgumentException("OWNER-scoped tags must not include scopeFolderId");
            }

            return TagEntity.builder()
                    .displayName(tagName.displayName())
                    .normalizedName(tagName.normalizedName())
                    .scopeType(TagScopeType.OWNER)
                    .ownerUser(createdByUser)
                    .createdByUser(createdByUser)
                    .build();
        }

        if (request.getScopeType() != TagScopeType.FOLDER) {
            throw new IllegalArgumentException("Unsupported tag scope type");
        }

        if (request.getScopeFolderId() == null) {
            throw new IllegalArgumentException("FOLDER-scoped tags require scopeFolderId");
        }

        FolderEntity folder = folderRepository.findByIdAndDeletedAtIsNull(request.getScopeFolderId())
                .orElseThrow(() -> new ResourceNotFoundException("Folder not found: " + request.getScopeFolderId()));

        return TagEntity.builder()
                .displayName(tagName.displayName())
                .normalizedName(tagName.normalizedName())
                .scopeType(TagScopeType.FOLDER)
                .scopeFolder(folder)
                .ownerUser(folder.getOwnerUser())
                .createdByUser(createdByUser)
                .build();
    }

    private void assertCanCreateTagInRequestedScope(CreateTagRequest request, UUID actorUserId) {
        if (request.getScopeType() == TagScopeType.OWNER) {
            if (request.getScopeFolderId() != null) {
                throw new IllegalArgumentException("OWNER-scoped tags must not include scopeFolderId");
            }

            accessControlService.assertCanUploadToOwner(actorUserId, actorUserId);

            return;
        }

        if (request.getScopeType() == TagScopeType.FOLDER) {
            if (request.getScopeFolderId() == null) {
                throw new IllegalArgumentException("FOLDER-scoped tags require scopeFolderId");
            }
            accessControlService.assertCanAccessFolder(actorUserId, request.getScopeFolderId(), Permission.FOLDER_UPLOAD_FILE);

            return;
        }

        throw new IllegalArgumentException("Unsupported tag scope type");
    }

    private Optional<TagEntity> findExistingTag(CreateTagRequest request, UUID actorUserId, String normalizedName) {
        if (request.getScopeType() == TagScopeType.FOLDER) {
            if (request.getScopeFolderId() == null) {
                return Optional.empty();
            }

            return tagRepository.findByScopeFolderIdAndNormalizedNameAndDeletedAtIsNull(
                    request.getScopeFolderId(),
                    normalizedName);
        }

        if (request.getScopeType() == TagScopeType.OWNER) {
            return tagRepository.findByOwnerUserIdAndScopeTypeAndNormalizedNameAndDeletedAtIsNull(
                    actorUserId,
                    TagScopeType.OWNER,
                    normalizedName);
        }

        return Optional.empty();
    }

    private void assertTagApplicableToFile(TagEntity tag, FileEntity file, UUID actorUserId) {
        assertCanViewTag(actorUserId, tag);
        if (tag.getScopeType() == TagScopeType.OWNER) {
            assertTagOwnedBy(tag, fileOwnerUserId(file), "Tag cannot be applied across owner contexts.");

            return;
        }

        UUID fileFolderId = file.getFolder() != null ? file.getFolder().getId() : null;
        if (!Objects.equals(tag.getScopeFolder().getId(), fileFolderId)) {
            throw new AccessDeniedException("Folder-scoped tag cannot be applied to a file outside its folder scope.");
        }
    }

    private void assertTagApplicableToFolder(TagEntity tag, FolderEntity folder, UUID actorUserId) {
        assertCanViewTag(actorUserId, tag);
        if (tag.getScopeType() == TagScopeType.OWNER) {
            assertTagOwnedBy(tag, folderOwnerUserId(folder), "Tag cannot be applied across owner contexts.");

            return;
        }

        UUID scopeFolderId = tag.getScopeFolder().getId();
        UUID parentFolderId = folder.getParentFolder() != null ? folder.getParentFolder().getId() : null;
        if (!scopeFolderId.equals(folder.getId()) && !scopeFolderId.equals(parentFolderId)) {
            throw new AccessDeniedException("Folder-scoped tag cannot be applied to an unrelated folder.");
        }
    }

    private void assertCanViewTag(UUID actorUserId, TagEntity tag) {
        if (tag.getScopeType() == TagScopeType.FOLDER) {
            accessControlService.assertCanAccessFolder(actorUserId, tag.getScopeFolder().getId(), Permission.FOLDER_VIEW);

            return;
        }

        accessControlService.assertCanViewOwner(actorUserId, tag.getOwnerUser().getId());
    }

    private void assertTagOwnedBy(TagEntity tag, UUID ownerUserId, String message) {
        if (!Objects.equals(tag.getOwnerUser() != null ? tag.getOwnerUser().getId() : null, ownerUserId)) {
            throw new AccessDeniedException(message);
        }
    }

    private NormalizedTagName normalizeTagName(String rawName) {
        if (rawName == null) {
            throw new IllegalArgumentException("Tag name is required");
        }

        if (rawName.length() > MAX_RAW_TAG_NAME_LENGTH) {
            throw new IllegalArgumentException("Tag name is too long");
        }

        if (rawName.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Tag name must not contain control characters");
        }

        String displayName = rawName.trim().replaceAll(" +", " ");
        String normalizedName = displayName.toLowerCase(Locale.ROOT);

        if (normalizedName.isBlank()) {
            throw new IllegalArgumentException("Tag name must not be blank");
        }

        if (displayName.length() > MAX_TAG_NAME_LENGTH) {
            throw new IllegalArgumentException("Tag name must not exceed " + MAX_TAG_NAME_LENGTH + " characters");
        }

        return new NormalizedTagName(displayName, normalizedName);
    }

    private String normalizeOptionalQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return null;
        }

        return normalizeTagName(rawQuery).normalizedName();
    }

    private int normalizeLimit(Integer requestedLimit) {
        if (requestedLimit == null) {
            return DEFAULT_TAG_LIMIT;
        }

        if (requestedLimit < 1) {
            throw new IllegalArgumentException("Tag list limit must be positive");
        }

        if (requestedLimit > MAX_TAG_LIMIT) {
            throw new IllegalArgumentException("Tag list limit must not exceed " + MAX_TAG_LIMIT);
        }

        return requestedLimit;
    }

    private TagEntity findActiveTag(UUID tagId) {
        return tagRepository.findByIdAndDeletedAtIsNull(tagId)
                .orElseThrow(() -> new ResourceNotFoundException("Tag not found: " + tagId));
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }

    private UUID fileOwnerUserId(FileEntity file) {
        return file.getOwnerUser() != null ? file.getOwnerUser().getId() : null;
    }

    private UUID folderOwnerUserId(FolderEntity folder) {
        return folder.getOwnerUser() != null ? folder.getOwnerUser().getId() : null;
    }

    private record NormalizedTagName(String displayName, String normalizedName) {
    }
}

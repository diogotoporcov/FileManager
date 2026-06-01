package com.filemanager.api.service;

import com.filemanager.api.auth.AccessControlService;
import com.filemanager.api.auth.Permission;
import com.filemanager.api.dto.CreateTagRequest;
import com.filemanager.api.dto.TagResponse;
import com.filemanager.api.entity.FileEntity;
import com.filemanager.api.entity.FileTagEntity;
import com.filemanager.api.entity.FileTagId;
import com.filemanager.api.entity.FolderEntity;
import com.filemanager.api.entity.FolderTagEntity;
import com.filemanager.api.entity.FolderTagId;
import com.filemanager.api.entity.Organization;
import com.filemanager.api.entity.TagEntity;
import com.filemanager.api.entity.TagScopeType;
import com.filemanager.api.entity.User;
import com.filemanager.api.exception.AccessDeniedException;
import com.filemanager.api.exception.ResourceNotFoundException;
import com.filemanager.api.mapper.TagResponseMapper;
import com.filemanager.api.repository.FileRepository;
import com.filemanager.api.repository.FileTagRepository;
import com.filemanager.api.repository.FolderRepository;
import com.filemanager.api.repository.FolderTagRepository;
import com.filemanager.api.repository.OrganizationRepository;
import com.filemanager.api.repository.TagRepository;
import com.filemanager.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

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
    private final OrganizationRepository organizationRepository;
    private final AccessControlService accessControlService;
    private final TagResponseMapper tagResponseMapper;
    private final TransactionTemplate transactionTemplate;

    public TagResponse createOrGetTag(CreateTagRequest request, UUID actorUserId) {
        Objects.requireNonNull(request, "request must not be null");
        NormalizedTagName tagName = normalizeTagName(request.getName());
        assertCanCreateTagInRequestedScope(request, actorUserId);
        Optional<TagResponse> existing = executeInTransaction(() -> findExistingTag(request, tagName.normalizedName())
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
            UUID ownerUserId,
            UUID ownerOrganizationId,
            UUID scopeFolderId,
            String query,
            Integer requestedLimit,
            UUID actorUserId) {
        int limit = normalizeLimit(requestedLimit);
        String normalizedQuery = normalizeOptionalQuery(query);

        if (scopeFolderId != null) {
            if (ownerUserId != null || ownerOrganizationId != null) {
                throw new IllegalArgumentException("Specify either scopeFolderId or owner context, not both");
            }

            accessControlService.assertCanAccessFolder(actorUserId, scopeFolderId, Permission.FOLDER_VIEW);
            folderRepository.findByIdAndDeletedAtIsNull(scopeFolderId)
                    .orElseThrow(() -> new ResourceNotFoundException("Folder not found: " + scopeFolderId));
            return tagRepository.listFolderTags(scopeFolderId, normalizedQuery, PageRequest.of(0, limit))
                    .stream()
                    .map(tagResponseMapper::toResponse)
                    .toList();
        }

        validateExactlyOneOwner(ownerUserId, ownerOrganizationId);
        accessControlService.assertCanViewContext(actorUserId, ownerUserId, ownerOrganizationId);
        if (ownerUserId != null) {
            userRepository.findById(ownerUserId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found: " + ownerUserId));

            return tagRepository.listOwnerUserTags(ownerUserId, normalizedQuery, PageRequest.of(0, limit))
                    .stream()
                    .map(tagResponseMapper::toResponse)
                    .toList();
        }

        organizationRepository.findById(ownerOrganizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found: " + ownerOrganizationId));

        return tagRepository.listOwnerOrganizationTags(ownerOrganizationId, normalizedQuery, PageRequest.of(0, limit))
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
    public void assertCanUseTagForFileSearch(
            UUID tagId,
            UUID actorUserId,
            UUID ownerUserId,
            UUID ownerOrganizationId,
            UUID folderId) {
        if (tagId == null) {
            return;
        }

        TagEntity tag = findActiveTag(tagId);
        assertCanViewTag(actorUserId, tag);
        assertOwnerContextMatches(tag, ownerUserId, ownerOrganizationId, "Tag owner context must match file search context");
        if (tag.getScopeType() == TagScopeType.FOLDER) {
            if (folderId == null) {
                throw new AccessDeniedException("Folder-scoped tag requires an explicit matching folder search context.");
            }

            if (!folderId.equals(tag.getScopeFolder().getId())) {
                throw new AccessDeniedException("Folder-scoped tag cannot be used outside its folder scope.");
            }
        }
    }

    @Transactional(readOnly = true)
    public void assertCanUseTagForFolderListing(
            UUID tagId,
            UUID actorUserId,
            UUID ownerUserId,
            UUID ownerOrganizationId,
            UUID parentFolderId) {
        if (tagId == null) {
            return;
        }
        TagEntity tag = findActiveTag(tagId);
        assertCanViewTag(actorUserId, tag);
        assertOwnerContextMatches(tag, ownerUserId, ownerOrganizationId, "Tag owner context must match folder listing context");
        if (tag.getScopeType() == TagScopeType.FOLDER) {
            if (parentFolderId == null) {
                throw new AccessDeniedException("Folder-scoped tag cannot be used for root folder listing.");
            }

            if (!parentFolderId.equals(tag.getScopeFolder().getId())) {
                throw new AccessDeniedException("Folder-scoped tag cannot be used outside its folder scope.");
            }
        }
    }

    private TagEntity buildTag(CreateTagRequest request, UUID actorUserId, NormalizedTagName tagName) {
        User createdByUser = findUser(actorUserId);
        if (request.getScopeType() == TagScopeType.OWNER) {
            validateExactlyOneOwner(request.getOwnerUserId(), request.getOwnerOrganizationId());
            if (request.getScopeFolderId() != null) {
                throw new IllegalArgumentException("OWNER-scoped tags must not include scopeFolderId");
            }

            return TagEntity.builder()
                    .displayName(tagName.displayName())
                    .normalizedName(tagName.normalizedName())
                    .scopeType(TagScopeType.OWNER)
                    .ownerUser(resolveOwnerUser(request.getOwnerUserId()))
                    .ownerOrganization(resolveOwnerOrganization(request.getOwnerOrganizationId()))
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
                .ownerOrganization(folder.getOwnerOrganization())
                .createdByUser(createdByUser)
                .build();
    }

    private void assertCanCreateTagInRequestedScope(CreateTagRequest request, UUID actorUserId) {
        if (request.getScopeType() == TagScopeType.OWNER) {
            validateExactlyOneOwner(request.getOwnerUserId(), request.getOwnerOrganizationId());
            if (request.getScopeFolderId() != null) {
                throw new IllegalArgumentException("OWNER-scoped tags must not include scopeFolderId");
            }

            accessControlService.assertCanUploadToContext(
                    actorUserId,
                    request.getOwnerUserId(),
                    request.getOwnerOrganizationId());
            return;
        }

        if (request.getScopeType() == TagScopeType.FOLDER) {
            if (request.getScopeFolderId() == null) {
                throw new IllegalArgumentException("FOLDER-scoped tags require scopeFolderId");
            }

            if (request.getOwnerUserId() != null || request.getOwnerOrganizationId() != null) {
                throw new IllegalArgumentException("FOLDER-scoped tags derive owner context from scopeFolderId");
            }
            accessControlService.assertCanAccessFolder(actorUserId, request.getScopeFolderId(), Permission.FOLDER_UPLOAD_FILE);
            return;
        }

        throw new IllegalArgumentException("Unsupported tag scope type");
    }

    private Optional<TagEntity> findExistingTag(CreateTagRequest request, String normalizedName) {
        if (request.getScopeType() == TagScopeType.FOLDER) {
            if (request.getScopeFolderId() == null) {
                return Optional.empty();
            }

            return tagRepository.findByScopeFolderIdAndNormalizedNameAndDeletedAtIsNull(
                    request.getScopeFolderId(),
                    normalizedName);
        }

        if (request.getScopeType() == TagScopeType.OWNER && request.getOwnerUserId() != null) {
            return tagRepository.findByOwnerUserIdAndScopeTypeAndNormalizedNameAndDeletedAtIsNull(
                    request.getOwnerUserId(),
                    TagScopeType.OWNER,
                    normalizedName);
        }

        if (request.getScopeType() == TagScopeType.OWNER && request.getOwnerOrganizationId() != null) {
            return tagRepository.findByOwnerOrganizationIdAndScopeTypeAndNormalizedNameAndDeletedAtIsNull(
                    request.getOwnerOrganizationId(),
                    TagScopeType.OWNER,
                    normalizedName);
        }

        return Optional.empty();
    }

    private void assertTagApplicableToFile(TagEntity tag, FileEntity file, UUID actorUserId) {
        assertCanViewTag(actorUserId, tag);
        assertOwnerContextMatches(tag, fileOwnerUserId(file), fileOwnerOrganizationId(file), "Tag cannot be applied across owner contexts.");
        if (tag.getScopeType() == TagScopeType.FOLDER) {
            UUID fileFolderId = file.getFolder() != null ? file.getFolder().getId() : null;
            if (!Objects.equals(tag.getScopeFolder().getId(), fileFolderId)) {
                throw new AccessDeniedException("Folder-scoped tag cannot be applied to a file outside its folder scope.");
            }
        }
    }

    private void assertTagApplicableToFolder(TagEntity tag, FolderEntity folder, UUID actorUserId) {
        assertCanViewTag(actorUserId, tag);
        assertOwnerContextMatches(tag, folderOwnerUserId(folder), folderOwnerOrganizationId(folder), "Tag cannot be applied across owner contexts.");
        if (tag.getScopeType() == TagScopeType.FOLDER) {
            UUID scopeFolderId = tag.getScopeFolder().getId();
            UUID parentFolderId = folder.getParentFolder() != null ? folder.getParentFolder().getId() : null;

            if (!scopeFolderId.equals(folder.getId()) && !scopeFolderId.equals(parentFolderId)) {
                throw new AccessDeniedException("Folder-scoped tag cannot be applied to an unrelated folder.");
            }
        }
    }

    private void assertCanViewTag(UUID actorUserId, TagEntity tag) {
        if (tag.getScopeType() == TagScopeType.FOLDER) {
            accessControlService.assertCanAccessFolder(actorUserId, tag.getScopeFolder().getId(), Permission.FOLDER_VIEW);
            return;
        }

        accessControlService.assertCanViewContext(
                actorUserId,
                tag.getOwnerUser() != null ? tag.getOwnerUser().getId() : null,
                tag.getOwnerOrganization() != null ? tag.getOwnerOrganization().getId() : null);
    }

    private void assertOwnerContextMatches(
            TagEntity tag,
            UUID ownerUserId,
            UUID ownerOrganizationId,
            String message) {
        if (!Objects.equals(tag.getOwnerUser() != null ? tag.getOwnerUser().getId() : null, ownerUserId)
                || !Objects.equals(tag.getOwnerOrganization() != null ? tag.getOwnerOrganization().getId() : null,
                ownerOrganizationId)) {
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

    private void validateExactlyOneOwner(UUID ownerUserId, UUID ownerOrganizationId) {
        if ((ownerUserId != null && ownerOrganizationId != null) || (ownerUserId == null && ownerOrganizationId == null)) {
            throw new IllegalArgumentException("Exactly one owner (user or organization) must be provided");
        }
    }

    private UUID fileOwnerUserId(FileEntity file) {
        return file.getOwnerUser() != null ? file.getOwnerUser().getId() : null;
    }

    private UUID fileOwnerOrganizationId(FileEntity file) {
        return file.getOwnerOrganization() != null ? file.getOwnerOrganization().getId() : null;
    }

    private UUID folderOwnerUserId(FolderEntity folder) {
        return folder.getOwnerUser() != null ? folder.getOwnerUser().getId() : null;
    }

    private UUID folderOwnerOrganizationId(FolderEntity folder) {
        return folder.getOwnerOrganization() != null ? folder.getOwnerOrganization().getId() : null;
    }

    private record NormalizedTagName(String displayName, String normalizedName) {
    }
}

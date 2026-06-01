package com.filemanager.api.service;

import com.filemanager.api.auth.AccessControlService;
import com.filemanager.api.auth.Permission;
import com.filemanager.api.config.AppProperties;
import com.filemanager.api.dto.CursorPageResponse;
import com.filemanager.api.dto.FileResponse;
import com.filemanager.api.event.FileProcessingRequestedEvent;
import com.filemanager.api.entity.FileEntity;
import com.filemanager.api.entity.FolderEntity;
import com.filemanager.api.entity.Organization;
import com.filemanager.api.entity.ProcessingJob;
import com.filemanager.api.entity.User;
import com.filemanager.api.exception.AccessDeniedException;
import com.filemanager.api.exception.ResourceNotFoundException;
import com.filemanager.api.mapper.FileResponseMapper;
import com.filemanager.api.port.ApplicationMetricsPort;
import com.filemanager.api.port.ObjectStoragePort;
import com.filemanager.api.port.StoreObjectRequest;
import com.filemanager.api.port.StoreObjectResponse;
import com.filemanager.api.repository.FileRepository;
import com.filemanager.api.repository.FolderRepository;
import com.filemanager.api.search.file.FileSearchCriteria;
import com.filemanager.api.search.file.FileSearchCriteriaMapper;
import com.filemanager.api.search.file.FileSearchCursor;
import com.filemanager.api.search.file.FileSearchQuery;
import com.filemanager.api.search.file.FileSearchSpecificationBuilder;
import com.filemanager.api.search.file.FileSortMapper;
import com.filemanager.api.repository.OrganizationRepository;
import com.filemanager.api.repository.ProcessingJobRepository;
import com.filemanager.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileService {

    private final FileRepository fileRepository;
    private final FolderRepository folderRepository;
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final ProcessingJobRepository processingJobRepository;
    private final ProcessingJobPlanner processingJobPlanner;
    private final ObjectStoragePort objectStoragePort;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final AccessControlService accessControlService;
    private final ApplicationMetricsPort applicationMetricsPort;
    private final AppProperties appProperties;
    private final FileSearchCriteriaMapper fileSearchCriteriaMapper;
    private final FileSearchSpecificationBuilder fileSearchSpecificationBuilder;
    private final FileSortMapper fileSortMapper;
    private final FileResponseMapper fileResponseMapper;

    @Transactional
    public FileEntity uploadFile(
            String fileName,
            String contentType,
            long size,
            InputStream content,
            UUID ownerUserId,
            UUID ownerOrganizationId,
            UUID actorUserId) {
        return uploadFile(fileName, contentType, size, content, ownerUserId, ownerOrganizationId, null, actorUserId);
    }

    @Transactional
    public FileEntity uploadFile(
            String fileName,
            String contentType,
            long size,
            InputStream content,
            UUID ownerUserId,
            UUID ownerOrganizationId,
            UUID folderId,
            UUID actorUserId) {
        validateExactlyOneOwner(ownerUserId, ownerOrganizationId);

        FolderEntity folder = null;
        if (folderId != null) {
            accessControlService.assertCanAccessFolder(actorUserId, folderId, Permission.FOLDER_UPLOAD_FILE);
            folder = folderRepository.findByIdAndDeletedAtIsNull(folderId)
                    .orElseThrow(() -> new ResourceNotFoundException("Folder not found: " + folderId));
            validateFolderOwnerContext(folder, ownerUserId, ownerOrganizationId);
        } else {
            accessControlService.assertCanUploadToContext(actorUserId, ownerUserId, ownerOrganizationId);
        }

        User ownerUser = null;
        if (ownerUserId != null) {
            ownerUser = userRepository.findById(ownerUserId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found: " + ownerUserId));
            enforceUserQuota(ownerUser, size);
        }

        Organization ownerOrganization = null;
        if (ownerOrganizationId != null) {
            ownerOrganization = organizationRepository.findById(ownerOrganizationId)
                    .orElseThrow(() -> new ResourceNotFoundException("Organization not found: " + ownerOrganizationId));
            enforceOrganizationQuota(ownerOrganization, size);
        }

        User createdByUser = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + actorUserId));

        String effectiveContentType = (contentType == null || contentType.isBlank()) ? "application/octet-stream" : contentType;
        String storagePath = UUID.randomUUID().toString();

        StoreObjectResponse response = objectStoragePort.putObject(StoreObjectRequest.builder()
                .storagePath(storagePath)
                .content(content)
                .size(size)
                .contentType(effectiveContentType)
                .build());

        try {
            FileEntity fileEntity = FileEntity.builder()
                    .name(fileName)
                    .storagePath(storagePath)
                    .etag(response.getEtag())
                    .mimeType(effectiveContentType)
                    .size(size)
                    .ownerUser(ownerUser)
                    .ownerOrganization(ownerOrganization)
                    .folder(folder)
                    .createdByUser(createdByUser)
                    .build();

            FileEntity savedFile = fileRepository.save(fileEntity);

            String ownerType = ownerUserId != null ? "USER" : "ORGANIZATION";
            applicationMetricsPort.recordFileUpload(size, ownerType);

            // Determine and initiate background processing jobs.
            List<ProcessingJob.JobType> plannedJobs = processingJobPlanner.planJobs(effectiveContentType);

            for (ProcessingJob.JobType jobType : plannedJobs) {
                ProcessingJob job = ProcessingJob.builder()
                        .file(savedFile)
                        .jobType(jobType)
                        .status(ProcessingJob.JobStatus.PENDING)
                        .build();

                ProcessingJob savedJob = processingJobRepository.save(job);

                applicationMetricsPort.recordJobCreated(jobType.name());

                FileProcessingRequestedEvent event = FileProcessingRequestedEvent.builder()
                        .eventId(UUID.randomUUID())
                        .eventType("file.processing.requested")
                        .occurredAt(OffsetDateTime.now())
                        .fileId(savedFile.getId())
                        .processingJobId(savedJob.getId())
                        .jobType(jobType.name())
                        .storagePath(savedFile.getStoragePath())
                        .mimeType(savedFile.getMimeType())
                        .size(savedFile.getSize())
                        .ownerUserId(ownerUserId)
                        .ownerOrganizationId(ownerOrganizationId)
                        .build();

                applicationEventPublisher.publishEvent(event);
            }

            return savedFile;
        } catch (Exception e) {
            // Cleanup orphaned binary content if database persistence fails.
            log.error("Failed to save file metadata to database. Cleaning up object from storage: {}", storagePath, e);
            try {
                objectStoragePort.deleteObject(storagePath);
            } catch (Exception cleanupEx) {
                log.error("Failed to cleanup orphaned object from storage: {}", storagePath, cleanupEx);
            }
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public CursorPageResponse<FileResponse> searchFiles(FileSearchQuery query, UUID actorUserId) {
        if (query.getFolderId() != null) {
            accessControlService.assertCanAccessFolder(actorUserId, query.getFolderId(), Permission.FOLDER_VIEW);
            FolderEntity folder = folderRepository.findByIdAndDeletedAtIsNull(query.getFolderId())
                    .orElseThrow(() -> new ResourceNotFoundException("Folder not found: " + query.getFolderId()));
            applyOrValidateFolderOwnerContext(query, folder);
        }

        validateExactlyOneOwner(query.getOwnerUserId(), query.getOwnerOrganizationId());

        if (query.getOwnerUserId() != null) {
            if (!query.getOwnerUserId().equals(actorUserId)) {
                throw new AccessDeniedException("You can only list your own files.");
            }

            userRepository.findById(query.getOwnerUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("User not found: " + query.getOwnerUserId()));
        } else {
            accessControlService.assertOrganizationPermission(actorUserId, query.getOwnerOrganizationId(), Permission.FILE_VIEW);
            organizationRepository.findById(query.getOwnerOrganizationId())
                    .orElseThrow(() -> new ResourceNotFoundException("Organization not found: " + query.getOwnerOrganizationId()));
        }

        FileSearchCriteria criteria = fileSearchCriteriaMapper.toCriteria(query);
        Specification<FileEntity> specification = fileSearchSpecificationBuilder.build(criteria);
        Pageable pageable = PageRequest.of(
                0,
                criteria.pageRequest().fetchSize(),
                fileSortMapper.toSort(criteria.sort()));
        List<FileEntity> rows = fileRepository.findAll(specification, pageable).getContent();

        return toFilePage(rows, criteria);
    }

    private CursorPageResponse<FileResponse> toFilePage(List<FileEntity> rows, FileSearchCriteria criteria) {
        boolean hasMore = rows.size() > criteria.pageRequest().size();
        List<FileEntity> pageRows = hasMore ? rows.subList(0, criteria.pageRequest().size()) : rows;
        FileEntity last = pageRows.isEmpty() ? null : pageRows.getLast();

        return CursorPageResponse.<FileResponse>builder()
                .items(pageRows.stream().map(fileResponseMapper::toResponse).toList())
                .hasMore(hasMore)
                .nextCursor(nextFileCursor(hasMore, last, criteria))
                .pageSize(criteria.pageRequest().size())
                .build();
    }

    private String nextFileCursor(boolean hasMore, FileEntity last, FileSearchCriteria criteria) {
        if (!hasMore || last == null) {
            return null;
        }

        return FileSearchCursor.encode(criteria.sort(), last);
    }

    private void validateExactlyOneOwner(UUID ownerUserId, UUID ownerOrganizationId) {
        if ((ownerUserId != null && ownerOrganizationId != null) || (ownerUserId == null && ownerOrganizationId == null)) {
            throw new IllegalArgumentException("Exactly one owner (user or organization) must be provided");
        }
    }

    private void validateFolderOwnerContext(FolderEntity folder, UUID ownerUserId, UUID ownerOrganizationId) {
        UUID folderOwnerUserId = folder.getOwnerUser() != null ? folder.getOwnerUser().getId() : null;
        UUID folderOwnerOrganizationId = folder.getOwnerOrganization() != null ? folder.getOwnerOrganization().getId() : null;
        if (!Objects.equals(folderOwnerUserId, ownerUserId)
                || !Objects.equals(folderOwnerOrganizationId, ownerOrganizationId)) {
            throw new IllegalArgumentException("File owner context must match folder owner context");
        }
    }

    private void applyOrValidateFolderOwnerContext(FileSearchQuery query, FolderEntity folder) {
        UUID folderOwnerUserId = folder.getOwnerUser() != null ? folder.getOwnerUser().getId() : null;
        UUID folderOwnerOrganizationId = folder.getOwnerOrganization() != null ? folder.getOwnerOrganization().getId() : null;

        if (query.getOwnerUserId() == null && query.getOwnerOrganizationId() == null) {
            query.setOwnerUserId(folderOwnerUserId);
            query.setOwnerOrganizationId(folderOwnerOrganizationId);
            return;
        }

        if (!Objects.equals(query.getOwnerUserId(), folderOwnerUserId)
                || !Objects.equals(query.getOwnerOrganizationId(), folderOwnerOrganizationId)) {
            throw new IllegalArgumentException("File search owner context must match folder owner context");
        }
    }

    private void enforceUserQuota(User ownerUser, long uploadSize) {
        long currentSize = fileRepository.sumActiveSizeByOwnerUser(ownerUser);
        long quota = appProperties.getQuota().getUserBytes();
        if (wouldExceedQuota(currentSize, uploadSize, quota)) {
            throw new IllegalStateException("User storage quota exceeded");
        }
    }

    private void enforceOrganizationQuota(Organization ownerOrganization, long uploadSize) {
        long currentSize = fileRepository.sumActiveSizeByOwnerOrganization(ownerOrganization);
        long quota = appProperties.getQuota().getOrganizationBytes();
        if (wouldExceedQuota(currentSize, uploadSize, quota)) {
            throw new IllegalStateException("Organization storage quota exceeded");
        }
    }

    private boolean wouldExceedQuota(long currentSize, long uploadSize, long quota) {
        return uploadSize > quota || currentSize > quota - uploadSize;
    }

    public FileEntity getFileMetadata(UUID fileId, UUID actorUserId) {
        accessControlService.assertCanAccessFile(actorUserId, fileId, Permission.FILE_VIEW);
        return fileRepository.findByIdAndDeletedAtIsNull(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found: " + fileId));
    }

    public InputStream downloadFile(UUID fileId, UUID actorUserId) {
        accessControlService.assertCanAccessFile(actorUserId, fileId, Permission.FILE_VIEW);
        FileEntity file = fileRepository.findByIdAndDeletedAtIsNull(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found: " + fileId));
        applicationMetricsPort.recordFileDownload();
        return objectStoragePort.getObject(file.getStoragePath());
    }

    @Transactional
    public void deleteFile(UUID fileId, UUID actorUserId) {
        accessControlService.assertCanAccessFile(actorUserId, fileId, Permission.FILE_DELETE);
        FileEntity file = fileRepository.findByIdAndDeletedAtIsNull(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found: " + fileId));
        file.setDeletedAt(OffsetDateTime.now());
        fileRepository.save(file);
    }
}

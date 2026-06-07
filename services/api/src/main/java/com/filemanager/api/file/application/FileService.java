package com.filemanager.api.file.application;

import com.filemanager.api.auth.application.AccessControlService;
import com.filemanager.api.auth.domain.Permission;
import com.filemanager.api.config.AppProperties;
import com.filemanager.api.exception.ResourceNotFoundException;
import com.filemanager.api.file.web.FileResponse;
import com.filemanager.api.file.web.FileResponseMapper;
import com.filemanager.api.file.web.search.FileSearchQuery;
import com.filemanager.api.file.application.search.FileSearchCriteria;
import com.filemanager.api.file.application.search.FileSearchCriteriaMapper;
import com.filemanager.api.file.application.search.FileSearchCursor;
import com.filemanager.api.file.application.search.FileSortMapper;
import com.filemanager.api.file.domain.FileEntity;
import com.filemanager.api.file.persistence.FileRepository;
import com.filemanager.api.file.persistence.search.FileSearchSpecificationBuilder;
import com.filemanager.api.folder.domain.FolderEntity;
import com.filemanager.api.folder.persistence.FolderRepository;
import com.filemanager.api.identity.domain.User;
import com.filemanager.api.identity.persistence.UserRepository;
import com.filemanager.api.observability.application.FileManagerMetrics;
import com.filemanager.api.processing.application.job.ProcessingJobPlanner;
import com.filemanager.api.processing.application.policy.ProcessingPolicyContext;
import com.filemanager.api.processing.domain.ProcessingJob;
import com.filemanager.api.processing.messaging.FileProcessingRequestedEvent;
import com.filemanager.api.processing.persistence.ProcessingJobRepository;
import com.filemanager.api.storage.port.ObjectStoragePort;
import com.filemanager.api.storage.port.StoreObjectRequest;
import com.filemanager.api.storage.port.StoreObjectResponse;
import com.filemanager.api.tag.application.TagService;
import com.filemanager.api.web.CursorPageResponse;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileService {

    private final FileRepository fileRepository;
    private final FolderRepository folderRepository;
    private final UserRepository userRepository;
    private final ProcessingJobRepository processingJobRepository;
    private final ProcessingJobPlanner processingJobPlanner;
    private final ObjectStoragePort objectStoragePort;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final AccessControlService accessControlService;
    private final FileManagerMetrics fileManagerMetrics;
    private final AppProperties appProperties;
    private final FileSearchCriteriaMapper fileSearchCriteriaMapper;
    private final FileSearchSpecificationBuilder fileSearchSpecificationBuilder;
    private final FileSortMapper fileSortMapper;
    private final FileResponseMapper fileResponseMapper;
    private final TagService tagService;

    @Transactional
    public FileEntity uploadFile(
            String fileName,
            String contentType,
            long size,
            InputStream content,
            UUID actorUserId) {
        return uploadFile(fileName, contentType, size, content, null, actorUserId);
    }

    @Transactional
    public FileEntity uploadFile(
            String fileName,
            String contentType,
            long size,
            InputStream content,
            UUID folderId,
            UUID actorUserId) {
        User ownerUser = findUser(actorUserId);
        FolderEntity folder = resolveUploadFolder(folderId, actorUserId);
        if (folderId == null) {
            accessControlService.assertCanUploadToOwner(actorUserId, actorUserId);
        }
        enforceUserQuota(ownerUser, size);

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
                    .folder(folder)
                    .createdByUser(ownerUser)
                    .build();

            FileEntity savedFile = fileRepository.save(fileEntity);
            fileManagerMetrics.recordFileUpload(size, "USER");

            ProcessingPolicyContext processingContext = new ProcessingPolicyContext(
                    actorUserId,
                    folderId,
                    effectiveContentType,
                    null);
            List<ProcessingJob.JobType> plannedJobs = processingJobPlanner.planJobs(processingContext);

            for (ProcessingJob.JobType jobType : plannedJobs) {
                ProcessingJob job = ProcessingJob.builder()
                        .file(savedFile)
                        .jobType(jobType)
                        .status(ProcessingJob.JobStatus.PENDING)
                        .build();

                ProcessingJob savedJob = processingJobRepository.save(job);
                fileManagerMetrics.recordJobCreated(jobType.name());

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
                        .ownerUserId(actorUserId)
                        .build();

                applicationEventPublisher.publishEvent(event);
            }

            return savedFile;
        } catch (Exception e) {
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
        findUser(actorUserId);
        if (query.getFolderId() != null) {
            accessControlService.assertCanAccessFolder(actorUserId, query.getFolderId(), Permission.FOLDER_VIEW);
            folderRepository.findByIdAndDeletedAtIsNull(query.getFolderId())
                    .orElseThrow(() -> new ResourceNotFoundException("Folder not found: " + query.getFolderId()));
        }

        tagService.assertCanUseTagForFileSearch(query.getTagId(), actorUserId, query.getFolderId());
        FileSearchCriteria criteria = fileSearchCriteriaMapper.toCriteria(query, actorUserId);
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

    private FolderEntity resolveUploadFolder(UUID folderId, UUID actorUserId) {
        if (folderId == null) {
            return null;
        }

        accessControlService.assertCanAccessFolder(actorUserId, folderId, Permission.FOLDER_UPLOAD_FILE);

        return folderRepository.findByIdAndDeletedAtIsNull(folderId)
                .orElseThrow(() -> new ResourceNotFoundException("Folder not found: " + folderId));
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }

    private void enforceUserQuota(User ownerUser, long uploadSize) {
        long currentSize = fileRepository.sumActiveSizeByOwnerUser(ownerUser);
        long quota = appProperties.getQuota().getUserBytes();
        if (wouldExceedQuota(currentSize, uploadSize, quota)) {
            throw new IllegalStateException("User storage quota exceeded");
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
        fileManagerMetrics.recordFileDownload();

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

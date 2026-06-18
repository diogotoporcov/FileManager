package com.diogotoporcov.filemanager.api.file.application;

import com.diogotoporcov.filemanager.api.auth.application.AccessControlService;
import com.diogotoporcov.filemanager.api.auth.domain.Permission;
import com.diogotoporcov.filemanager.api.config.AppProperties;
import com.diogotoporcov.filemanager.api.config.FileTransferProperties;
import com.diogotoporcov.filemanager.api.duplicate.application.ExactDuplicateGroupMaintenanceService;
import com.diogotoporcov.filemanager.api.exception.FileTransferDisabledException;
import com.diogotoporcov.filemanager.api.exception.ResourceNotFoundException;
import com.diogotoporcov.filemanager.api.file.web.FileResponse;
import com.diogotoporcov.filemanager.api.file.web.FileResponseMapper;
import com.diogotoporcov.filemanager.api.file.web.search.FileSearchQuery;
import com.diogotoporcov.filemanager.api.file.application.search.FileSearchCriteria;
import com.diogotoporcov.filemanager.api.file.application.search.FileSearchCriteriaMapper;
import com.diogotoporcov.filemanager.api.file.application.search.FileSearchCursor;
import com.diogotoporcov.filemanager.api.file.application.search.FileSortMapper;
import com.diogotoporcov.filemanager.api.file.domain.FileEntity;
import com.diogotoporcov.filemanager.api.file.persistence.FileRepository;
import com.diogotoporcov.filemanager.api.file.persistence.search.FileSearchSpecificationBuilder;
import com.diogotoporcov.filemanager.api.folder.domain.FolderEntity;
import com.diogotoporcov.filemanager.api.folder.persistence.FolderRepository;
import com.diogotoporcov.filemanager.api.identity.domain.User;
import com.diogotoporcov.filemanager.api.identity.persistence.UserRepository;
import com.diogotoporcov.filemanager.api.observability.application.FileManagerMetrics;
import com.diogotoporcov.filemanager.api.processing.application.job.ProcessingJobPlanner;
import com.diogotoporcov.filemanager.api.processing.application.policy.ProcessingPolicyContext;
import com.diogotoporcov.filemanager.api.processing.domain.ProcessingJob;
import com.diogotoporcov.filemanager.api.processing.domain.result.FileFingerprint;
import com.diogotoporcov.filemanager.api.processing.messaging.FileProcessingRequestedEvent;
import com.diogotoporcov.filemanager.api.processing.persistence.result.FileFingerprintRepository;
import com.diogotoporcov.filemanager.api.processing.persistence.ProcessingJobRepository;
import com.diogotoporcov.filemanager.api.storage.exception.StorageException;
import com.diogotoporcov.filemanager.api.storage.port.CreatePresignedDownloadUrlRequest;
import com.diogotoporcov.filemanager.api.storage.port.CreatePresignedDownloadUrlResponse;
import com.diogotoporcov.filemanager.api.storage.port.GetObjectRequest;
import com.diogotoporcov.filemanager.api.storage.port.GetObjectResponse;
import com.diogotoporcov.filemanager.api.storage.port.ObjectStoragePort;
import com.diogotoporcov.filemanager.api.storage.port.StoreObjectRequest;
import com.diogotoporcov.filemanager.api.storage.port.StoreObjectResponse;
import com.diogotoporcov.filemanager.api.tag.application.TagService;
import com.diogotoporcov.filemanager.api.web.CursorPageResponse;
import java.io.IOException;
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
    private final FileFingerprintRepository fileFingerprintRepository;
    private final FolderRepository folderRepository;
    private final UserRepository userRepository;
    private final ProcessingJobRepository processingJobRepository;
    private final ProcessingJobPlanner processingJobPlanner;
    private final ObjectStoragePort objectStoragePort;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final AccessControlService accessControlService;
    private final FileManagerMetrics fileManagerMetrics;
    private final AppProperties appProperties;
    private final FileTransferProperties fileTransferProperties;
    private final FileSearchCriteriaMapper fileSearchCriteriaMapper;
    private final FileSearchSpecificationBuilder fileSearchSpecificationBuilder;
    private final FileSortMapper fileSortMapper;
    private final FileResponseMapper fileResponseMapper;
    private final TagService tagService;
    private final ExactDuplicateGroupMaintenanceService exactDuplicateGroupMaintenanceService;

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
        if (size < 0) {
            throw new IllegalArgumentException("File size must not be negative");
        }

        String normalizedFileName = FileTransferPolicy.normalizeUploadFilename(fileName);
        User ownerUser = findUser(actorUserId);
        FolderEntity folder = resolveUploadFolder(folderId, actorUserId);
        if (folderId == null) {
            accessControlService.assertCanUploadToOwner(actorUserId, actorUserId);
        }

        String effectiveContentType = FileTransferPolicy.safeContentType(contentType);
        String storagePath = UUID.randomUUID().toString();

        StoreObjectResponse response = objectStoragePort.putObject(StoreObjectRequest.builder()
                .storagePath(storagePath)
                .content(content)
                .size(size)
                .contentType(effectiveContentType)
                .build());

        try {
            User lockedOwnerUser = lockUser(ownerUser.getId());
            enforceUserQuota(lockedOwnerUser, size);

            FileEntity fileEntity = FileEntity.builder()
                    .name(normalizedFileName)
                    .storagePath(storagePath)
                    .etag(response.getEtag())
                    .mimeType(effectiveContentType)
                    .size(size)
                    .ownerUser(lockedOwnerUser)
                    .folder(folder)
                    .createdByUser(lockedOwnerUser)
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

    private User lockUser(UUID userId) {
        return userRepository.findByIdForUpdate(userId)
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

    public FileDownload openDownload(UUID fileId, UUID actorUserId, String rangeHeader) {
        accessControlService.assertCanAccessFile(actorUserId, fileId, Permission.FILE_VIEW);
        FileEntity file = fileRepository.findByIdAndDeletedAtIsNull(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found: " + fileId));
        long completeSize = file.getSize();
        FileDownloadRange range = FileDownloadRange.parse(rangeHeader, completeSize);
        GetObjectRequest objectRequest = GetObjectRequest.builder()
                .storagePath(file.getStoragePath())
                .rangeStart(range == null ? null : range.getStart())
                .rangeEnd(range == null ? null : range.getEnd())
                .build();
        GetObjectResponse objectResponse = objectStoragePort.getObject(objectRequest);
        long contentLength = range == null ? completeSize : range.getEnd() - range.getStart() + 1;
        validateObjectContentLength(fileId, objectResponse, contentLength);
        fileManagerMetrics.recordFileDownload();

        return FileDownload.builder()
                .name(file.getName())
                .mimeType(file.getMimeType())
                .completeSize(completeSize)
                .contentLength(contentLength)
                .rangeStart(range == null ? null : range.getStart())
                .rangeEnd(range == null ? null : range.getEnd())
                .etag(objectResponse.getEtag() == null ? file.getEtag() : objectResponse.getEtag())
                .content(objectResponse.getContent())
                .build();
    }

    private void validateObjectContentLength(UUID fileId, GetObjectResponse objectResponse, long expectedContentLength) {
        if (objectResponse.getContentLength() < 0 || objectResponse.getContentLength() == expectedContentLength) {
            return;
        }

        closeQuietly(objectResponse.getContent());
        log.warn("Storage object content length mismatch for file {}", fileId);
        throw new StorageException("Storage object content length does not match file metadata.");
    }

    private void closeQuietly(InputStream content) {
        if (content == null) {
            return;
        }

        try {
            content.close();
        } catch (IOException ex) {
            log.debug("Failed to close mismatched storage stream", ex);
        }
    }

    public PresignedDownloadUrl createPresignedDownloadUrl(UUID fileId, UUID actorUserId) {
        if (!fileTransferProperties.getPresignedDownload().isEnabled()) {
            throw new FileTransferDisabledException("Presigned downloads are disabled.");
        }

        accessControlService.assertCanAccessFile(actorUserId, fileId, Permission.FILE_VIEW);
        FileEntity file = fileRepository.findByIdAndDeletedAtIsNull(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found: " + fileId));
        var presignedDownload = fileTransferProperties.getPresignedDownload();
        CreatePresignedDownloadUrlResponse response = objectStoragePort.createPresignedDownloadUrl(
                CreatePresignedDownloadUrlRequest.builder()
                        .storagePath(file.getStoragePath())
                        .ttl(presignedDownload.getTtl())
                        .responseContentDisposition(FileTransferPolicy.attachmentContentDisposition(file.getName()))
                        .responseContentType(FileTransferPolicy.safeContentType(file.getMimeType()))
                        .build());

        return PresignedDownloadUrl.builder()
                .url(response.getUrl())
                .expiresAt(response.getExpiresAt())
                .expiresInSeconds(presignedDownload.getTtl().toSeconds())
                .method("GET")
                .build();
    }

    @Transactional
    public void deleteFile(UUID fileId, UUID actorUserId) {
        accessControlService.assertCanAccessFile(actorUserId, fileId, Permission.FILE_DELETE);
        FileEntity file = fileRepository.findByIdAndDeletedAtIsNull(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found: " + fileId));
        file.setDeletedAt(OffsetDateTime.now());
        fileRepository.save(file);
        fileFingerprintRepository.findByFileIdAndAlgorithm(fileId, FileFingerprint.FingerprintAlgorithm.SHA256)
                .ifPresent(fingerprint -> exactDuplicateGroupMaintenanceService.refreshGroup(
                        file.getOwnerUser().getId(),
                        fingerprint.getAlgorithm(),
                        fingerprint.getHashValue()));
    }
}

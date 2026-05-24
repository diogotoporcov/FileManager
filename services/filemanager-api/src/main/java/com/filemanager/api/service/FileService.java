package com.filemanager.api.service;

import com.filemanager.api.auth.AccessControlService;
import com.filemanager.api.auth.Permission;
import com.filemanager.api.event.FileProcessingRequestedEvent;
import com.filemanager.api.entity.FileEntity;
import com.filemanager.api.entity.Organization;
import com.filemanager.api.entity.ProcessingJob;
import com.filemanager.api.entity.User;
import com.filemanager.api.exception.AccessDeniedException;
import com.filemanager.api.exception.ResourceNotFoundException;
import com.filemanager.api.port.ObjectStoragePort;
import com.filemanager.api.port.StoreObjectRequest;
import com.filemanager.api.repository.FileRepository;
import com.filemanager.api.repository.OrganizationRepository;
import com.filemanager.api.repository.ProcessingJobRepository;
import com.filemanager.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileService {

    private final FileRepository fileRepository;
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final ProcessingJobRepository processingJobRepository;
    private final ProcessingJobPlanner processingJobPlanner;
    private final ObjectStoragePort objectStoragePort;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final AccessControlService accessControlService;
    private final FileManagerMetrics fileManagerMetrics;

    @Transactional
    public FileEntity uploadFile(String fileName, String contentType, long size, InputStream content, UUID ownerUserId, UUID ownerOrganizationId, UUID actorUserId) {
        accessControlService.assertCanUploadToContext(actorUserId, ownerUserId, ownerOrganizationId);
        validateExactlyOneOwner(ownerUserId, ownerOrganizationId);

        User ownerUser = null;
        if (ownerUserId != null) {
            ownerUser = userRepository.findById(ownerUserId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found: " + ownerUserId));
        }

        Organization ownerOrganization = null;
        if (ownerOrganizationId != null) {
            ownerOrganization = organizationRepository.findById(ownerOrganizationId)
                    .orElseThrow(() -> new ResourceNotFoundException("Organization not found: " + ownerOrganizationId));
        }

        String effectiveContentType = (contentType == null || contentType.isBlank()) ? "application/octet-stream" : contentType;
        String storagePath = UUID.randomUUID().toString();

        objectStoragePort.putObject(StoreObjectRequest.builder()
                .storagePath(storagePath)
                .content(content)
                .size(size)
                .contentType(effectiveContentType)
                .build());

        try {
            FileEntity fileEntity = FileEntity.builder()
                    .name(fileName)
                    .storagePath(storagePath)
                    .mimeType(effectiveContentType)
                    .size(size)
                    .ownerUser(ownerUser)
                    .ownerOrganization(ownerOrganization)
                    .build();

            FileEntity savedFile = fileRepository.save(fileEntity);

            String ownerType = ownerUserId != null ? "USER" : "ORGANIZATION";
            fileManagerMetrics.recordFileUpload(size, ownerType);

            // Determine and initiate background processing jobs.
            List<ProcessingJob.JobType> plannedJobs = processingJobPlanner.planJobs(effectiveContentType);

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

    public List<FileEntity> listFiles(UUID ownerUserId, UUID ownerOrganizationId, UUID actorUserId) {
        validateExactlyOneOwner(ownerUserId, ownerOrganizationId);

        if (ownerUserId != null) {
            if (!ownerUserId.equals(actorUserId)) {
                throw new AccessDeniedException("You can only list your own files.");
            }
            User user = userRepository.findById(ownerUserId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found: " + ownerUserId));
            return fileRepository.findAllByOwnerUserAndDeletedAtIsNull(user);
        }

        accessControlService.assertOrganizationPermission(actorUserId, ownerOrganizationId, Permission.FILE_VIEW);
        Organization org = organizationRepository.findById(ownerOrganizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found: " + ownerOrganizationId));
        return fileRepository.findAllByOwnerOrganizationAndDeletedAtIsNull(org);
    }

    private void validateExactlyOneOwner(UUID ownerUserId, UUID ownerOrganizationId) {
        if ((ownerUserId != null && ownerOrganizationId != null) || (ownerUserId == null && ownerOrganizationId == null)) {
            throw new IllegalArgumentException("Exactly one owner (user or organization) must be provided");
        }
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

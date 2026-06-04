package com.filemanager.api.service;

import com.filemanager.api.auth.AccessControlService;
import com.filemanager.api.auth.Permission;
import com.filemanager.api.config.AppProperties;
import com.filemanager.api.event.FileProcessingRequestedEvent;
import com.filemanager.api.entity.FileEntity;
import com.filemanager.api.entity.FolderEntity;
import com.filemanager.api.entity.Organization;
import com.filemanager.api.entity.ProcessingJob;
import com.filemanager.api.entity.User;
import com.filemanager.api.mapper.FileResponseMapper;
import com.filemanager.api.port.ApplicationMetricsPort;
import com.filemanager.api.port.ObjectStoragePort;
import com.filemanager.api.port.StoreObjectResponse;
import com.filemanager.api.repository.FileRepository;
import com.filemanager.api.repository.FolderRepository;
import com.filemanager.api.repository.OrganizationRepository;
import com.filemanager.api.repository.ProcessingJobRepository;
import com.filemanager.api.repository.UserRepository;
import com.filemanager.api.search.file.FileSearchCriteriaMapper;
import com.filemanager.api.search.file.FileSearchQuery;
import com.filemanager.api.search.file.FileSearchSpecificationBuilder;
import com.filemanager.api.search.file.FileSortMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileServiceTest {
    @Mock
    private FileRepository fileRepository;
    @Mock
    private FolderRepository folderRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private ProcessingJobRepository processingJobRepository;
    @Mock
    private ProcessingJobPlanner processingJobPlanner;
    @Mock
    private ObjectStoragePort objectStoragePort;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;
    @Mock
    private AccessControlService accessControlService;
    @Mock
    private ApplicationMetricsPort applicationMetricsPort;
    @Mock
    private TagService tagService;

    private FileService fileService;

    private User user;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = new User();
        user.setId(userId);
        FileSortMapper fileSortMapper = new FileSortMapper();
        fileService = new FileService(
                fileRepository,
                folderRepository,
                userRepository,
                organizationRepository,
                processingJobRepository,
                processingJobPlanner,
                objectStoragePort,
                applicationEventPublisher,
                accessControlService,
                applicationMetricsPort,
                new AppProperties(),
                new FileSearchCriteriaMapper(fileSortMapper),
                new FileSearchSpecificationBuilder(),
                fileSortMapper,
                new FileResponseMapper(),
                tagService
        );
    }

    @Test
    void uploadFile_ShouldCreateJobAndPublishEvent() throws java.io.IOException {
        String fileName = "test.txt";
        String contentType = "text/plain";
        long size = 10L;
        
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        
        FileEntity fileEntity = new FileEntity();
        fileEntity.setId(UUID.randomUUID());
        fileEntity.setStoragePath("storage-path");
        fileEntity.setMimeType(contentType);
        fileEntity.setSize(size);
        
        when(fileRepository.save(any(FileEntity.class))).thenReturn(fileEntity);

        when(objectStoragePort.putObject(any())).thenReturn(StoreObjectResponse.builder()
                .storagePath("storage-path")
                .etag("test-etag")
                .build());
        
        when(processingJobPlanner.planJobs(any(ProcessingPolicyContext.class))).thenReturn(List.of(ProcessingJob.JobType.CHECKSUM));

        ProcessingJob processingJob = new ProcessingJob();
        processingJob.setId(UUID.randomUUID());
        processingJob.setJobType(ProcessingJob.JobType.CHECKSUM);
        when(processingJobRepository.save(any(ProcessingJob.class))).thenReturn(processingJob);

        FileEntity result;
        try (ByteArrayInputStream content = new ByteArrayInputStream("hello world".getBytes())) {
            result = fileService.uploadFile(fileName, contentType, size, content, userId, null, userId);
        }

        assertNotNull(result);
        verify(accessControlService).assertCanUploadToContext(userId, userId, null);
        verify(fileRepository).save(any(FileEntity.class));
        verify(processingJobRepository).save(any(ProcessingJob.class));
        
        verify(applicationMetricsPort).recordFileUpload(size, "USER");
        verify(applicationMetricsPort).recordJobCreated("CHECKSUM");

        ArgumentCaptor<FileProcessingRequestedEvent> eventCaptor = ArgumentCaptor.forClass(FileProcessingRequestedEvent.class);
        verify(applicationEventPublisher).publishEvent(eventCaptor.capture());
        
        FileProcessingRequestedEvent publishedEvent = eventCaptor.getValue();
        assertEquals(fileEntity.getId(), publishedEvent.fileId());
        assertEquals(processingJob.getId(), publishedEvent.processingJobId());
        assertEquals(ProcessingJob.JobType.CHECKSUM.name(), publishedEvent.jobType());
        assertEquals("file.processing.requested", publishedEvent.eventType());
        assertEquals(userId, publishedEvent.ownerUserId());

        ArgumentCaptor<FileEntity> fileCaptor = ArgumentCaptor.forClass(FileEntity.class);
        verify(fileRepository).save(fileCaptor.capture());
        assertEquals(user, fileCaptor.getValue().getCreatedByUser());
    }

    @Test
    void uploadFile_Image_ShouldCreateMultipleJobsAndPublishEvents() throws java.io.IOException {
        String fileName = "test.png";
        String contentType = "image/png";
        long size = 100L;
        
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        
        FileEntity fileEntity = new FileEntity();
        fileEntity.setId(UUID.randomUUID());
        fileEntity.setStoragePath("storage-path");
        fileEntity.setMimeType(contentType);
        fileEntity.setSize(size);
        
        when(fileRepository.save(any(FileEntity.class))).thenReturn(fileEntity);
        
        when(objectStoragePort.putObject(any())).thenReturn(StoreObjectResponse.builder()
                .storagePath("storage-path")
                .etag("test-etag")
                .build());

        when(processingJobPlanner.planJobs(any(ProcessingPolicyContext.class))).thenReturn(List.of(ProcessingJob.JobType.CHECKSUM, ProcessingJob.JobType.PHASH));

        ProcessingJob checksumJob = ProcessingJob.builder().id(UUID.randomUUID()).jobType(ProcessingJob.JobType.CHECKSUM).build();
        ProcessingJob phashJob = ProcessingJob.builder().id(UUID.randomUUID()).jobType(ProcessingJob.JobType.PHASH).build();
        
        when(processingJobRepository.save(any(ProcessingJob.class)))
                .thenReturn(checksumJob)
                .thenReturn(phashJob);

        FileEntity result;
        try (ByteArrayInputStream content = new ByteArrayInputStream(new byte[100])) {
            result = fileService.uploadFile(fileName, contentType, size, content, userId, null, userId);
        }

        assertNotNull(result);
        verify(accessControlService).assertCanUploadToContext(userId, userId, null);
        verify(processingJobRepository, times(2)).save(any(ProcessingJob.class));
        
        verify(applicationMetricsPort).recordFileUpload(size, "USER");
        verify(applicationMetricsPort).recordJobCreated("CHECKSUM");
        verify(applicationMetricsPort).recordJobCreated("PHASH");

        ArgumentCaptor<FileProcessingRequestedEvent> eventCaptor = ArgumentCaptor.forClass(FileProcessingRequestedEvent.class);
        verify(applicationEventPublisher, times(2)).publishEvent(eventCaptor.capture());
        
        List<FileProcessingRequestedEvent> events = eventCaptor.getAllValues();
        assertEquals(2, events.size());
        
        assertTrue(events.stream().anyMatch(e -> e.jobType().equals("CHECKSUM")));
        assertTrue(events.stream().anyMatch(e -> e.jobType().equals("PHASH")));
    }

    @Test
    void uploadFile_OrganizationOwned_ShouldRecordOrganizationMetrics() throws java.io.IOException {
        String fileName = "org.txt";
        String contentType = "text/plain";
        long size = 50L;
        UUID orgId = UUID.randomUUID();
        Organization org = new Organization();
        org.setId(orgId);

        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(org));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        FileEntity fileEntity = new FileEntity();
        fileEntity.setId(UUID.randomUUID());
        fileEntity.setStoragePath("storage-path");
        fileEntity.setMimeType(contentType);
        fileEntity.setSize(size);
        fileEntity.setOwnerOrganization(org);

        when(fileRepository.save(any(FileEntity.class))).thenReturn(fileEntity);

        when(objectStoragePort.putObject(any())).thenReturn(StoreObjectResponse.builder()
                .storagePath("storage-path")
                .etag("test-etag")
                .build());

        when(processingJobPlanner.planJobs(any(ProcessingPolicyContext.class))).thenReturn(List.of(ProcessingJob.JobType.CHECKSUM));
        
        ProcessingJob job = ProcessingJob.builder().id(UUID.randomUUID()).jobType(ProcessingJob.JobType.CHECKSUM).build();
        when(processingJobRepository.save(any(ProcessingJob.class))).thenReturn(job);

        try (ByteArrayInputStream content = new ByteArrayInputStream(new byte[50])) {
            fileService.uploadFile(fileName, contentType, size, content, null, orgId, userId);
        }

        verify(accessControlService).assertCanUploadToContext(userId, null, orgId);
        verify(applicationMetricsPort).recordFileUpload(size, "ORGANIZATION");
    }

    @Test
    void uploadFileIntoFolderSetsFolderAndCreatedByUser() throws java.io.IOException {
        UUID folderId = UUID.randomUUID();
        FolderEntity folder = FolderEntity.builder()
                .id(folderId)
                .ownerUser(user)
                .build();
        when(folderRepository.findByIdAndDeletedAtIsNull(folderId)).thenReturn(Optional.of(folder));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(fileRepository.save(any(FileEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(objectStoragePort.putObject(any())).thenReturn(StoreObjectResponse.builder()
                .storagePath("storage-path")
                .etag("test-etag")
                .build());
        when(processingJobPlanner.planJobs(any(ProcessingPolicyContext.class))).thenReturn(List.of());

        try (ByteArrayInputStream content = new ByteArrayInputStream("hello".getBytes())) {
            fileService.uploadFile("folder.txt", "text/plain", 5L, content, userId, null, folderId, userId);
        }

        verify(accessControlService).assertCanAccessFolder(userId, folderId, Permission.FOLDER_UPLOAD_FILE);
        ArgumentCaptor<FileEntity> fileCaptor = ArgumentCaptor.forClass(FileEntity.class);
        verify(fileRepository).save(fileCaptor.capture());
        assertEquals(folder, fileCaptor.getValue().getFolder());
        assertEquals(user, fileCaptor.getValue().getCreatedByUser());
    }

    @Test
    void uploadFileIntoFolderRejectsMismatchedOwnerContext() {
        UUID folderId = UUID.randomUUID();
        FolderEntity folder = FolderEntity.builder()
                .id(folderId)
                .ownerUser(user)
                .build();
        when(folderRepository.findByIdAndDeletedAtIsNull(folderId)).thenReturn(Optional.of(folder));

        assertThrows(IllegalArgumentException.class, () -> fileService.uploadFile(
                "folder.txt",
                "text/plain",
                5L,
                new ByteArrayInputStream("hello".getBytes()),
                UUID.randomUUID(),
                null,
                folderId,
                userId));

        verify(fileRepository, never()).save(any(FileEntity.class));
    }

    @Test
    void downloadFile_ShouldRecordDownloadMetrics() throws java.io.IOException {
        UUID fileId = UUID.randomUUID();
        FileEntity file = new FileEntity();
        file.setId(fileId);
        file.setStoragePath("path/to/file");

        when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.of(file));
        when(objectStoragePort.getObject("path/to/file")).thenReturn(new ByteArrayInputStream("data".getBytes()));

        try (InputStream is = fileService.downloadFile(fileId, userId)) {
            assertNotNull(is);
        }

        verify(accessControlService).assertCanAccessFile(userId, fileId, Permission.FILE_VIEW);
        verify(applicationMetricsPort).recordFileDownload();
    }

    @Test
    void searchFilesWithoutSearchParamsUsesSpecificationAndDefaultSort() {
        FileSearchQuery query = new FileSearchQuery();
        query.setOwnerUserId(userId);
        query.setSize(2);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        FileEntity first = file("a.txt", 10L, OffsetDateTime.parse("2026-01-03T00:00:00Z"));
        FileEntity second = file("b.txt", 20L, OffsetDateTime.parse("2026-01-02T00:00:00Z"));
        FileEntity extra = file("c.txt", 30L, OffsetDateTime.parse("2026-01-01T00:00:00Z"));
        when(fileRepository.findAll(org.mockito.ArgumentMatchers.<Specification<FileEntity>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(first, second, extra)));

        var result = fileService.searchFiles(query, userId);

        assertEquals(2, result.getItems().size());
        assertTrue(result.isHasMore());
        assertNotNull(result.getNextCursor());
        assertEquals(2, result.getPageSize());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(fileRepository).findAll(org.mockito.ArgumentMatchers.<Specification<FileEntity>>any(), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertEquals(3, pageable.getPageSize());
        assertEquals("createdAt: DESC,id: DESC", pageable.getSort().toString());
    }

    @Test
    void searchFilesWithFolderIdChecksFolderBeforeQueryAndInfersOwnerScope() {
        UUID folderId = UUID.randomUUID();
        FolderEntity folder = FolderEntity.builder()
                .id(folderId)
                .ownerUser(user)
                .build();
        FileSearchQuery query = new FileSearchQuery();
        query.setFolderId(folderId);
        query.setSize(2);

        when(folderRepository.findByIdAndDeletedAtIsNull(folderId)).thenReturn(Optional.of(folder));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(fileRepository.findAll(org.mockito.ArgumentMatchers.<Specification<FileEntity>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        fileService.searchFiles(query, userId);

        verify(accessControlService).assertCanAccessFolder(userId, folderId, Permission.FOLDER_VIEW);
        verify(fileRepository).findAll(org.mockito.ArgumentMatchers.<Specification<FileEntity>>any(), any(Pageable.class));
        assertEquals(userId, query.getOwnerUserId());
    }

    @Test
    void searchFilesWithTagIdValidatesTagBeforeQueryExecution() {
        UUID tagId = UUID.randomUUID();
        FileSearchQuery query = new FileSearchQuery();
        query.setOwnerUserId(userId);
        query.setTagId(tagId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(fileRepository.findAll(org.mockito.ArgumentMatchers.<Specification<FileEntity>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        fileService.searchFiles(query, userId);

        verify(tagService).assertCanUseTagForFileSearch(tagId, userId, userId, null, null);
        verify(fileRepository).findAll(org.mockito.ArgumentMatchers.<Specification<FileEntity>>any(), any(Pageable.class));
    }

    @Test
    void searchFilesRejectsDifferentOwnerUserBeforeQueryExecution() {
        FileSearchQuery query = new FileSearchQuery();
        query.setOwnerUserId(UUID.randomUUID());

        assertThrows(com.filemanager.api.exception.AccessDeniedException.class, () -> fileService.searchFiles(query, userId));

        verify(fileRepository, never()).findAll(org.mockito.ArgumentMatchers.<Specification<FileEntity>>any(), any(Pageable.class));
    }

    @Test
    void searchFilesAppliesOrganizationAuthorizationBeforeQueryExecution() {
        UUID organizationId = UUID.randomUUID();
        Organization organization = new Organization();
        organization.setId(organizationId);

        FileSearchQuery query = new FileSearchQuery();
        query.setOwnerOrganizationId(organizationId);
        when(organizationRepository.findById(organizationId)).thenReturn(Optional.of(organization));
        when(fileRepository.findAll(org.mockito.ArgumentMatchers.<Specification<FileEntity>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        fileService.searchFiles(query, userId);

        verify(accessControlService).assertOrganizationPermission(userId, organizationId, Permission.FILE_VIEW);
        verify(fileRepository).findAll(org.mockito.ArgumentMatchers.<Specification<FileEntity>>any(), any(Pageable.class));
    }

    private FileEntity file(String name, Long size, OffsetDateTime createdAt) {
        FileEntity file = new FileEntity();
        file.setId(UUID.randomUUID());
        file.setName(name);
        file.setSize(size);
        file.setMimeType("text/plain");
        file.setOwnerUser(user);
        file.setCreatedAt(createdAt);
        file.setUpdatedAt(createdAt);
        return file;
    }
}

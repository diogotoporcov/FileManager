package com.filemanager.api.file.application;

import com.filemanager.api.auth.application.AccessControlService;
import com.filemanager.api.auth.domain.Permission;
import com.filemanager.api.config.AppProperties;
import com.filemanager.api.file.web.FileResponseMapper;
import com.filemanager.api.file.web.search.FileSearchQuery;
import com.filemanager.api.file.application.search.FileSearchCriteriaMapper;
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
import com.filemanager.api.storage.port.StoreObjectResponse;
import com.filemanager.api.tag.application.TagService;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.jpa.domain.Specification;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
    private FileManagerMetrics fileManagerMetrics;
    @Mock
    private TagService tagService;

    private FileService fileService;
    private User user;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = User.builder().id(userId).email("user@example.com").build();
        FileSortMapper fileSortMapper = new FileSortMapper();
        fileService = new FileService(
                fileRepository,
                folderRepository,
                userRepository,
                processingJobRepository,
                processingJobPlanner,
                objectStoragePort,
                applicationEventPublisher,
                accessControlService,
                fileManagerMetrics,
                new AppProperties(),
                new FileSearchCriteriaMapper(fileSortMapper),
                new FileSearchSpecificationBuilder(),
                fileSortMapper,
                new FileResponseMapper(),
                tagService);
    }

    @Test
    void uploadFileUsesAuthenticatedUserAsOwnerAndPublishesProcessingEvent() throws Exception {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(fileRepository.save(any(FileEntity.class))).thenAnswer(invocation -> {
            FileEntity file = invocation.getArgument(0);
            file.setId(UUID.randomUUID());

            return file;
        });
        when(objectStoragePort.putObject(any())).thenReturn(StoreObjectResponse.builder()
                .storagePath("storage-path")
                .etag("test-etag")
                .build());
        when(processingJobPlanner.planJobs(any(ProcessingPolicyContext.class))).thenReturn(List.of(ProcessingJob.JobType.CHECKSUM));
        ProcessingJob processingJob = ProcessingJob.builder().id(UUID.randomUUID()).jobType(ProcessingJob.JobType.CHECKSUM).build();
        when(processingJobRepository.save(any(ProcessingJob.class))).thenReturn(processingJob);

        FileEntity result;
        try (ByteArrayInputStream content = new ByteArrayInputStream("hello".getBytes())) {
            result = fileService.uploadFile("test.txt", "text/plain", 5L, content, userId);
        }

        assertNotNull(result);
        verify(accessControlService).assertCanUploadToOwner(userId, userId);
        verify(fileManagerMetrics).recordFileUpload(5L, "USER");
        verify(fileManagerMetrics).recordJobCreated("CHECKSUM");

        ArgumentCaptor<FileEntity> fileCaptor = ArgumentCaptor.forClass(FileEntity.class);
        verify(fileRepository).save(fileCaptor.capture());
        assertEquals(user, fileCaptor.getValue().getOwnerUser());
        assertEquals(user, fileCaptor.getValue().getCreatedByUser());

        ArgumentCaptor<FileProcessingRequestedEvent> eventCaptor = ArgumentCaptor.forClass(FileProcessingRequestedEvent.class);
        verify(applicationEventPublisher).publishEvent(eventCaptor.capture());
        assertEquals(result.getId(), eventCaptor.getValue().fileId());
        assertEquals(userId, eventCaptor.getValue().ownerUserId());
    }

    @Test
    void uploadFilePlansMultipleJobs() throws Exception {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(fileRepository.save(any(FileEntity.class))).thenAnswer(invocation -> {
            FileEntity file = invocation.getArgument(0);
            file.setId(UUID.randomUUID());

            return file;
        });
        when(objectStoragePort.putObject(any())).thenReturn(StoreObjectResponse.builder().etag("etag").build());
        when(processingJobPlanner.planJobs(any(ProcessingPolicyContext.class)))
                .thenReturn(List.of(ProcessingJob.JobType.CHECKSUM, ProcessingJob.JobType.PHASH));
        when(processingJobRepository.save(any(ProcessingJob.class)))
                .thenReturn(ProcessingJob.builder().id(UUID.randomUUID()).jobType(ProcessingJob.JobType.CHECKSUM).build())
                .thenReturn(ProcessingJob.builder().id(UUID.randomUUID()).jobType(ProcessingJob.JobType.PHASH).build());

        try (ByteArrayInputStream content = new ByteArrayInputStream(new byte[10])) {
            fileService.uploadFile("image.png", "image/png", 10L, content, userId);
        }

        verify(processingJobRepository, times(2)).save(any(ProcessingJob.class));
        verify(applicationEventPublisher, times(2)).publishEvent(any(FileProcessingRequestedEvent.class));
    }

    @Test
    void uploadFileIntoFolderKeepsUploaderAsFileOwner() throws Exception {
        UUID folderId = UUID.randomUUID();
        User folderOwner = User.builder().id(UUID.randomUUID()).email("owner@example.com").build();
        FolderEntity folder = FolderEntity.builder().id(folderId).ownerUser(folderOwner).build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(folderRepository.findByIdAndDeletedAtIsNull(folderId)).thenReturn(Optional.of(folder));
        when(fileRepository.save(any(FileEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(objectStoragePort.putObject(any())).thenReturn(StoreObjectResponse.builder().etag("etag").build());
        when(processingJobPlanner.planJobs(any(ProcessingPolicyContext.class))).thenReturn(List.of());

        try (ByteArrayInputStream content = new ByteArrayInputStream("hello".getBytes())) {
            fileService.uploadFile("folder.txt", "text/plain", 5L, content, folderId, userId);
        }

        verify(accessControlService).assertCanAccessFolder(userId, folderId, Permission.FOLDER_UPLOAD_FILE);
        ArgumentCaptor<FileEntity> fileCaptor = ArgumentCaptor.forClass(FileEntity.class);
        verify(fileRepository).save(fileCaptor.capture());
        assertEquals(user, fileCaptor.getValue().getOwnerUser());
        assertEquals(folder, fileCaptor.getValue().getFolder());
    }

    @Test
    void searchFilesDefaultsToAuthenticatedUserVisibility() {
        FileSearchQuery query = new FileSearchQuery();
        query.setSize(2);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(fileRepository.findAll(org.mockito.ArgumentMatchers.<Specification<FileEntity>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(
                        file("a.txt", 10L, OffsetDateTime.parse("2026-01-03T00:00:00Z")),
                        file("b.txt", 20L, OffsetDateTime.parse("2026-01-02T00:00:00Z")),
                        file("c.txt", 30L, OffsetDateTime.parse("2026-01-01T00:00:00Z")))));

        var result = fileService.searchFiles(query, userId);

        assertEquals(2, result.getItems().size());
        assertTrue(result.isHasMore());
        assertNotNull(result.getNextCursor());
        verify(tagService).assertCanUseTagForFileSearch(null, userId, null);
    }

    @Test
    void searchFilesWithFolderChecksFolderBeforeQuery() {
        UUID folderId = UUID.randomUUID();
        FileSearchQuery query = new FileSearchQuery();
        query.setFolderId(folderId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(folderRepository.findByIdAndDeletedAtIsNull(folderId))
                .thenReturn(Optional.of(FolderEntity.builder().id(folderId).ownerUser(user).build()));
        when(fileRepository.findAll(org.mockito.ArgumentMatchers.<Specification<FileEntity>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        fileService.searchFiles(query, userId);

        verify(accessControlService).assertCanAccessFolder(userId, folderId, Permission.FOLDER_VIEW);
        verify(tagService).assertCanUseTagForFileSearch(null, userId, folderId);
    }

    @Test
    void searchFilesWithTagValidatesTagBeforeQuery() {
        UUID tagId = UUID.randomUUID();
        FileSearchQuery query = new FileSearchQuery();
        query.setTagId(tagId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(fileRepository.findAll(org.mockito.ArgumentMatchers.<Specification<FileEntity>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        fileService.searchFiles(query, userId);

        verify(tagService).assertCanUseTagForFileSearch(tagId, userId, null);
    }

    @Test
    void downloadFileRecordsMetricAfterPermissionCheck() throws Exception {
        UUID fileId = UUID.randomUUID();
        FileEntity file = FileEntity.builder().id(fileId).storagePath("path").ownerUser(user).build();
        when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.of(file));
        when(objectStoragePort.getObject("path")).thenReturn(new ByteArrayInputStream("data".getBytes()));

        try (InputStream stream = fileService.downloadFile(fileId, userId)) {
            assertNotNull(stream);
        }

        verify(accessControlService).assertCanAccessFile(userId, fileId, Permission.FILE_VIEW);
        verify(fileManagerMetrics).recordFileDownload();
    }

    private FileEntity file(String name, Long size, OffsetDateTime createdAt) {
        return FileEntity.builder()
                .id(UUID.randomUUID())
                .name(name)
                .size(size)
                .mimeType("text/plain")
                .ownerUser(user)
                .createdByUser(user)
                .createdAt(createdAt)
                .updatedAt(createdAt)
                .build();
    }
}

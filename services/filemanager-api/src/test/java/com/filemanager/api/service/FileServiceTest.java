package com.filemanager.api.service;

import com.filemanager.api.auth.AccessControlService;
import com.filemanager.api.event.FileProcessingRequestedEvent;
import com.filemanager.api.entity.FileEntity;
import com.filemanager.api.entity.Organization;
import com.filemanager.api.entity.ProcessingJob;
import com.filemanager.api.entity.User;
import com.filemanager.api.port.ObjectStoragePort;
import com.filemanager.api.repository.FileRepository;
import com.filemanager.api.repository.OrganizationRepository;
import com.filemanager.api.repository.ProcessingJobRepository;
import com.filemanager.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileServiceTest {
    @Mock
    private FileRepository fileRepository;
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
    private FileManagerMetrics fileManagerMetrics;

    @InjectMocks
    private FileService fileService;

    private User user;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = new User();
        user.setId(userId);
    }

    @Test
    void uploadFile_ShouldCreateJobAndPublishEvent() {
        String fileName = "test.txt";
        String contentType = "text/plain";
        long size = 10L;
        ByteArrayInputStream content = new ByteArrayInputStream("hello world".getBytes());
        
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        
        FileEntity fileEntity = new FileEntity();
        fileEntity.setId(UUID.randomUUID());
        fileEntity.setStoragePath("storage-path");
        fileEntity.setMimeType(contentType);
        fileEntity.setSize(size);
        
        when(fileRepository.save(any(FileEntity.class))).thenReturn(fileEntity);
        
        when(processingJobPlanner.planJobs(contentType)).thenReturn(List.of(ProcessingJob.JobType.CHECKSUM));

        ProcessingJob processingJob = new ProcessingJob();
        processingJob.setId(UUID.randomUUID());
        processingJob.setJobType(ProcessingJob.JobType.CHECKSUM);
        when(processingJobRepository.save(any(ProcessingJob.class))).thenReturn(processingJob);

        FileEntity result = fileService.uploadFile(fileName, contentType, size, content, userId, null, userId);

        assertNotNull(result);
        verify(fileRepository).save(any(FileEntity.class));
        verify(processingJobRepository).save(any(ProcessingJob.class));
        
        verify(fileManagerMetrics).recordFileUpload(size, "USER");
        verify(fileManagerMetrics).recordJobCreated("CHECKSUM");

        ArgumentCaptor<FileProcessingRequestedEvent> eventCaptor = ArgumentCaptor.forClass(FileProcessingRequestedEvent.class);
        verify(applicationEventPublisher).publishEvent(eventCaptor.capture());
        
        FileProcessingRequestedEvent publishedEvent = eventCaptor.getValue();
        assertEquals(fileEntity.getId(), publishedEvent.fileId());
        assertEquals(processingJob.getId(), publishedEvent.processingJobId());
        assertEquals(ProcessingJob.JobType.CHECKSUM.name(), publishedEvent.jobType());
        assertEquals("file.processing.requested", publishedEvent.eventType());
        assertEquals(userId, publishedEvent.ownerUserId());
    }

    @Test
    void uploadFile_Image_ShouldCreateMultipleJobsAndPublishEvents() {
        String fileName = "test.png";
        String contentType = "image/png";
        long size = 100L;
        ByteArrayInputStream content = new ByteArrayInputStream(new byte[100]);
        
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        
        FileEntity fileEntity = new FileEntity();
        fileEntity.setId(UUID.randomUUID());
        fileEntity.setStoragePath("storage-path");
        fileEntity.setMimeType(contentType);
        fileEntity.setSize(size);
        
        when(fileRepository.save(any(FileEntity.class))).thenReturn(fileEntity);
        
        when(processingJobPlanner.planJobs(contentType)).thenReturn(List.of(ProcessingJob.JobType.CHECKSUM, ProcessingJob.JobType.PHASH));

        ProcessingJob checksumJob = ProcessingJob.builder().id(UUID.randomUUID()).jobType(ProcessingJob.JobType.CHECKSUM).build();
        ProcessingJob phashJob = ProcessingJob.builder().id(UUID.randomUUID()).jobType(ProcessingJob.JobType.PHASH).build();
        
        when(processingJobRepository.save(any(ProcessingJob.class)))
                .thenReturn(checksumJob)
                .thenReturn(phashJob);

        FileEntity result = fileService.uploadFile(fileName, contentType, size, content, userId, null, userId);

        assertNotNull(result);
        verify(processingJobRepository, times(2)).save(any(ProcessingJob.class));
        
        verify(fileManagerMetrics).recordFileUpload(size, "USER");
        verify(fileManagerMetrics).recordJobCreated("CHECKSUM");
        verify(fileManagerMetrics).recordJobCreated("PHASH");

        ArgumentCaptor<FileProcessingRequestedEvent> eventCaptor = ArgumentCaptor.forClass(FileProcessingRequestedEvent.class);
        verify(applicationEventPublisher, times(2)).publishEvent(eventCaptor.capture());
        
        List<FileProcessingRequestedEvent> events = eventCaptor.getAllValues();
        assertEquals(2, events.size());
        
        assertTrue(events.stream().anyMatch(e -> e.jobType().equals("CHECKSUM")));
        assertTrue(events.stream().anyMatch(e -> e.jobType().equals("PHASH")));
    }

    @Test
    void uploadFile_OrganizationOwned_ShouldRecordOrganizationMetrics() {
        String fileName = "org.txt";
        String contentType = "text/plain";
        long size = 50L;
        ByteArrayInputStream content = new ByteArrayInputStream(new byte[50]);
        UUID orgId = UUID.randomUUID();
        Organization org = new Organization();
        org.setId(orgId);

        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(org));

        FileEntity fileEntity = new FileEntity();
        fileEntity.setId(UUID.randomUUID());
        fileEntity.setStoragePath("storage-path");
        fileEntity.setMimeType(contentType);
        fileEntity.setSize(size);
        fileEntity.setOwnerOrganization(org);

        when(fileRepository.save(any(FileEntity.class))).thenReturn(fileEntity);
        when(processingJobPlanner.planJobs(contentType)).thenReturn(List.of(ProcessingJob.JobType.CHECKSUM));
        
        ProcessingJob job = ProcessingJob.builder().id(UUID.randomUUID()).jobType(ProcessingJob.JobType.CHECKSUM).build();
        when(processingJobRepository.save(any(ProcessingJob.class))).thenReturn(job);

        fileService.uploadFile(fileName, contentType, size, content, null, orgId, userId);

        verify(fileManagerMetrics).recordFileUpload(size, "ORGANIZATION");
    }

    @Test
    void downloadFile_ShouldRecordDownloadMetrics() {
        UUID fileId = UUID.randomUUID();
        FileEntity file = new FileEntity();
        file.setId(fileId);
        file.setStoragePath("path/to/file");

        when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.of(file));
        when(objectStoragePort.getObject("path/to/file")).thenReturn(new ByteArrayInputStream("data".getBytes()));

        fileService.downloadFile(fileId, userId);

        verify(fileManagerMetrics).recordFileDownload();
    }
}

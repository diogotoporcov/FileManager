package com.filemanager.api.service;

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
    private ObjectStoragePort objectStoragePort;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

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
        // Arrange
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
        
        ProcessingJob processingJob = new ProcessingJob();
        processingJob.setId(UUID.randomUUID());
        when(processingJobRepository.save(any(ProcessingJob.class))).thenReturn(processingJob);

        // Act
        FileEntity result = fileService.uploadFile(fileName, contentType, size, content, userId, null);

        // Assert
        assertNotNull(result);
        verify(fileRepository).save(any(FileEntity.class));
        verify(processingJobRepository).save(any(ProcessingJob.class));
        
        ArgumentCaptor<FileProcessingRequestedEvent> eventCaptor = ArgumentCaptor.forClass(FileProcessingRequestedEvent.class);
        verify(applicationEventPublisher).publishEvent(eventCaptor.capture());
        
        FileProcessingRequestedEvent publishedEvent = eventCaptor.getValue();
        assertEquals(fileEntity.getId(), publishedEvent.fileId());
        assertEquals(processingJob.getId(), publishedEvent.processingJobId());
        assertEquals("file.processing.requested", publishedEvent.eventType());
        assertEquals(userId, publishedEvent.ownerUserId());
    }
}

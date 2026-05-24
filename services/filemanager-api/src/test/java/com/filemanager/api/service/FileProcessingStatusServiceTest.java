package com.filemanager.api.service;

import com.filemanager.api.auth.AccessControlService;
import com.filemanager.api.auth.Permission;
import com.filemanager.api.dto.FileProcessingStatusResponse;
import com.filemanager.api.dto.FileProcessingStatusResponse.AggregateStatus;
import com.filemanager.api.dto.ProcessingJobResponse;
import com.filemanager.api.entity.FileEntity;
import com.filemanager.api.entity.ProcessingJob;
import com.filemanager.api.repository.DuplicateCandidateRepository;
import com.filemanager.api.repository.ProcessingJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileProcessingStatusServiceTest {

    @Mock
    private ProcessingJobRepository processingJobRepository;
    @Mock
    private DuplicateCandidateRepository duplicateCandidateRepository;
    @Mock
    private AccessControlService accessControlService;

    @InjectMocks
    private FileProcessingStatusService fileProcessingStatusService;

    private UUID actorUserId;
    private UUID fileId;
    private FileEntity fileEntity;

    @BeforeEach
    void setUp() {
        actorUserId = UUID.randomUUID();
        fileId = UUID.randomUUID();
        fileEntity = FileEntity.builder().id(fileId).build();
    }

    @Test
    @SuppressWarnings("unchecked")
    void getProcessingJobs_ShouldReturnJobs_WhenActorHasAccess() {
        // Arrange
        ProcessingJob job = ProcessingJob.builder()
                .id(UUID.randomUUID())
                .file(fileEntity)
                .jobType(ProcessingJob.JobType.CHECKSUM)
                .status(ProcessingJob.JobStatus.COMPLETED)
                .build();
        when(processingJobRepository.findAllByFile_IdOrderByCreatedAtAsc(fileId)).thenReturn(List.of(job));

        // Act
        List<ProcessingJobResponse> result = fileProcessingStatusService.getProcessingJobs(actorUserId, fileId);

        // Assert
        assertEquals(1, result.size());
        assertEquals(job.getId(), result.get(0).getId());
        verify(accessControlService).assertCanAccessFile(actorUserId, fileId, Permission.FILE_VIEW);
    }

    @Test
    @SuppressWarnings("unchecked")
    void getFileProcessingStatus_ShouldReturnNotStarted_WhenNoJobs() {
        // Arrange
        when(processingJobRepository.findAllByFile_IdOrderByCreatedAtAsc(fileId)).thenReturn(Collections.emptyList());

        // Act
        FileProcessingStatusResponse result = fileProcessingStatusService.getFileProcessingStatus(actorUserId, fileId);

        // Assert
        assertEquals(AggregateStatus.NOT_STARTED, result.getOverallStatus());
        assertTrue(result.getJobs().isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void getFileProcessingStatus_ShouldReturnProcessing_WhenAnyJobPending() {
        // Arrange
        ProcessingJob job1 = ProcessingJob.builder().status(ProcessingJob.JobStatus.COMPLETED).file(fileEntity).build();
        ProcessingJob job2 = ProcessingJob.builder().status(ProcessingJob.JobStatus.PENDING).file(fileEntity).build();
        when(processingJobRepository.findAllByFile_IdOrderByCreatedAtAsc(fileId)).thenReturn(List.of(job1, job2));

        // Act
        FileProcessingStatusResponse result = fileProcessingStatusService.getFileProcessingStatus(actorUserId, fileId);

        // Assert
        assertEquals(AggregateStatus.PROCESSING, result.getOverallStatus());
    }

    @Test
    @SuppressWarnings("unchecked")
    void getFileProcessingStatus_ShouldReturnCompleted_WhenAllJobsCompleted() {
        // Arrange
        ProcessingJob job1 = ProcessingJob.builder().status(ProcessingJob.JobStatus.COMPLETED).file(fileEntity).build();
        when(processingJobRepository.findAllByFile_IdOrderByCreatedAtAsc(fileId)).thenReturn(List.of(job1));

        // Act
        FileProcessingStatusResponse result = fileProcessingStatusService.getFileProcessingStatus(actorUserId, fileId);

        // Assert
        assertEquals(AggregateStatus.COMPLETED, result.getOverallStatus());
    }

    @Test
    @SuppressWarnings("unchecked")
    void getFileProcessingStatus_ShouldReturnFailed_WhenAllJobsFailed() {
        // Arrange
        ProcessingJob job1 = ProcessingJob.builder().status(ProcessingJob.JobStatus.FAILED).file(fileEntity).build();
        when(processingJobRepository.findAllByFile_IdOrderByCreatedAtAsc(fileId)).thenReturn(List.of(job1));

        // Act
        FileProcessingStatusResponse result = fileProcessingStatusService.getFileProcessingStatus(actorUserId, fileId);

        // Assert
        assertEquals(AggregateStatus.FAILED, result.getOverallStatus());
    }

    @Test
    @SuppressWarnings("unchecked")
    void getFileProcessingStatus_ShouldReturnPartialFailure_WhenMixed() {
        // Arrange
        ProcessingJob job1 = ProcessingJob.builder().status(ProcessingJob.JobStatus.COMPLETED).file(fileEntity).build();
        ProcessingJob job2 = ProcessingJob.builder().status(ProcessingJob.JobStatus.FAILED).file(fileEntity).build();
        when(processingJobRepository.findAllByFile_IdOrderByCreatedAtAsc(fileId)).thenReturn(List.of(job1, job2));

        // Act
        FileProcessingStatusResponse result = fileProcessingStatusService.getFileProcessingStatus(actorUserId, fileId);

        // Assert
        assertEquals(AggregateStatus.PARTIAL_FAILURE, result.getOverallStatus());
    }

    @Test
    @SuppressWarnings("unchecked")
    void getFileProcessingStatus_ShouldIncludeCounts() {
        // Arrange
        when(processingJobRepository.findAllByFile_IdOrderByCreatedAtAsc(fileId)).thenReturn(Collections.emptyList());
        when(duplicateCandidateRepository.count(any(Specification.class))).thenReturn(5L);

        // Act
        FileProcessingStatusResponse result = fileProcessingStatusService.getFileProcessingStatus(actorUserId, fileId);

        // Assert
        assertEquals(5L, result.getTotalDuplicateCandidates());
        assertEquals(3, result.getDuplicateCandidatesByDetectionMethod().size());
        assertEquals(5L, result.getDuplicateCandidatesByDetectionMethod().get("EXACT"));
        assertEquals(5L, result.getDuplicateCandidatesByDetectionMethod().get("PHASH"));
        assertEquals(5L, result.getDuplicateCandidatesByDetectionMethod().get("EMBEDDING"));
        assertEquals(3, result.getDuplicateCandidatesByStatus().size());
        assertEquals(5L, result.getDuplicateCandidatesByStatus().get("PENDING"));
        assertEquals(5L, result.getDuplicateCandidatesByStatus().get("CONFIRMED"));
        assertEquals(5L, result.getDuplicateCandidatesByStatus().get("REJECTED"));
        verify(accessControlService).assertCanAccessFile(actorUserId, fileId, Permission.FILE_VIEW);
    }

    @Test
    void getProcessingJobs_ShouldPropagateAccessDenied() {
        // Arrange
        doThrow(new com.filemanager.api.exception.AccessDeniedException("Denied"))
                .when(accessControlService).assertCanAccessFile(actorUserId, fileId, Permission.FILE_VIEW);

        // Act & Assert
        assertThrows(com.filemanager.api.exception.AccessDeniedException.class,
                () -> fileProcessingStatusService.getProcessingJobs(actorUserId, fileId));
    }

    @Test
    void getFileProcessingStatus_ShouldPropagateResourceNotFound() {
        // Arrange
        doThrow(new com.filemanager.api.exception.ResourceNotFoundException("Not found"))
                .when(accessControlService).assertCanAccessFile(actorUserId, fileId, Permission.FILE_VIEW);

        // Act & Assert
        assertThrows(com.filemanager.api.exception.ResourceNotFoundException.class,
                () -> fileProcessingStatusService.getFileProcessingStatus(actorUserId, fileId));
    }
}

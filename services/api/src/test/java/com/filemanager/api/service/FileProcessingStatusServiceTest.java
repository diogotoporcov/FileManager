package com.filemanager.api.service;

import com.filemanager.api.auth.AccessControlService;
import com.filemanager.api.auth.Permission;
import com.filemanager.api.dto.BoundedPageRequest;
import com.filemanager.api.dto.CursorPageResponse;
import com.filemanager.api.dto.FileProcessingStatusResponse;
import com.filemanager.api.dto.FileProcessingStatusResponse.AggregateStatus;
import com.filemanager.api.dto.ProcessingJobResponse;
import com.filemanager.api.entity.FileEntity;
import com.filemanager.api.entity.ProcessingJob;
import com.filemanager.api.repository.ProcessingJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileProcessingStatusServiceTest {

    @Mock
    private ProcessingJobRepository processingJobRepository;
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
    void getProcessingJobs_ShouldReturnJobs_WhenActorHasAccess() {
        ProcessingJob job = ProcessingJob.builder()
                .id(UUID.randomUUID())
                .file(fileEntity)
                .jobType(ProcessingJob.JobType.CHECKSUM)
                .status(ProcessingJob.JobStatus.COMPLETED)
                .build();
        when(processingJobRepository.findAllByFile_IdOrderByCreatedAtAsc(org.mockito.ArgumentMatchers.eq(fileId), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(job));

        CursorPageResponse<ProcessingJobResponse> result = fileProcessingStatusService.getProcessingJobs(
                actorUserId,
                fileId,
                BoundedPageRequest.of(null, null));

        assertEquals(1, result.getItems().size());
        assertEquals(job.getId(), result.getItems().getFirst().getId());
        verify(accessControlService).assertCanAccessFile(actorUserId, fileId, Permission.FILE_VIEW);
    }

    @Test
    void getFileProcessingStatus_ShouldReturnNotStarted_WhenNoJobs() {
        when(processingJobRepository.findAllByFile_IdOrderByCreatedAtAsc(org.mockito.ArgumentMatchers.eq(fileId), org.mockito.ArgumentMatchers.any())).thenReturn(Collections.emptyList());

        FileProcessingStatusResponse result = fileProcessingStatusService.getFileProcessingStatus(actorUserId, fileId);

        assertEquals(AggregateStatus.NOT_STARTED, result.getOverallStatus());
        assertTrue(result.getJobs().isEmpty());
    }

    @Test
    void getFileProcessingStatus_ShouldReturnProcessing_WhenAnyJobPending() {
        ProcessingJob job1 = ProcessingJob.builder().status(ProcessingJob.JobStatus.COMPLETED).file(fileEntity).build();
        ProcessingJob job2 = ProcessingJob.builder().status(ProcessingJob.JobStatus.PENDING).file(fileEntity).build();
        when(processingJobRepository.findAllByFile_IdOrderByCreatedAtAsc(org.mockito.ArgumentMatchers.eq(fileId), org.mockito.ArgumentMatchers.any())).thenReturn(List.of(job1, job2));

        FileProcessingStatusResponse result = fileProcessingStatusService.getFileProcessingStatus(actorUserId, fileId);

        assertEquals(AggregateStatus.PROCESSING, result.getOverallStatus());
    }

    @Test
    void getFileProcessingStatus_ShouldReturnCompleted_WhenAllJobsCompleted() {
        ProcessingJob job1 = ProcessingJob.builder().status(ProcessingJob.JobStatus.COMPLETED).file(fileEntity).build();
        when(processingJobRepository.findAllByFile_IdOrderByCreatedAtAsc(org.mockito.ArgumentMatchers.eq(fileId), org.mockito.ArgumentMatchers.any())).thenReturn(List.of(job1));

        FileProcessingStatusResponse result = fileProcessingStatusService.getFileProcessingStatus(actorUserId, fileId);

        assertEquals(AggregateStatus.COMPLETED, result.getOverallStatus());
    }

    @Test
    void getFileProcessingStatus_ShouldReturnFailed_WhenAllJobsFailed() {
        ProcessingJob job1 = ProcessingJob.builder().status(ProcessingJob.JobStatus.FAILED).file(fileEntity).build();
        when(processingJobRepository.findAllByFile_IdOrderByCreatedAtAsc(org.mockito.ArgumentMatchers.eq(fileId), org.mockito.ArgumentMatchers.any())).thenReturn(List.of(job1));

        FileProcessingStatusResponse result = fileProcessingStatusService.getFileProcessingStatus(actorUserId, fileId);

        assertEquals(AggregateStatus.FAILED, result.getOverallStatus());
    }

    @Test
    void getFileProcessingStatus_ShouldReturnPartialFailure_WhenMixed() {
        ProcessingJob job1 = ProcessingJob.builder().status(ProcessingJob.JobStatus.COMPLETED).file(fileEntity).build();
        ProcessingJob job2 = ProcessingJob.builder().status(ProcessingJob.JobStatus.FAILED).file(fileEntity).build();
        when(processingJobRepository.findAllByFile_IdOrderByCreatedAtAsc(org.mockito.ArgumentMatchers.eq(fileId), org.mockito.ArgumentMatchers.any())).thenReturn(List.of(job1, job2));

        FileProcessingStatusResponse result = fileProcessingStatusService.getFileProcessingStatus(actorUserId, fileId);

        assertEquals(AggregateStatus.PARTIAL_FAILURE, result.getOverallStatus());
    }

    @Test
    void getProcessingJobs_ShouldPropagateAccessDenied() {
        doThrow(new com.filemanager.api.exception.AccessDeniedException("Denied"))
                .when(accessControlService).assertCanAccessFile(actorUserId, fileId, Permission.FILE_VIEW);

        // Act & Assert
        assertThrows(com.filemanager.api.exception.AccessDeniedException.class,
                () -> fileProcessingStatusService.getProcessingJobs(
                        actorUserId,
                        fileId,
                        BoundedPageRequest.of(null, null)));
    }

    @Test
    void getFileProcessingStatus_ShouldPropagateResourceNotFound() {
        doThrow(new com.filemanager.api.exception.ResourceNotFoundException("Not found"))
                .when(accessControlService).assertCanAccessFile(actorUserId, fileId, Permission.FILE_VIEW);

        // Act & Assert
        assertThrows(com.filemanager.api.exception.ResourceNotFoundException.class,
                () -> fileProcessingStatusService.getFileProcessingStatus(actorUserId, fileId));
    }
}

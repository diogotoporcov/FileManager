package com.filemanager.api.processing.application.status;

import com.filemanager.api.auth.application.AccessControlService;
import com.filemanager.api.auth.domain.Permission;
import com.filemanager.api.file.domain.FileEntity;
import com.filemanager.api.processing.web.status.FileProcessingStatusResponse.AggregateStatus;
import com.filemanager.api.processing.web.status.FileProcessingStatusResponse;
import com.filemanager.api.processing.web.status.ProcessingJobResponse;
import com.filemanager.api.processing.domain.ProcessingJob;
import com.filemanager.api.processing.persistence.ProcessingJobRepository;
import com.filemanager.api.web.BoundedPageRequest;
import com.filemanager.api.web.CursorPageResponse;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
        when(processingJobRepository.findPage(eq(fileId), any()))
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
    void getProcessingJobs_ShouldUseCursor_WhenCursorProvided() {
        OffsetDateTime cursorCreatedAt = OffsetDateTime.parse("2026-01-01T10:15:30Z");
        UUID cursorId = UUID.randomUUID();
        String cursor = BoundedPageRequest.encodeCursor(cursorCreatedAt, cursorId);
        ProcessingJob job = ProcessingJob.builder()
                .id(UUID.randomUUID())
                .file(fileEntity)
                .jobType(ProcessingJob.JobType.PHASH)
                .status(ProcessingJob.JobStatus.PENDING)
                .createdAt(cursorCreatedAt.plusSeconds(1))
                .build();
        when(processingJobRepository.findPageAfterCursor(eq(fileId), eq(cursorCreatedAt), eq(cursorId), any()))
                .thenReturn(List.of(job));

        CursorPageResponse<ProcessingJobResponse> result = fileProcessingStatusService.getProcessingJobs(
                actorUserId,
                fileId,
                BoundedPageRequest.of(10, cursor));

        assertEquals(1, result.getItems().size());
        assertEquals(job.getId(), result.getItems().getFirst().getId());
        verify(processingJobRepository).findPageAfterCursor(eq(fileId), eq(cursorCreatedAt), eq(cursorId), any());
    }

    @Test
    void getProcessingJobs_ShouldReturnNextCursor_WhenMoreJobsExist() {
        ProcessingJob job1 = processingJob(
                UUID.randomUUID(),
                OffsetDateTime.parse("2026-01-01T10:15:30Z"),
                ProcessingJob.JobType.CHECKSUM);
        ProcessingJob job2 = processingJob(
                UUID.randomUUID(),
                OffsetDateTime.parse("2026-01-01T10:16:30Z"),
                ProcessingJob.JobType.PHASH);
        when(processingJobRepository.findPage(eq(fileId), any())).thenReturn(List.of(job1, job2));

        CursorPageResponse<ProcessingJobResponse> result = fileProcessingStatusService.getProcessingJobs(
                actorUserId,
                fileId,
                BoundedPageRequest.of(1, null));

        assertEquals(1, result.getItems().size());
        assertTrue(result.isHasMore());
        assertEquals(BoundedPageRequest.encodeCursor(job1.getCreatedAt(), job1.getId()), result.getNextCursor());
    }

    @Test
    void getFileProcessingStatus_ShouldReturnNotStarted_WhenNoJobs() {
        when(processingJobRepository.findPage(eq(fileId), any())).thenReturn(Collections.emptyList());

        FileProcessingStatusResponse result = fileProcessingStatusService.getFileProcessingStatus(actorUserId, fileId);

        assertEquals(AggregateStatus.NOT_STARTED, result.getOverallStatus());
        assertTrue(result.getJobs().isEmpty());
    }

    @Test
    void getFileProcessingStatus_ShouldReturnProcessing_WhenAnyJobPending() {
        ProcessingJob job1 = ProcessingJob.builder().status(ProcessingJob.JobStatus.COMPLETED).file(fileEntity).build();
        ProcessingJob job2 = ProcessingJob.builder().status(ProcessingJob.JobStatus.PENDING).file(fileEntity).build();
        when(processingJobRepository.findPage(eq(fileId), any())).thenReturn(List.of(job1, job2));

        FileProcessingStatusResponse result = fileProcessingStatusService.getFileProcessingStatus(actorUserId, fileId);

        assertEquals(AggregateStatus.PROCESSING, result.getOverallStatus());
    }

    @Test
    void getFileProcessingStatus_ShouldReturnCompleted_WhenAllJobsCompleted() {
        ProcessingJob job1 = ProcessingJob.builder().status(ProcessingJob.JobStatus.COMPLETED).file(fileEntity).build();
        when(processingJobRepository.findPage(eq(fileId), any())).thenReturn(List.of(job1));

        FileProcessingStatusResponse result = fileProcessingStatusService.getFileProcessingStatus(actorUserId, fileId);

        assertEquals(AggregateStatus.COMPLETED, result.getOverallStatus());
    }

    @Test
    void getFileProcessingStatus_ShouldReturnFailed_WhenAllJobsFailed() {
        ProcessingJob job1 = ProcessingJob.builder().status(ProcessingJob.JobStatus.FAILED).file(fileEntity).build();
        when(processingJobRepository.findPage(eq(fileId), any())).thenReturn(List.of(job1));

        FileProcessingStatusResponse result = fileProcessingStatusService.getFileProcessingStatus(actorUserId, fileId);

        assertEquals(AggregateStatus.FAILED, result.getOverallStatus());
    }

    @Test
    void getFileProcessingStatus_ShouldReturnPartialFailure_WhenMixed() {
        ProcessingJob job1 = ProcessingJob.builder().status(ProcessingJob.JobStatus.COMPLETED).file(fileEntity).build();
        ProcessingJob job2 = ProcessingJob.builder().status(ProcessingJob.JobStatus.FAILED).file(fileEntity).build();
        when(processingJobRepository.findPage(eq(fileId), any())).thenReturn(List.of(job1, job2));

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

    private ProcessingJob processingJob(UUID id, OffsetDateTime createdAt, ProcessingJob.JobType jobType) {
        return ProcessingJob.builder()
                .id(id)
                .file(fileEntity)
                .jobType(jobType)
                .status(ProcessingJob.JobStatus.PENDING)
                .createdAt(createdAt)
                .build();
    }
}

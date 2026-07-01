package com.diogotoporcov.filemanager.api.processing.application.status;

import com.diogotoporcov.filemanager.api.application.CursorPage;
import com.diogotoporcov.filemanager.api.auth.application.AccessControlService;
import com.diogotoporcov.filemanager.api.auth.domain.Permission;
import com.diogotoporcov.filemanager.api.file.domain.FileEntity;
import com.diogotoporcov.filemanager.api.processing.application.status.FileProcessingStatus.AggregateStatus;
import com.diogotoporcov.filemanager.api.processing.domain.ProcessingJob;
import com.diogotoporcov.filemanager.api.processing.persistence.ProcessingJobRepository;
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

        CursorPage<ProcessingJobStatus> result = fileProcessingStatusService.getProcessingJobs(
                actorUserId,
                fileId,
                ProcessingJobsPageRequest.of(null, null));

        assertEquals(1, result.items().size());
        assertEquals(job.getId(), result.items().getFirst().id());
        verify(accessControlService).assertCanAccessFile(actorUserId, fileId, Permission.FILE_VIEW);
    }

    @Test
    void getProcessingJobs_ShouldUseCursor_WhenCursorProvided() {
        OffsetDateTime cursorCreatedAt = OffsetDateTime.parse("2026-01-01T10:15:30Z");
        UUID cursorId = UUID.randomUUID();
        String cursor = ProcessingJobsPageRequest.encodeCursor(cursorCreatedAt, cursorId);
        ProcessingJob job = ProcessingJob.builder()
                .id(UUID.randomUUID())
                .file(fileEntity)
                .jobType(ProcessingJob.JobType.PHASH)
                .status(ProcessingJob.JobStatus.PENDING)
                .createdAt(cursorCreatedAt.plusSeconds(1))
                .build();
        when(processingJobRepository.findPageAfterCursor(eq(fileId), eq(cursorCreatedAt), eq(cursorId), any()))
                .thenReturn(List.of(job));

        CursorPage<ProcessingJobStatus> result = fileProcessingStatusService.getProcessingJobs(
                actorUserId,
                fileId,
                ProcessingJobsPageRequest.of(10, cursor));

        assertEquals(1, result.items().size());
        assertEquals(job.getId(), result.items().getFirst().id());
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

        CursorPage<ProcessingJobStatus> result = fileProcessingStatusService.getProcessingJobs(
                actorUserId,
                fileId,
                ProcessingJobsPageRequest.of(1, null));

        assertEquals(1, result.items().size());
        assertTrue(result.hasMore());
        assertEquals(ProcessingJobsPageRequest.encodeCursor(job1.getCreatedAt(), job1.getId()), result.nextCursor());
    }

    @Test
    void getFileProcessingStatus_ShouldReturnNotStarted_WhenNoJobs() {
        when(processingJobRepository.findPage(eq(fileId), any())).thenReturn(Collections.emptyList());

        FileProcessingStatus result = fileProcessingStatusService.getFileProcessingStatus(actorUserId, fileId);

        assertEquals(AggregateStatus.NOT_STARTED, result.overallStatus());
        assertTrue(result.jobs().isEmpty());
    }

    @Test
    void getFileProcessingStatus_ShouldReturnProcessing_WhenAnyJobPending() {
        ProcessingJob job1 = ProcessingJob.builder().status(ProcessingJob.JobStatus.COMPLETED).file(fileEntity).build();
        ProcessingJob job2 = ProcessingJob.builder().status(ProcessingJob.JobStatus.PENDING).file(fileEntity).build();
        when(processingJobRepository.findPage(eq(fileId), any())).thenReturn(List.of(job1, job2));

        FileProcessingStatus result = fileProcessingStatusService.getFileProcessingStatus(actorUserId, fileId);

        assertEquals(AggregateStatus.PROCESSING, result.overallStatus());
    }

    @Test
    void getFileProcessingStatus_ShouldReturnCompleted_WhenAllJobsCompleted() {
        ProcessingJob job1 = ProcessingJob.builder().status(ProcessingJob.JobStatus.COMPLETED).file(fileEntity).build();
        when(processingJobRepository.findPage(eq(fileId), any())).thenReturn(List.of(job1));

        FileProcessingStatus result = fileProcessingStatusService.getFileProcessingStatus(actorUserId, fileId);

        assertEquals(AggregateStatus.COMPLETED, result.overallStatus());
    }

    @Test
    void getFileProcessingStatus_ShouldReturnFailed_WhenAllJobsFailed() {
        ProcessingJob job1 = ProcessingJob.builder().status(ProcessingJob.JobStatus.FAILED).file(fileEntity).build();
        when(processingJobRepository.findPage(eq(fileId), any())).thenReturn(List.of(job1));

        FileProcessingStatus result = fileProcessingStatusService.getFileProcessingStatus(actorUserId, fileId);

        assertEquals(AggregateStatus.FAILED, result.overallStatus());
    }

    @Test
    void getFileProcessingStatus_ShouldReturnPartialFailure_WhenMixed() {
        ProcessingJob job1 = ProcessingJob.builder().status(ProcessingJob.JobStatus.COMPLETED).file(fileEntity).build();
        ProcessingJob job2 = ProcessingJob.builder().status(ProcessingJob.JobStatus.FAILED).file(fileEntity).build();
        when(processingJobRepository.findPage(eq(fileId), any())).thenReturn(List.of(job1, job2));

        FileProcessingStatus result = fileProcessingStatusService.getFileProcessingStatus(actorUserId, fileId);

        assertEquals(AggregateStatus.PARTIAL_FAILURE, result.overallStatus());
    }

    @Test
    void getProcessingJobs_ShouldPropagateAccessDenied() {
        doThrow(new com.diogotoporcov.filemanager.api.exception.AccessDeniedException("Denied"))
                .when(accessControlService).assertCanAccessFile(actorUserId, fileId, Permission.FILE_VIEW);

        // Act & Assert
        assertThrows(com.diogotoporcov.filemanager.api.exception.AccessDeniedException.class,
                () -> fileProcessingStatusService.getProcessingJobs(
                        actorUserId,
                        fileId,
                        ProcessingJobsPageRequest.of(null, null)));
    }

    @Test
    void getFileProcessingStatus_ShouldPropagateResourceNotFound() {
        doThrow(new com.diogotoporcov.filemanager.api.exception.ResourceNotFoundException("Not found"))
                .when(accessControlService).assertCanAccessFile(actorUserId, fileId, Permission.FILE_VIEW);

        // Act & Assert
        assertThrows(com.diogotoporcov.filemanager.api.exception.ResourceNotFoundException.class,
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

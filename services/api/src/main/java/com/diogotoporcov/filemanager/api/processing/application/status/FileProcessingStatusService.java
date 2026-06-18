package com.diogotoporcov.filemanager.api.processing.application.status;

import com.diogotoporcov.filemanager.api.auth.application.AccessControlService;
import com.diogotoporcov.filemanager.api.auth.domain.Permission;
import com.diogotoporcov.filemanager.api.processing.web.status.FileProcessingStatusResponse.AggregateStatus;
import com.diogotoporcov.filemanager.api.processing.web.status.FileProcessingStatusResponse;
import com.diogotoporcov.filemanager.api.processing.web.status.ProcessingJobResponse;
import com.diogotoporcov.filemanager.api.processing.domain.ProcessingJob;
import com.diogotoporcov.filemanager.api.processing.persistence.ProcessingJobRepository;
import com.diogotoporcov.filemanager.api.web.BoundedPageRequest.SeekCursor;
import com.diogotoporcov.filemanager.api.web.BoundedPageRequest;
import com.diogotoporcov.filemanager.api.web.CursorPageResponse;
import java.util.List;
import java.util.stream.Collectors;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FileProcessingStatusService {

    private final ProcessingJobRepository processingJobRepository;
    private final AccessControlService accessControlService;

    @Transactional(readOnly = true)
    public CursorPageResponse<ProcessingJobResponse> getProcessingJobs(
            UUID actorUserId,
            UUID fileId,
            BoundedPageRequest pageRequest) {
        accessControlService.assertCanAccessFile(actorUserId, fileId, Permission.FILE_VIEW);

        List<ProcessingJob> jobs = findProcessingJobPage(fileId, pageRequest);
        boolean hasMore = jobs.size() > pageRequest.size();
        List<ProcessingJob> pageJobs = hasMore ? jobs.subList(0, pageRequest.size()) : jobs;
        ProcessingJob last = pageJobs.isEmpty() ? null : pageJobs.getLast();

        return CursorPageResponse.<ProcessingJobResponse>builder()
                .items(pageJobs.stream()
                        .map(this::mapToResponse)
                        .collect(Collectors.toList()))
                .hasMore(hasMore)
                .nextCursor(nextJobCursor(hasMore, last))
                .pageSize(pageRequest.size())
                .build();
    }

    private List<ProcessingJob> findProcessingJobPage(UUID fileId, BoundedPageRequest pageRequest) {
        PageRequest pageable = PageRequest.of(0, pageRequest.fetchSize());
        SeekCursor cursor = pageRequest.decodedCursor();
        if (cursor == null) {
            return processingJobRepository.findPage(fileId, pageable);
        }

        return processingJobRepository.findPageAfterCursor(fileId, cursor.createdAt(), cursor.id(), pageable);
    }

    private String nextJobCursor(boolean hasMore, ProcessingJob last) {
        if (!hasMore || last == null) {
            return null;
        }

        return BoundedPageRequest.encodeCursor(last.getCreatedAt(), last.getId());
    }

    @Transactional(readOnly = true)
    public FileProcessingStatusResponse getFileProcessingStatus(UUID actorUserId, UUID fileId) {
        accessControlService.assertCanAccessFile(actorUserId, fileId, Permission.FILE_VIEW);

        List<ProcessingJob> jobs = processingJobRepository.findPage(
                fileId,
                PageRequest.of(0, BoundedPageRequest.MAX_SIZE));
        AggregateStatus overallStatus = calculateAggregateStatus(jobs);

        return FileProcessingStatusResponse.builder()
                .fileId(fileId)
                .overallStatus(overallStatus)
                .jobs(jobs.stream().map(this::mapToResponse).collect(Collectors.toList()))
                .build();
    }

    private AggregateStatus calculateAggregateStatus(List<ProcessingJob> jobs) {
        if (jobs.isEmpty()) {
            return AggregateStatus.NOT_STARTED;
        }

        boolean anyPendingOrInProgress = jobs.stream()
                .anyMatch(j -> j.getStatus() == ProcessingJob.JobStatus.PENDING || j.getStatus() == ProcessingJob.JobStatus.IN_PROGRESS);

        if (anyPendingOrInProgress) {
            return AggregateStatus.PROCESSING;
        }

        boolean allCompleted = jobs.stream().allMatch(j -> j.getStatus() == ProcessingJob.JobStatus.COMPLETED);
        if (allCompleted) {
            return AggregateStatus.COMPLETED;
        }

        boolean allFailed = jobs.stream().allMatch(j -> j.getStatus() == ProcessingJob.JobStatus.FAILED);
        if (allFailed) {
            return AggregateStatus.FAILED;
        }

        return AggregateStatus.PARTIAL_FAILURE;
    }

    private ProcessingJobResponse mapToResponse(ProcessingJob job) {
        return ProcessingJobResponse.builder()
                .id(job.getId())
                .fileId(job.getFile().getId())
                .jobType(job.getJobType())
                .status(job.getStatus())
                .errorMessage(job.getErrorMessage())
                .createdAt(job.getCreatedAt())
                .updatedAt(job.getUpdatedAt())
                .build();
    }
}

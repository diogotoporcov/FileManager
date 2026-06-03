package com.filemanager.api.service;

import com.filemanager.api.auth.AccessControlService;
import com.filemanager.api.auth.Permission;
import com.filemanager.api.dto.BoundedPageRequest;
import com.filemanager.api.dto.CursorPageResponse;
import com.filemanager.api.dto.FileProcessingStatusResponse;
import com.filemanager.api.dto.FileProcessingStatusResponse.AggregateStatus;
import com.filemanager.api.dto.ProcessingJobResponse;
import com.filemanager.api.entity.ProcessingJob;
import com.filemanager.api.repository.ProcessingJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

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

        List<ProcessingJob> jobs = processingJobRepository.findAllByFile_IdOrderByCreatedAtAsc(
                fileId,
                PageRequest.of(0, pageRequest.fetchSize()));
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

    private String nextJobCursor(boolean hasMore, ProcessingJob last) {
        if (!hasMore || last == null) {
            return null;
        }

        return BoundedPageRequest.encodeCursor(last.getCreatedAt(), last.getId());
    }

    @Transactional(readOnly = true)
    public FileProcessingStatusResponse getFileProcessingStatus(UUID actorUserId, UUID fileId) {
        accessControlService.assertCanAccessFile(actorUserId, fileId, Permission.FILE_VIEW);

        List<ProcessingJob> jobs = processingJobRepository.findAllByFile_IdOrderByCreatedAtAsc(
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

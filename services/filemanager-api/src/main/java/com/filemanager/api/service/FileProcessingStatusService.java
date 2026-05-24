package com.filemanager.api.service;

import com.filemanager.api.auth.AccessControlService;
import com.filemanager.api.auth.Permission;
import com.filemanager.api.dto.FileProcessingStatusResponse;
import com.filemanager.api.dto.FileProcessingStatusResponse.AggregateStatus;
import com.filemanager.api.dto.ProcessingJobResponse;
import com.filemanager.api.entity.DuplicateCandidate;
import com.filemanager.api.entity.ProcessingJob;
import com.filemanager.api.repository.DuplicateCandidateRepository;
import com.filemanager.api.repository.DuplicateCandidateSpecifications;
import com.filemanager.api.repository.ProcessingJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FileProcessingStatusService {

    private final ProcessingJobRepository processingJobRepository;
    private final DuplicateCandidateRepository duplicateCandidateRepository;
    private final AccessControlService accessControlService;

    @Transactional(readOnly = true)
    public List<ProcessingJobResponse> getProcessingJobs(UUID actorUserId, UUID fileId) {
        accessControlService.assertCanAccessFile(actorUserId, fileId, Permission.FILE_VIEW);

        return processingJobRepository.findAllByFile_IdOrderByCreatedAtAsc(fileId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public FileProcessingStatusResponse getFileProcessingStatus(UUID actorUserId, UUID fileId) {
        accessControlService.assertCanAccessFile(actorUserId, fileId, Permission.FILE_VIEW);

        List<ProcessingJob> jobs = processingJobRepository.findAllByFile_IdOrderByCreatedAtAsc(fileId);
        AggregateStatus overallStatus = calculateAggregateStatus(jobs);

        long totalDuplicates = countDuplicates(fileId, null, null);

        Map<String, Long> byMethod = Arrays.stream(DuplicateCandidate.DetectionMethod.values())
                .collect(Collectors.toMap(
                        Enum::name,
                        method -> countDuplicates(fileId, method, null)
                ));

        Map<String, Long> byStatus = Arrays.stream(DuplicateCandidate.CandidateStatus.values())
                .collect(Collectors.toMap(
                        Enum::name,
                        status -> countDuplicates(fileId, null, status)
                ));

        return FileProcessingStatusResponse.builder()
                .fileId(fileId)
                .overallStatus(overallStatus)
                .jobs(jobs.stream().map(this::mapToResponse).collect(Collectors.toList()))
                .totalDuplicateCandidates(totalDuplicates)
                .duplicateCandidatesByDetectionMethod(byMethod)
                .duplicateCandidatesByStatus(byStatus)
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

    private long countDuplicates(UUID fileId, DuplicateCandidate.DetectionMethod method, DuplicateCandidate.CandidateStatus status) {
        Specification<DuplicateCandidate> spec = Specification.where(DuplicateCandidateSpecifications.hasFileId(fileId))
                .and(DuplicateCandidateSpecifications.isNotDeleted());

        if (method != null) {
            spec = spec.and(DuplicateCandidateSpecifications.hasDetectionMethod(method));
        }
        if (status != null) {
            spec = spec.and(DuplicateCandidateSpecifications.hasStatus(status));
        }

        return duplicateCandidateRepository.count(spec);
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

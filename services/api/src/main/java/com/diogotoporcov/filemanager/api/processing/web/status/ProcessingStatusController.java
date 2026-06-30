package com.diogotoporcov.filemanager.api.processing.web.status;

import com.diogotoporcov.filemanager.api.application.CursorPage;
import com.diogotoporcov.filemanager.api.auth.application.CurrentUserService;
import com.diogotoporcov.filemanager.api.processing.application.status.FileProcessingStatus;
import com.diogotoporcov.filemanager.api.processing.application.status.FileProcessingStatusService;
import com.diogotoporcov.filemanager.api.processing.application.status.ProcessingJobsPageRequest;
import com.diogotoporcov.filemanager.api.processing.application.status.ProcessingJobStatus;
import com.diogotoporcov.filemanager.api.web.CursorPageResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
@Tag(name = "Processing Status", description = "Endpoints for monitoring file processing status and jobs")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "Unauthenticated - Invalid or missing JWT", content = @Content),
        @ApiResponse(responseCode = "403", description = "Forbidden - Insufficient permissions", content = @Content),
        @ApiResponse(responseCode = "404", description = "File not found", content = @Content),
        @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content)
})
public class ProcessingStatusController {

    private final FileProcessingStatusService fileProcessingStatusService;
    private final CurrentUserService currentUserService;

    @Operation(summary = "Get processing jobs", description = "Lists all processing jobs (checksum, phash, etc.) for a specific file.")
    @ApiResponse(responseCode = "200", description = "List of processing jobs")
    @GetMapping("/{fileId}/processing-jobs")
    public CursorPageResponse<ProcessingJobResponse> getProcessingJobs(
            @Parameter(description = "ID of the file") @PathVariable UUID fileId,
            @Parameter(description = "Maximum jobs to return") @org.springframework.web.bind.annotation.RequestParam(required = false) Integer size,
            @Parameter(description = "Cursor returned by the previous page") @org.springframework.web.bind.annotation.RequestParam(required = false) String cursor) {
        UUID actorUserId = currentUserService.getCurrentUserId();

        return toResponsePage(fileProcessingStatusService.getProcessingJobs(actorUserId, fileId, ProcessingJobsPageRequest.of(size, cursor)));
    }

    @Operation(summary = "Get file processing status", description = "Retrieves an aggregated status of all processing tasks for a file.")
    @ApiResponse(responseCode = "200", description = "Aggregated processing status")
    @GetMapping("/{fileId}/processing-status")
    public FileProcessingStatusResponse getFileProcessingStatus(@Parameter(description = "ID of the file") @PathVariable UUID fileId) {
        UUID actorUserId = currentUserService.getCurrentUserId();

        return toResponse(fileProcessingStatusService.getFileProcessingStatus(actorUserId, fileId));
    }

    private CursorPageResponse<ProcessingJobResponse> toResponsePage(CursorPage<ProcessingJobStatus> page) {
        return CursorPageResponse.<ProcessingJobResponse>builder()
                .items(page.items().stream().map(this::toResponse).toList())
                .nextCursor(page.nextCursor())
                .hasMore(page.hasMore())
                .pageSize(page.pageSize())
                .build();
    }

    private FileProcessingStatusResponse toResponse(FileProcessingStatus status) {
        return FileProcessingStatusResponse.builder()
                .fileId(status.fileId())
                .overallStatus(FileProcessingStatusResponse.AggregateStatus.valueOf(status.overallStatus().name()))
                .jobs(status.jobs().stream().map(this::toResponse).toList())
                .build();
    }

    private ProcessingJobResponse toResponse(ProcessingJobStatus status) {
        return ProcessingJobResponse.builder()
                .id(status.id())
                .fileId(status.fileId())
                .jobType(status.jobType())
                .status(status.status())
                .errorMessage(status.errorMessage())
                .createdAt(status.createdAt())
                .updatedAt(status.updatedAt())
                .build();
    }
}

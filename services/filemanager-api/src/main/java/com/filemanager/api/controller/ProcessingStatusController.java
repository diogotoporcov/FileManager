package com.filemanager.api.controller;

import com.filemanager.api.auth.CurrentUserService;
import com.filemanager.api.dto.FileProcessingStatusResponse;
import com.filemanager.api.dto.ProcessingJobResponse;
import com.filemanager.api.service.FileProcessingStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

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
    public List<ProcessingJobResponse> getProcessingJobs(@Parameter(description = "ID of the file") @PathVariable UUID fileId) {
        UUID actorUserId = currentUserService.getCurrentUserId();
        return fileProcessingStatusService.getProcessingJobs(actorUserId, fileId);
    }

    @Operation(summary = "Get file processing status", description = "Retrieves an aggregated status of all processing tasks for a file.")
    @ApiResponse(responseCode = "200", description = "Aggregated processing status")
    @GetMapping("/{fileId}/processing-status")
    public FileProcessingStatusResponse getFileProcessingStatus(@Parameter(description = "ID of the file") @PathVariable UUID fileId) {
        UUID actorUserId = currentUserService.getCurrentUserId();
        return fileProcessingStatusService.getFileProcessingStatus(actorUserId, fileId);
    }
}

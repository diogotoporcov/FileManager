package com.filemanager.api.controller;

import com.filemanager.api.auth.CurrentUserService;
import com.filemanager.api.dto.DuplicateCandidateResponse;
import com.filemanager.api.dto.DuplicateStatusUpdateRequest;
import com.filemanager.api.dto.FileDuplicateResponse;
import com.filemanager.api.entity.DuplicateCandidate.CandidateStatus;
import com.filemanager.api.entity.DuplicateCandidate.DetectionMethod;
import com.filemanager.api.service.DuplicateCandidateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Duplicate Management", description = "Endpoints for managing duplicate file candidates")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "Unauthenticated - Invalid or missing JWT", content = @Content),
        @ApiResponse(responseCode = "403", description = "Forbidden - Insufficient permissions", content = @Content),
        @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content)
})
public class DuplicateCandidateController {

    private final DuplicateCandidateService duplicateCandidateService;
    private final CurrentUserService currentUserService;

    @Operation(summary = "Get duplicates for a file", description = "Retrieves potential duplicates for a specific file.")
    @ApiResponse(responseCode = "200", description = "List of potential duplicates")
    @GetMapping("/files/{fileId}/duplicates")
    public List<FileDuplicateResponse> getDuplicatesForFile(
            @Parameter(description = "ID of the file") @PathVariable UUID fileId,
            @Parameter(description = "Filter by owner User ID") @RequestParam(required = false) UUID ownerUserId,
            @Parameter(description = "Filter by owner Organization ID") @RequestParam(required = false) UUID ownerOrganizationId,
            @Parameter(description = "Filter by detection method") @RequestParam(required = false) DetectionMethod detectionMethod,
            @Parameter(description = "Filter by status") @RequestParam(required = false) CandidateStatus status) {
        UUID actorUserId = currentUserService.getCurrentUserId();
        return duplicateCandidateService.getDuplicatesForFile(
                fileId, ownerUserId, ownerOrganizationId, detectionMethod, status, actorUserId);
    }

    @Operation(summary = "Get all duplicate candidates", description = "Lists all duplicate candidates based on ownership filters.")
    @ApiResponse(responseCode = "200", description = "List of duplicate candidates")
    @GetMapping("/duplicate-candidates")
    public List<DuplicateCandidateResponse> getDuplicatesForOwner(
            @Parameter(description = "Filter by owner User ID") @RequestParam(required = false) UUID ownerUserId,
            @Parameter(description = "Filter by owner Organization ID") @RequestParam(required = false) UUID ownerOrganizationId,
            @Parameter(description = "Filter by detection method") @RequestParam(required = false) DetectionMethod detectionMethod,
            @Parameter(description = "Filter by status") @RequestParam(required = false) CandidateStatus status) {
        UUID actorUserId = currentUserService.getCurrentUserId();
        return duplicateCandidateService.getDuplicatesForOwner(
                ownerUserId, ownerOrganizationId, detectionMethod, status, actorUserId);
    }

    @Operation(summary = "Update duplicate status", description = "Updates the status of a specific duplicate candidate (e.g., PENDING, CONFIRMED, REJECTED).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Status updated successfully"),
            @ApiResponse(responseCode = "404", description = "Candidate not found", content = @Content)
    })
    @PatchMapping("/duplicate-candidates/{candidateId}/status")
    public DuplicateCandidateResponse updateStatus(
            @Parameter(description = "ID of the duplicate candidate") @PathVariable UUID candidateId,
            @Parameter(description = "Filter by owner User ID") @RequestParam(required = false) UUID ownerUserId,
            @Parameter(description = "Filter by owner Organization ID") @RequestParam(required = false) UUID ownerOrganizationId,
            @Valid @RequestBody DuplicateStatusUpdateRequest request) {
        UUID actorUserId = currentUserService.getCurrentUserId();
        return duplicateCandidateService.updateStatus(
                candidateId, ownerUserId, ownerOrganizationId, request.getStatus(), actorUserId);
    }
}

package com.filemanager.api.controller;

import com.filemanager.api.auth.CurrentUserService;
import com.filemanager.api.dto.BoundedOffsetPageRequest;
import com.filemanager.api.dto.BoundedPageRequest;
import com.filemanager.api.dto.CursorPageResponse;
import com.filemanager.api.dto.DuplicateCandidateResponse;
import com.filemanager.api.dto.DuplicateGroupResponse;
import com.filemanager.api.dto.DuplicateSearchMethod;
import com.filemanager.api.dto.DuplicateStatusUpdateRequest;
import com.filemanager.api.dto.FileDuplicateSearchResponse;
import com.filemanager.api.dto.PageResponse;
import com.filemanager.api.entity.DuplicateCandidate.CandidateStatus;
import com.filemanager.api.entity.DuplicateCandidate.DetectionMethod;
import com.filemanager.api.service.DuplicateCandidateService;
import com.filemanager.api.service.DuplicateSearchService;
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
    private final DuplicateSearchService duplicateSearchService;
    private final CurrentUserService currentUserService;

    @Operation(summary = "Search duplicates for a file", description = "Computes duplicate matches for a specific file on demand.")
    @ApiResponse(responseCode = "200", description = "On-demand duplicate matches")
    @GetMapping("/files/{fileId}/duplicates")
    public FileDuplicateSearchResponse getDuplicatesForFile(
            @Parameter(description = "ID of the file") @PathVariable UUID fileId,
            @Parameter(description = "Detection methods to run") @RequestParam(required = false, name = "method") List<DuplicateSearchMethod> methods,
            @Parameter(description = "Maximum items to return") @RequestParam(required = false) Integer size,
            @Parameter(description = "Cursor returned by the previous page") @RequestParam(required = false) String cursor) {
        UUID actorUserId = currentUserService.getCurrentUserId();
        return duplicateSearchService.findDuplicatesForFile(fileId, actorUserId, methods, BoundedPageRequest.of(size, cursor));
    }

    @Operation(summary = "Search duplicate groups", description = "Computes duplicate groups for an ownership context on demand.")
    @ApiResponse(responseCode = "200", description = "On-demand duplicate groups")
    @GetMapping("/duplicates")
    public CursorPageResponse<DuplicateGroupResponse> getDuplicateGroups(
            @Parameter(description = "Filter by owner User ID") @RequestParam(required = false) UUID ownerUserId,
            @Parameter(description = "Filter by owner Organization ID") @RequestParam(required = false) UUID ownerOrganizationId,
            @Parameter(description = "Detection methods to run") @RequestParam(required = false, name = "method") List<DuplicateSearchMethod> methods,
            @Parameter(description = "Maximum groups to return") @RequestParam(required = false) Integer size,
            @Parameter(description = "Cursor returned by the previous page") @RequestParam(required = false) String cursor) {
        UUID actorUserId = currentUserService.getCurrentUserId();
        return duplicateSearchService.findDuplicateGroups(
                ownerUserId,
                ownerOrganizationId,
                methods,
                actorUserId,
                BoundedPageRequest.of(size, cursor));
    }

    @Operation(summary = "Get persisted duplicate candidates", description = "Lists persisted duplicate candidate state based on ownership filters.")
    @ApiResponse(responseCode = "200", description = "List of duplicate candidates")
    @GetMapping("/duplicate-candidates")
    public PageResponse<DuplicateCandidateResponse> getDuplicatesForOwner(
            @Parameter(description = "Filter by owner User ID") @RequestParam(required = false) UUID ownerUserId,
            @Parameter(description = "Filter by owner Organization ID") @RequestParam(required = false) UUID ownerOrganizationId,
            @Parameter(description = "Filter by detection method") @RequestParam(required = false) DetectionMethod detectionMethod,
            @Parameter(description = "Filter by status") @RequestParam(required = false) CandidateStatus status,
            @Parameter(description = "Page index") @RequestParam(required = false) Integer page,
            @Parameter(description = "Maximum items to return") @RequestParam(required = false) Integer size) {
        UUID actorUserId = currentUserService.getCurrentUserId();
        return duplicateCandidateService.getDuplicatesForOwner(
                ownerUserId,
                ownerOrganizationId,
                detectionMethod,
                status,
                actorUserId,
                BoundedOffsetPageRequest.of(page, size));
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

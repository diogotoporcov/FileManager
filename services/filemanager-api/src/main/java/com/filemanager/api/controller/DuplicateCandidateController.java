package com.filemanager.api.controller;

import com.filemanager.api.dto.*;
import com.filemanager.api.entity.DuplicateCandidate.CandidateStatus;
import com.filemanager.api.entity.DuplicateCandidate.DetectionMethod;
import com.filemanager.api.service.DuplicateCandidateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class DuplicateCandidateController {

    private final DuplicateCandidateService duplicateCandidateService;

    @GetMapping("/files/{fileId}/duplicates")
    public List<FileDuplicateResponse> getDuplicatesForFile(
            @PathVariable UUID fileId,
            @RequestParam(required = false) UUID ownerUserId,
            @RequestParam(required = false) UUID ownerOrganizationId,
            @RequestParam(required = false) DetectionMethod detectionMethod,
            @RequestParam(required = false) CandidateStatus status) {
        return duplicateCandidateService.getDuplicatesForFile(
                fileId, ownerUserId, ownerOrganizationId, detectionMethod, status);
    }

    @GetMapping("/duplicate-candidates")
    public List<DuplicateCandidateResponse> getDuplicatesForOwner(
            @RequestParam(required = false) UUID ownerUserId,
            @RequestParam(required = false) UUID ownerOrganizationId,
            @RequestParam(required = false) DetectionMethod detectionMethod,
            @RequestParam(required = false) CandidateStatus status) {
        return duplicateCandidateService.getDuplicatesForOwner(
                ownerUserId, ownerOrganizationId, detectionMethod, status);
    }

    @PatchMapping("/duplicate-candidates/{candidateId}/status")
    public DuplicateCandidateResponse updateStatus(
            @PathVariable UUID candidateId,
            @RequestParam(required = false) UUID ownerUserId,
            @RequestParam(required = false) UUID ownerOrganizationId,
            @Valid @RequestBody DuplicateStatusUpdateRequest request) {
        return duplicateCandidateService.updateStatus(
                candidateId, ownerUserId, ownerOrganizationId, request.getStatus());
    }
}

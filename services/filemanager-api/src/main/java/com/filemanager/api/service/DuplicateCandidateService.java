package com.filemanager.api.service;

import com.filemanager.api.dto.*;
import com.filemanager.api.entity.DuplicateCandidate;
import com.filemanager.api.entity.DuplicateCandidate.CandidateStatus;
import com.filemanager.api.entity.DuplicateCandidate.DetectionMethod;
import com.filemanager.api.entity.FileEntity;
import com.filemanager.api.exception.ResourceNotFoundException;
import com.filemanager.api.repository.DuplicateCandidateRepository;
import com.filemanager.api.repository.DuplicateCandidateSpecifications;
import com.filemanager.api.repository.FileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DuplicateCandidateService {

    private final DuplicateCandidateRepository duplicateCandidateRepository;
    private final FileRepository fileRepository;

    @Transactional(readOnly = true)
    public List<FileDuplicateResponse> getDuplicatesForFile(
            UUID fileId, UUID ownerUserId, UUID ownerOrganizationId,
            DetectionMethod method, CandidateStatus status) {
        
        validateOwnerContext(ownerUserId, ownerOrganizationId);
        
        FileEntity file = fileRepository.findByIdAndDeletedAtIsNull(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found: " + fileId));
        
        verifyFileOwnership(file, ownerUserId, ownerOrganizationId);

        Specification<DuplicateCandidate> spec = Specification.where(DuplicateCandidateSpecifications.hasFileId(fileId))
                .and(DuplicateCandidateSpecifications.isNotDeleted())
                .and(DuplicateCandidateSpecifications.hasDetectionMethod(method))
                .and(DuplicateCandidateSpecifications.hasStatus(status));

        List<DuplicateCandidate> candidates = duplicateCandidateRepository.findAll(spec);

        return candidates.stream()
                .map(dc -> mapToFileDuplicateResponse(dc, fileId))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<DuplicateCandidateResponse> getDuplicatesForOwner(
            UUID ownerUserId, UUID ownerOrganizationId,
            DetectionMethod method, CandidateStatus status) {
        
        validateOwnerContext(ownerUserId, ownerOrganizationId);

        Specification<DuplicateCandidate> spec = Specification.where(DuplicateCandidateSpecifications.isNotDeleted())
                .and(DuplicateCandidateSpecifications.hasDetectionMethod(method))
                .and(DuplicateCandidateSpecifications.hasStatus(status));

        if (ownerUserId != null) {
            spec = spec.and(DuplicateCandidateSpecifications.hasOwnerUserId(ownerUserId));
        } else {
            spec = spec.and(DuplicateCandidateSpecifications.hasOwnerOrganizationId(ownerOrganizationId));
        }

        List<DuplicateCandidate> candidates = duplicateCandidateRepository.findAll(spec);

        return candidates.stream()
                .map(this::mapToDuplicateCandidateResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public DuplicateCandidateResponse updateStatus(
            UUID candidateId, UUID ownerUserId, UUID ownerOrganizationId, CandidateStatus status) {
        
        validateOwnerContext(ownerUserId, ownerOrganizationId);

        DuplicateCandidate dc = duplicateCandidateRepository.findById(candidateId)
                .orElseThrow(() -> new ResourceNotFoundException("Duplicate candidate not found: " + candidateId));

        // Verify that the owner has access to at least one of the files in the pair
        boolean hasAccess = false;
        if (ownerUserId != null) {
            hasAccess = (dc.getSourceFile().getOwnerUser() != null && dc.getSourceFile().getOwnerUser().getId().equals(ownerUserId)) ||
                        (dc.getCandidateFile().getOwnerUser() != null && dc.getCandidateFile().getOwnerUser().getId().equals(ownerUserId));
        } else {
            hasAccess = (dc.getSourceFile().getOwnerOrganization() != null && dc.getSourceFile().getOwnerOrganization().getId().equals(ownerOrganizationId)) ||
                        (dc.getCandidateFile().getOwnerOrganization() != null && dc.getCandidateFile().getOwnerOrganization().getId().equals(ownerOrganizationId));
        }

        if (!hasAccess) {
            throw new ResourceNotFoundException("Duplicate candidate not found: " + candidateId);
        }

        dc.setStatus(status);
        return mapToDuplicateCandidateResponse(duplicateCandidateRepository.save(dc));
    }

    private void validateOwnerContext(UUID ownerUserId, UUID ownerOrganizationId) {
        if ((ownerUserId == null && ownerOrganizationId == null) || (ownerUserId != null && ownerOrganizationId != null)) {
            throw new IllegalArgumentException("Exactly one of ownerUserId or ownerOrganizationId must be provided");
        }
    }

    private void verifyFileOwnership(FileEntity file, UUID ownerUserId, UUID ownerOrganizationId) {
        if (ownerUserId != null) {
            if (file.getOwnerUser() == null || !file.getOwnerUser().getId().equals(ownerUserId)) {
                throw new ResourceNotFoundException("File not found: " + file.getId());
            }
        } else {
            if (file.getOwnerOrganization() == null || !file.getOwnerOrganization().getId().equals(ownerOrganizationId)) {
                throw new ResourceNotFoundException("File not found: " + file.getId());
            }
        }
    }

    private FileDuplicateResponse mapToFileDuplicateResponse(DuplicateCandidate dc, UUID requestedFileId) {
        FileEntity requestedFile;
        FileEntity duplicateFile;

        if (dc.getSourceFile().getId().equals(requestedFileId)) {
            requestedFile = dc.getSourceFile();
            duplicateFile = dc.getCandidateFile();
        } else {
            requestedFile = dc.getCandidateFile();
            duplicateFile = dc.getSourceFile();
        }

        return FileDuplicateResponse.builder()
                .id(dc.getId())
                .requestedFile(mapToFileSummary(requestedFile))
                .duplicateFile(mapToFileSummary(duplicateFile))
                .detectionMethod(dc.getDetectionMethod())
                .distance(dc.getDistance())
                .confidenceScore(dc.getConfidenceScore())
                .status(dc.getStatus())
                .createdAt(dc.getCreatedAt())
                .build();
    }

    private DuplicateCandidateResponse mapToDuplicateCandidateResponse(DuplicateCandidate dc) {
        return DuplicateCandidateResponse.builder()
                .id(dc.getId())
                .sourceFile(mapToFileSummary(dc.getSourceFile()))
                .candidateFile(mapToFileSummary(dc.getCandidateFile()))
                .detectionMethod(dc.getDetectionMethod())
                .distance(dc.getDistance())
                .confidenceScore(dc.getConfidenceScore())
                .status(dc.getStatus())
                .createdAt(dc.getCreatedAt())
                .build();
    }

    private FileSummaryResponse mapToFileSummary(FileEntity file) {
        return FileSummaryResponse.builder()
                .id(file.getId())
                .name(file.getName())
                .mimeType(file.getMimeType())
                .size(file.getSize())
                .createdAt(file.getCreatedAt())
                .updatedAt(file.getUpdatedAt())
                .build();
    }
}

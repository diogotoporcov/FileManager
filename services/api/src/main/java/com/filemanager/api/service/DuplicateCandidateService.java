package com.filemanager.api.service;

import com.filemanager.api.auth.AccessControlService;
import com.filemanager.api.auth.Permission;
import com.filemanager.api.dto.DuplicateCandidateResponse;
import com.filemanager.api.dto.FileDuplicateResponse;
import com.filemanager.api.dto.FileSummaryResponse;
import com.filemanager.api.entity.DuplicateCandidate;
import com.filemanager.api.entity.DuplicateCandidate.CandidateStatus;
import com.filemanager.api.entity.DuplicateCandidate.DetectionMethod;
import com.filemanager.api.entity.FileEntity;
import com.filemanager.api.exception.ResourceNotFoundException;
import com.filemanager.api.port.DuplicateCandidateSearchPort;
import com.filemanager.api.port.DuplicateCandidateSearchRequest;
import com.filemanager.api.repository.DuplicateCandidateRepository;
import com.filemanager.api.repository.FileRepository;
import lombok.RequiredArgsConstructor;
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
    private final AccessControlService accessControlService;
    private final DuplicateCandidateSearchPort duplicateCandidateSearchPort;

    @Transactional(readOnly = true)
    public List<FileDuplicateResponse> getDuplicatesForFile(
            UUID fileId, UUID ownerUserId, UUID ownerOrganizationId,
            DetectionMethod method, CandidateStatus status, UUID actorUserId) {
        
        accessControlService.assertCanViewDuplicates(actorUserId, ownerUserId, ownerOrganizationId);
        accessControlService.assertCanAccessFile(actorUserId, fileId, Permission.DUPLICATE_VIEW);
        
        validateOwnerContext(ownerUserId, ownerOrganizationId);
        
        FileEntity file = fileRepository.findByIdAndDeletedAtIsNull(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found: " + fileId));
        
        verifyFileOwnership(file, ownerUserId, ownerOrganizationId);

        List<DuplicateCandidate> candidates = duplicateCandidateSearchPort.search(new DuplicateCandidateSearchRequest(
                fileId,
                ownerUserId,
                ownerOrganizationId,
                method,
                status
        ));

        return candidates.stream()
                .map(dc -> mapToFileDuplicateResponse(dc, fileId))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<DuplicateCandidateResponse> getDuplicatesForOwner(
            UUID ownerUserId, UUID ownerOrganizationId,
            DetectionMethod method, CandidateStatus status, UUID actorUserId) {
        
        accessControlService.assertCanViewDuplicates(actorUserId, ownerUserId, ownerOrganizationId);
        validateOwnerContext(ownerUserId, ownerOrganizationId);

        List<DuplicateCandidate> candidates = duplicateCandidateSearchPort.search(new DuplicateCandidateSearchRequest(
                null,
                ownerUserId,
                ownerOrganizationId,
                method,
                status
        ));

        return candidates.stream()
                .map(this::mapToDuplicateCandidateResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public DuplicateCandidateResponse updateStatus(
            UUID candidateId, UUID ownerUserId, UUID ownerOrganizationId, CandidateStatus status, UUID actorUserId) {
        
        accessControlService.assertCanManageDuplicate(actorUserId, candidateId);
        validateOwnerContext(ownerUserId, ownerOrganizationId);

        DuplicateCandidate dc = duplicateCandidateRepository.findById(candidateId)
                .orElseThrow(() -> new ResourceNotFoundException("Duplicate candidate not found: " + candidateId));

        if (!hasOwnershipOfCandidate(dc, ownerUserId, ownerOrganizationId)) {
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
        boolean owned = (ownerUserId != null) ? isFileOwnedByUser(file, ownerUserId) : isFileOwnedByOrganization(file, ownerOrganizationId);
        if (!owned) {
            throw new ResourceNotFoundException("File not found: " + file.getId());
        }
    }


    private boolean hasOwnershipOfCandidate(DuplicateCandidate dc, UUID ownerUserId, UUID ownerOrganizationId) {
        if (ownerUserId != null) {
            return isFileOwnedByUser(dc.getSourceFile(), ownerUserId) &&
                   isFileOwnedByUser(dc.getCandidateFile(), ownerUserId);
        }

        return isFileOwnedByOrganization(dc.getSourceFile(), ownerOrganizationId) &&
               isFileOwnedByOrganization(dc.getCandidateFile(), ownerOrganizationId);
    }

    private boolean isFileOwnedByUser(FileEntity file, UUID userId) {
        return file.getOwnerUser() != null && file.getOwnerUser().getId().equals(userId);
    }

    private boolean isFileOwnedByOrganization(FileEntity file, UUID organizationId) {
        return file.getOwnerOrganization() != null && file.getOwnerOrganization().getId().equals(organizationId);
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

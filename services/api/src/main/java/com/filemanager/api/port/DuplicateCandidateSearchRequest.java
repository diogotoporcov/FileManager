package com.filemanager.api.port;

import com.filemanager.api.entity.DuplicateCandidate.CandidateStatus;
import com.filemanager.api.entity.DuplicateCandidate.DetectionMethod;

import java.util.UUID;

public record DuplicateCandidateSearchRequest(
        UUID fileId,
        UUID ownerUserId,
        UUID ownerOrganizationId,
        DetectionMethod method,
        CandidateStatus status
) {
}

package com.filemanager.api.duplicate.web;

import com.filemanager.api.duplicate.domain.DuplicateConfidence;
import com.filemanager.api.duplicate.domain.DuplicateEvidenceType;
import com.filemanager.api.duplicate.domain.DuplicateMethodStatus;
import com.filemanager.api.duplicate.domain.DuplicateSearchMethod;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Builder;

public record DuplicateGroupSearchResponse(
        List<DuplicateGroupMethodResultResponse> methods,
        String nextCursor
) {
    @Builder
    public record DuplicateGroupMethodResultResponse(
            DuplicateSearchMethod method,
            DuplicateMethodStatus status,
            List<DuplicateGroupResponse> groups
    ) {
    }

    @Builder
    public record DuplicateGroupResponse(
            String groupId,
            DuplicateConfidence confidence,
            UUID representativeFileId,
            List<DuplicateGroupFileResponse> files,
            List<DuplicateGroupEvidenceResponse> evidence
    ) {
    }

    @Builder
    public record DuplicateGroupFileResponse(
            UUID fileId,
            String name,
            String mimeType,
            Long size
    ) {
    }

    @Builder
    public record DuplicateGroupEvidenceResponse(
            DuplicateEvidenceType type,
            double score,
            Map<String, Object> details
    ) {
    }
}
package com.diogotoporcov.filemanager.api.duplicate.web;

import com.diogotoporcov.filemanager.api.duplicate.domain.DuplicateConfidence;
import com.diogotoporcov.filemanager.api.duplicate.domain.DuplicateEvidenceType;
import com.diogotoporcov.filemanager.api.duplicate.domain.DuplicateMethodStatus;
import com.diogotoporcov.filemanager.api.duplicate.domain.DuplicateSearchMethod;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Builder;

public record DuplicateSearchResponse(
        UUID sourceFileId,
        List<DuplicateMethodResultResponse> methods
) {
    @Builder
    public record DuplicateMethodResultResponse(
            DuplicateSearchMethod method,
            DuplicateMethodStatus status,
            List<DuplicateMatchResponse> matches,
            int pageSize,
            boolean hasMore,
            String nextCursor
    ) {
    }

    @Builder
    public record DuplicateMatchResponse(
            UUID fileId,
            DuplicateConfidence confidence,
            double score,
            List<DuplicateEvidenceResponse> evidence
    ) {
    }

    @Builder
    public record DuplicateEvidenceResponse(
            DuplicateEvidenceType type,
            double score,
            Map<String, Object> details
    ) {
    }
}

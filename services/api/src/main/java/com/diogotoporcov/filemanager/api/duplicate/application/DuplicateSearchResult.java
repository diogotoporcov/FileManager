package com.diogotoporcov.filemanager.api.duplicate.application;

import com.diogotoporcov.filemanager.api.duplicate.domain.DuplicateConfidence;
import com.diogotoporcov.filemanager.api.duplicate.domain.DuplicateEvidenceType;
import com.diogotoporcov.filemanager.api.duplicate.domain.DuplicateMethodStatus;
import com.diogotoporcov.filemanager.api.duplicate.domain.DuplicateSearchMethod;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Builder;

public record DuplicateSearchResult(
        UUID sourceFileId,
        List<DuplicateMethodResult> methods
) {
    @Builder
    public record DuplicateMethodResult(
            DuplicateSearchMethod method,
            DuplicateMethodStatus status,
            List<DuplicateMatchResult> matches,
            int pageSize,
            boolean hasMore,
            String nextCursor
    ) {
    }

    @Builder
    public record DuplicateMatchResult(
            UUID fileId,
            DuplicateConfidence confidence,
            double score,
            List<DuplicateEvidenceResult> evidence
    ) {
    }

    @Builder
    public record DuplicateEvidenceResult(
            DuplicateEvidenceType type,
            double score,
            Map<String, Object> details
    ) {
    }
}

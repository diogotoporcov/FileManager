package com.diogotoporcov.filemanager.api.duplicate.application;

import com.diogotoporcov.filemanager.api.duplicate.domain.DuplicateConfidence;
import com.diogotoporcov.filemanager.api.duplicate.domain.DuplicateEvidenceType;
import com.diogotoporcov.filemanager.api.duplicate.domain.DuplicateMethodStatus;
import com.diogotoporcov.filemanager.api.duplicate.domain.DuplicateSearchMethod;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Builder;

public record DuplicateGroupSearchResult(
        List<DuplicateGroupMethodResult> methods,
        String nextCursor
) {
    @Builder
    public record DuplicateGroupMethodResult(
            DuplicateSearchMethod method,
            DuplicateMethodStatus status,
            List<DuplicateGroupResult> groups
    ) {
    }

    @Builder
    public record DuplicateGroupResult(
            String groupId,
            DuplicateConfidence confidence,
            UUID representativeFileId,
            List<DuplicateGroupFileResult> files,
            List<DuplicateGroupEvidenceResult> evidence
    ) {
    }

    @Builder
    public record DuplicateGroupFileResult(
            UUID fileId,
            String name,
            String mimeType,
            Long size
    ) {
    }

    @Builder
    public record DuplicateGroupEvidenceResult(
            DuplicateEvidenceType type,
            double score,
            Map<String, Object> details
    ) {
    }
}

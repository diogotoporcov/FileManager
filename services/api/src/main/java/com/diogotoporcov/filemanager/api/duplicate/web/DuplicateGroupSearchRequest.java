package com.diogotoporcov.filemanager.api.duplicate.web;

import com.diogotoporcov.filemanager.api.duplicate.domain.DuplicateConfidence;
import com.diogotoporcov.filemanager.api.duplicate.domain.DuplicateSearchMethod;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;

public record DuplicateGroupSearchRequest(
        List<DuplicateSearchMethod> methods,
        @Min(1) @Max(100) Integer limit,
        String cursor,
        UUID folderId,
        String mimeType,
        DuplicateConfidence minConfidence
) {
}
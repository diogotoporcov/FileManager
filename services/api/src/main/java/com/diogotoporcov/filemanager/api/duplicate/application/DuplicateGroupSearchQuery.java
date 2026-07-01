package com.diogotoporcov.filemanager.api.duplicate.application;

import com.diogotoporcov.filemanager.api.duplicate.domain.DuplicateConfidence;
import com.diogotoporcov.filemanager.api.duplicate.domain.DuplicateSearchMethod;
import java.util.List;
import java.util.UUID;

public record DuplicateGroupSearchQuery(
        List<DuplicateSearchMethod> methods,
        Integer limit,
        UUID folderId,
        String mimeType,
        DuplicateConfidence minConfidence) {
}

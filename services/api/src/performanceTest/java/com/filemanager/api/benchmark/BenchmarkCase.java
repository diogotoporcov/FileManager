package com.filemanager.api.benchmark;

import com.filemanager.api.duplicate.domain.DuplicateSearchMethod;
import java.util.List;
import java.util.UUID;

record BenchmarkCase(
        String name,
        UUID sourceFileId,
        DuplicateSearchMethod method,
        String hashValue,
        List<UUID> expectedMatches) {
}

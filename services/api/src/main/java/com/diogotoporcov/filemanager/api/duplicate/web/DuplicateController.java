package com.diogotoporcov.filemanager.api.duplicate.web;

import com.diogotoporcov.filemanager.api.auth.application.CurrentUserProvider;
import com.diogotoporcov.filemanager.api.duplicate.application.DuplicateGroupSearchQuery;
import com.diogotoporcov.filemanager.api.duplicate.application.DuplicateGroupSearchResult;
import com.diogotoporcov.filemanager.api.duplicate.application.DuplicateSearchPage;
import com.diogotoporcov.filemanager.api.duplicate.application.DuplicateSearchResult;
import com.diogotoporcov.filemanager.api.duplicate.application.DuplicateSearchService;
import com.diogotoporcov.filemanager.api.duplicate.domain.DuplicateSearchMethod;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Duplicate Detection", description = "Read-only owner-only duplicate detection endpoints")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "401", description = "Unauthenticated"),
        @ApiResponse(responseCode = "403", description = "Only the owning user can run duplicate detection"),
        @ApiResponse(responseCode = "404", description = "File not found")
})
public class DuplicateController {
    private final DuplicateSearchService duplicateSearchService;
    private final CurrentUserProvider currentUserProvider;

    @Operation(summary = "Find duplicates for one owned source file")
    @GetMapping("/files/{fileId}/duplicates")
    public DuplicateSearchResponse findDuplicatesForFile(
            @Parameter(description = "ID of the source file") @PathVariable UUID fileId,
            @Parameter(description = "Comma-separated duplicate search methods")
            @RequestParam(value = "methods", required = false) String methods,
            @Parameter(description = "Maximum duplicate candidates to return per method page")
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @Parameter(description = "Opaque per-method pagination cursor from a previous response")
            @RequestParam(value = "cursor", required = false) String cursor) {
        List<DuplicateSearchMethod> parsedMethods = parseMethods(methods);
        validateCursorRequest(cursor, parsedMethods);
        UUID actorUserId = currentUserProvider.getCurrentUserId();

        return toResponse(duplicateSearchService.searchDuplicatesForFile(
                fileId,
                parsedMethods,
                actorUserId,
                new DuplicateSearchPage(pageSize, cursor)));
    }

    @Operation(summary = "Find duplicate groups among files owned by the current user")
    @PostMapping("/duplicates/groups/search")
    public DuplicateGroupSearchResponse searchDuplicateGroups(
            @RequestBody(required = false) @Valid DuplicateGroupSearchRequest request) {
        UUID actorUserId = currentUserProvider.getCurrentUserId();

        return toResponse(duplicateSearchService.searchGroups(toQuery(request), actorUserId));
    }

    private DuplicateGroupSearchQuery toQuery(DuplicateGroupSearchRequest request) {
        if (request == null) {
            return null;
        }

        return new DuplicateGroupSearchQuery(
                request.methods(),
                request.limit(),
                request.folderId(),
                request.mimeType(),
                request.minConfidence());
    }

    private DuplicateSearchResponse toResponse(DuplicateSearchResult result) {
        return new DuplicateSearchResponse(
                result.sourceFileId(),
                result.methods().stream()
                        .map(method -> DuplicateSearchResponse.DuplicateMethodResultResponse.builder()
                                .method(method.method())
                                .status(method.status())
                                .matches(method.matches().stream()
                                        .map(match -> DuplicateSearchResponse.DuplicateMatchResponse.builder()
                                                .fileId(match.fileId())
                                                .confidence(match.confidence())
                                                .score(match.score())
                                                .evidence(match.evidence().stream()
                                                        .map(evidence -> DuplicateSearchResponse.DuplicateEvidenceResponse.builder()
                                                                .type(evidence.type())
                                                                .score(evidence.score())
                                                                .details(evidence.details())
                                                                .build())
                                                        .toList())
                                                .build())
                                        .toList())
                                .pageSize(method.pageSize())
                                .hasMore(method.hasMore())
                                .nextCursor(method.nextCursor())
                                .build())
                        .toList());
    }

    private DuplicateGroupSearchResponse toResponse(DuplicateGroupSearchResult result) {
        return new DuplicateGroupSearchResponse(
                result.methods().stream()
                        .map(method -> DuplicateGroupSearchResponse.DuplicateGroupMethodResultResponse.builder()
                                .method(method.method())
                                .status(method.status())
                                .groups(method.groups().stream()
                                        .map(group -> DuplicateGroupSearchResponse.DuplicateGroupResponse.builder()
                                                .groupId(group.groupId())
                                                .confidence(group.confidence())
                                                .representativeFileId(group.representativeFileId())
                                                .files(group.files().stream()
                                                        .map(file -> DuplicateGroupSearchResponse.DuplicateGroupFileResponse.builder()
                                                                .fileId(file.fileId())
                                                                .name(file.name())
                                                                .mimeType(file.mimeType())
                                                                .size(file.size())
                                                                .build())
                                                        .toList())
                                                .evidence(group.evidence().stream()
                                                        .map(evidence -> DuplicateGroupSearchResponse.DuplicateGroupEvidenceResponse.builder()
                                                                .type(evidence.type())
                                                                .score(evidence.score())
                                                                .details(evidence.details())
                                                                .build())
                                                        .toList())
                                                .build())
                                        .toList())
                                .build())
                        .toList(),
                result.nextCursor());
    }

    private List<DuplicateSearchMethod> parseMethods(String rawMethods) {
        if (rawMethods == null || rawMethods.isBlank()) {
            return List.of();
        }

        List<DuplicateSearchMethod> methods = new ArrayList<>();
        for (String rawMethod : rawMethods.split(",")) {
            String normalized = rawMethod.trim().toUpperCase(Locale.ROOT);
            if (normalized.isBlank()) {
                continue;
            }
            try {
                methods.add(DuplicateSearchMethod.valueOf(normalized));
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("Invalid duplicate search method: " + rawMethod.trim());
            }
        }

        return methods;
    }

    private void validateCursorRequest(String cursor, List<DuplicateSearchMethod> methods) {
        if (cursor == null || cursor.isBlank()) {
            return;
        }

        long methodCount = methods.stream().filter(Objects::nonNull).distinct().count();
        if (methodCount != 1) {
            throw new IllegalArgumentException("Duplicate search cursor requests must specify exactly one method.");
        }
    }
}

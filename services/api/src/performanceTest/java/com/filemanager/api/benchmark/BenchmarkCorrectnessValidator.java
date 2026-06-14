package com.filemanager.api.benchmark;

import com.filemanager.api.duplicate.application.DuplicateSearchService;
import com.filemanager.api.duplicate.domain.DuplicateMethodStatus;
import com.filemanager.api.duplicate.domain.DuplicateSearchMethod;
import com.filemanager.api.duplicate.web.DuplicateGroupSearchRequest;
import com.filemanager.api.duplicate.web.DuplicateGroupSearchResponse;
import com.filemanager.api.duplicate.web.DuplicateSearchResponse;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class BenchmarkCorrectnessValidator {
    private final DuplicateSearchService duplicateSearchService;

    BenchmarkCorrectnessValidator(DuplicateSearchService duplicateSearchService) {
        this.duplicateSearchService = duplicateSearchService;
    }

    BenchmarkSuiteTest.CorrectnessResults validate(BenchmarkDatasetPlan dataset) {
        BenchmarkSuiteTest.CorrectnessResults results = new BenchmarkSuiteTest.CorrectnessResults();

        for (BenchmarkCase benchmarkCase : dataset.cases()) {
            DuplicateSearchResponse response = duplicateSearchService.searchDuplicatesForFile(
                    benchmarkCase.sourceFileId(),
                    List.of(benchmarkCase.method()),
                    dataset.actorUserId());

            List<UUID> actual = response.methods().getFirst().matches().stream()
                    .map(DuplicateSearchResponse.DuplicateMatchResponse::fileId)
                    .toList();
            DuplicateMethodStatus status = response.methods().getFirst().status();

            results.add(
                    benchmarkCase.name(),
                    status == DuplicateMethodStatus.COMPLETED
                            && new LinkedHashSet<>(actual).equals(new LinkedHashSet<>(benchmarkCase.expectedMatches())),
                    Map.of("status", DuplicateMethodStatus.COMPLETED.name(), "candidateIds", benchmarkCase.expectedMatches()),
                    Map.of("status", status.name(), "candidateIds", actual),
                    "Duplicate candidates must match the dataset oracle exactly.");
        }

        validateCompletedGroups(results, dataset, DuplicateSearchMethod.EXACT, "duplicate.groups.EXACT");
        validateCompletedGroups(results, dataset, DuplicateSearchMethod.AUDIO_FINGERPRINT, "duplicate.groups.AUDIO_FINGERPRINT");

        for (DuplicateSearchMethod method : List.of(
                DuplicateSearchMethod.IMAGE_PHASH,
                DuplicateSearchMethod.IMAGE_EMBEDDING,
                DuplicateSearchMethod.VIDEO_EMBEDDING)) {
            DuplicateGroupSearchResponse response = duplicateSearchService.searchGroups(
                    new DuplicateGroupSearchRequest(List.of(method), 10, null, null, null, null),
                    dataset.actorUserId());

            results.add(
                    "duplicate.groups.deferred." + method,
                    groupResult(response).status() == DuplicateMethodStatus.NOT_SUPPORTED_FOR_GROUPED_SEARCH_YET,
                    Map.of("status", DuplicateMethodStatus.NOT_SUPPORTED_FOR_GROUPED_SEARCH_YET.name()),
                    Map.of("status", groupResult(response).status().name()),
                    "Deferred grouped duplicate methods must report the intended status.");
        }

        return results;
    }

    private void validateCompletedGroups(
            BenchmarkSuiteTest.CorrectnessResults results,
            BenchmarkDatasetPlan dataset,
            DuplicateSearchMethod method,
            String caseName) {
        DuplicateGroupSearchResponse response = duplicateSearchService.searchGroups(
                new DuplicateGroupSearchRequest(List.of(method), 50, null, null, null, null),
                dataset.actorUserId());

        results.add(
                caseName,
                groupResult(response).status() == DuplicateMethodStatus.COMPLETED
                        && groupResult(response).groups().stream().allMatch(group -> group.files().size() >= 2),
                Map.of("status", DuplicateMethodStatus.COMPLETED.name(), "minimumGroupSize", 2),
                Map.of(
                        "status", groupResult(response).status().name(),
                        "groupSizes", groupResult(response).groups().stream()
                                .map(group -> group.files().size())
                                .toList()),
                "Grouped duplicate results must complete and contain only eligible duplicate groups.");
    }

    private DuplicateGroupSearchResponse.DuplicateGroupMethodResultResponse groupResult(
            DuplicateGroupSearchResponse response) {
        return response.methods().getFirst();
    }
}

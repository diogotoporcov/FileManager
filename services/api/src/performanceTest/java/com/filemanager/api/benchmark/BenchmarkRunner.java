package com.filemanager.api.benchmark;

import com.filemanager.api.duplicate.application.DuplicateSearchService;
import com.filemanager.api.duplicate.domain.DuplicateSearchMethod;
import com.filemanager.api.duplicate.web.DuplicateGroupSearchRequest;
import com.filemanager.api.duplicate.web.DuplicateGroupSearchResponse;
import com.filemanager.api.duplicate.web.DuplicateSearchResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class BenchmarkRunner {
    private final DuplicateSearchService duplicateSearchService;
    private final Runnable afterWarmup;

    BenchmarkRunner(
            DuplicateSearchService duplicateSearchService,
            Runnable afterWarmup) {
        this.duplicateSearchService = duplicateSearchService;
        this.afterWarmup = afterWarmup;
    }

    List<BenchmarkSuiteTest.BenchmarkMeasurement> run(BenchmarkOptions options, BenchmarkDatasetPlan dataset) {
        List<BenchmarkOperation> operations = benchmarkOperations(dataset);

        for (BenchmarkOperation operation : operations) {
            for (int i = 0; i < options.warmupIterations(); i++) {
                operation.run(i);
            }
        }

        afterWarmup.run();

        List<BenchmarkSuiteTest.BenchmarkMeasurement> measurements = new ArrayList<>();

        for (BenchmarkOperation operation : operations) {
            measurements.add(measure(operation.name(), options, operation.sampleSize(), operation));
        }

        return measurements;
    }

    private List<BenchmarkOperation> benchmarkOperations(BenchmarkDatasetPlan dataset) {
        List<BenchmarkOperation> operations = new ArrayList<>();

        for (BenchmarkRegistryOperation registryOperation : dataset.registry().operations().values()) {
            if (registryOperation.method() == null) {
                operations.add(new BenchmarkOperation(
                        registryOperation.operation(),
                        registryOperation.sampleSize(),
                        iteration -> countGroups(dataset.actorUserId())));
            } else {
                operations.add(new BenchmarkOperation(
                        registryOperation.operation(),
                        registryOperation.sampleSize(),
                        iteration -> countMatches(dataset.actorUserId(), registryOperation, iteration)));
            }
        }

        return operations;
    }

    private int countMatches(UUID actorUserId, BenchmarkRegistryOperation operation, int iteration) {
        UUID sourceFileId = operation.sourceFileIds().get(iteration % operation.sourceFileIds().size());
        DuplicateSearchResponse response = duplicateSearchService.searchDuplicatesForFile(
                sourceFileId,
                List.of(operation.method()),
                actorUserId);

        return response.methods().getFirst().matches().size();
    }

    private int countGroups(UUID actorUserId) {
        DuplicateGroupSearchResponse response = duplicateSearchService.searchGroups(
                new DuplicateGroupSearchRequest(List.of(DuplicateSearchMethod.EXACT), 50, null, null, null, null),
                actorUserId);

        return response.methods().getFirst().groups().size();
    }

    private BenchmarkSuiteTest.BenchmarkMeasurement measure(
            String operation,
            BenchmarkOptions options,
            int sourceSampleCount,
            BenchmarkOperation benchmarkOperation) {
        List<BenchmarkSample> samples = new ArrayList<>(options.measuredIterations());
        RuntimeException failure = null;

        for (int i = 0; i < options.measuredIterations(); i++) {
            long started = System.nanoTime();

            try {
                int resultCount = benchmarkOperation.run(i);
                samples.add(new BenchmarkSample(
                        true,
                        BenchmarkSupport.nanosToMillis(System.nanoTime() - started),
                        resultCount));
            } catch (RuntimeException ex) {
                samples.add(new BenchmarkSample(false, BenchmarkSupport.nanosToMillis(System.nanoTime() - started)));
                failure = ex;
                break;
            }
        }

        if (failure != null) {
            throw failure;
        }

        return BenchmarkSuiteTest.BenchmarkMeasurement.from(operation, options, sourceSampleCount, samples);
    }

    private record BenchmarkOperation(String name, int sampleSize, BenchmarkAction action) {
        int run(int iteration) {
            return action.run(iteration);
        }
    }

    private interface BenchmarkAction {
        int run(int iteration);
    }
}

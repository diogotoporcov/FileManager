package com.filemanager.api.benchmark;

import com.filemanager.api.duplicate.application.DuplicateSearchService;
import com.filemanager.api.duplicate.domain.DuplicateSearchMethod;
import com.filemanager.api.duplicate.web.DuplicateGroupSearchRequest;
import com.filemanager.api.file.application.FileService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;

final class BenchmarkRunner {
    private final DuplicateSearchService duplicateSearchService;
    private final FileService fileService;
    private final JdbcTemplate jdbcTemplate;
    private final Runnable afterWarmup;

    BenchmarkRunner(
            DuplicateSearchService duplicateSearchService,
            FileService fileService,
            JdbcTemplate jdbcTemplate,
            Runnable afterWarmup) {
        this.duplicateSearchService = duplicateSearchService;
        this.fileService = fileService;
        this.jdbcTemplate = jdbcTemplate;
        this.afterWarmup = afterWarmup;
    }

    List<BenchmarkSuiteTest.BenchmarkMeasurement> run(BenchmarkOptions options, BenchmarkDatasetPlan dataset) {
        List<BenchmarkOperation> operations = benchmarkOperations(dataset);

        for (BenchmarkOperation operation : operations) {
            for (int i = 0; i < options.warmupIterations(); i++) {
                operation.action().get();
            }
        }

        afterWarmup.run();

        List<BenchmarkSuiteTest.BenchmarkMeasurement> measurements = new ArrayList<>();

        for (BenchmarkOperation operation : operations) {
            measurements.add(measure(operation.name(), options, operation.action()));
        }

        return measurements;
    }

    private List<BenchmarkOperation> benchmarkOperations(BenchmarkDatasetPlan dataset) {
        List<BenchmarkOperation> operations = new ArrayList<>();

        for (BenchmarkCase benchmarkCase : dataset.cases()) {
            operations.add(new BenchmarkOperation(
                    benchmarkCase.name(),
                    () -> duplicateSearchService.searchDuplicatesForFile(
                            benchmarkCase.sourceFileId(),
                            List.of(benchmarkCase.method()),
                            dataset.actorUserId())));
        }

        UUID actorUserId = dataset.actorUserId();

        operations.add(new BenchmarkOperation("duplicate.groups.EXACT.first-50", () -> duplicateSearchService.searchGroups(
                new DuplicateGroupSearchRequest(List.of(DuplicateSearchMethod.EXACT), 50, null, null, null, null),
                actorUserId)));
        operations.add(new BenchmarkOperation(
                "duplicate.groups.AUDIO_FINGERPRINT.first-50",
                () -> duplicateSearchService.searchGroups(
                        new DuplicateGroupSearchRequest(
                                List.of(DuplicateSearchMethod.AUDIO_FINGERPRINT),
                                50,
                                null,
                                null,
                                null,
                                null),
                        actorUserId)));
        operations.add(new BenchmarkOperation("file.search.owner-first-page", () -> jdbcTemplate.queryForList(
                """
                SELECT id
                FROM files
                WHERE owner_user_id = ? AND deleted_at IS NULL
                ORDER BY created_at DESC, id DESC
                LIMIT 50
                """,
                actorUserId)));
        operations.add(new BenchmarkOperation("folder.list.root", () -> jdbcTemplate.queryForList(
                """
                SELECT id
                FROM folders
                WHERE owner_user_id = ? AND parent_folder_id IS NULL AND deleted_at IS NULL
                ORDER BY name, id
                """,
                actorUserId)));
        operations.add(new BenchmarkOperation("permission.evaluate.file-owner", () -> fileService.getFileMetadata(
                dataset.source("exact-one-match"),
                actorUserId)));
        operations.add(new BenchmarkOperation("sharing.list-file-grants", () -> jdbcTemplate.queryForList(
                """
                SELECT id
                FROM file_grants
                WHERE file_id = ? AND revoked_at IS NULL
                ORDER BY created_at, id
                """,
                dataset.source("foreign-shared-exact"))));
        operations.add(new BenchmarkOperation("processing.status.jobs", () -> jdbcTemplate.queryForList(
                """
                SELECT id, job_type, status
                FROM processing_jobs
                WHERE file_id = ?
                ORDER BY created_at, id
                """,
                dataset.source("processing-status"))));

        return operations;
    }

    private BenchmarkSuiteTest.BenchmarkMeasurement measure(
            String operation,
            BenchmarkOptions options,
            Supplier<Object> action) {
        List<BenchmarkSample> samples = new ArrayList<>(options.measuredIterations());
        RuntimeException failure = null;

        for (int i = 0; i < options.measuredIterations(); i++) {
            long started = System.nanoTime();

            try {
                action.get();
                samples.add(new BenchmarkSample(true, BenchmarkSupport.nanosToMillis(System.nanoTime() - started)));
            } catch (RuntimeException ex) {
                samples.add(new BenchmarkSample(false, BenchmarkSupport.nanosToMillis(System.nanoTime() - started)));
                failure = ex;
                break;
            }
        }

        if (failure != null) {
            throw failure;
        }

        return BenchmarkSuiteTest.BenchmarkMeasurement.from(operation, options, samples);
    }

    private record BenchmarkOperation(String name, Supplier<Object> action) {
    }
}

package com.filemanager.api.benchmark;

import com.filemanager.api.config.EmbeddingDimensions;
import com.filemanager.api.duplicate.application.DuplicateCandidateMaintenanceService;
import com.filemanager.api.duplicate.application.DuplicateSearchService;
import com.filemanager.api.duplicate.domain.DuplicateSearchMethod;
import com.filemanager.api.duplicate.web.DuplicateGroupSearchRequest;
import com.filemanager.api.file.application.FileService;
import com.filemanager.api.file.domain.FileEntity;
import com.filemanager.api.identity.domain.User;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;

final class BenchmarkRunner {
    private final DuplicateSearchService duplicateSearchService;
    private final DuplicateCandidateMaintenanceService duplicateCandidateMaintenanceService;
    private final FileService fileService;
    private final JdbcTemplate jdbcTemplate;
    private final Runnable afterWarmup;

    BenchmarkRunner(
            DuplicateSearchService duplicateSearchService,
            DuplicateCandidateMaintenanceService duplicateCandidateMaintenanceService,
            FileService fileService,
            JdbcTemplate jdbcTemplate,
            Runnable afterWarmup) {
        this.duplicateSearchService = duplicateSearchService;
        this.duplicateCandidateMaintenanceService = duplicateCandidateMaintenanceService;
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
        operations.add(new BenchmarkOperation("duplicate.groups.EXACT.summary.first-50", () -> jdbcTemplate.queryForList(
                """
                SELECT algorithm, hash_value, active_file_count
                FROM exact_duplicate_groups
                WHERE owner_user_id = ? AND active_file_count > 1
                ORDER BY active_file_count DESC, algorithm, hash_value
                LIMIT 50
                """,
                actorUserId)));
        operations.add(new BenchmarkOperation("duplicate.groups.EXACT.files.first-50", () -> jdbcTemplate.queryForList(
                """
                SELECT fp.algorithm, fp.hash_value, f.id, f.name, f.mime_type, f.size
                FROM file_fingerprints fp
                JOIN files f ON f.id = fp.file_id
                LEFT JOIN folders folder ON folder.id = f.folder_id
                WHERE f.owner_user_id = ?
                  AND f.deleted_at IS NULL
                  AND (f.folder_id IS NULL OR folder.deleted_at IS NULL)
                  AND fp.hash_value IN (
                      SELECT hash_value
                      FROM exact_duplicate_groups
                      WHERE owner_user_id = ? AND active_file_count > 1
                      ORDER BY active_file_count DESC, algorithm, hash_value
                      LIMIT 50
                  )
                ORDER BY fp.algorithm, fp.hash_value, f.created_at, f.id
                """,
                actorUserId,
                actorUserId)));
        operations.add(new BenchmarkOperation("duplicate.summary.EXACT.insert-new-hash", () -> {
            String hash = BenchmarkSupport.sha256("summary-insert-new-" + UUID.randomUUID());
            return jdbcTemplate.update(
                    """
                    INSERT INTO exact_duplicate_groups (
                        id, owner_user_id, algorithm, hash_value, active_file_count, representative_file_id
                    )
                    VALUES (uuidv7(), ?, 'SHA256', ?, 1, ?)
                    """,
                    actorUserId,
                    hash,
                    dataset.source("exact-no-match"));
        }));
        operations.add(new BenchmarkOperation("duplicate.summary.EXACT.insert-existing-hash", () -> {
            int updated = jdbcTemplate.update(
                    """
                    UPDATE exact_duplicate_groups
                    SET active_file_count = active_file_count + 1, updated_at = CURRENT_TIMESTAMP
                    WHERE owner_user_id = ? AND algorithm = 'SHA256' AND hash_value = ?
                    """,
                    actorUserId,
                    dataset.hash("exact-one-match"));
            jdbcTemplate.update(
                    """
                    UPDATE exact_duplicate_groups
                    SET active_file_count = GREATEST(active_file_count - 1, 0), updated_at = CURRENT_TIMESTAMP
                    WHERE owner_user_id = ? AND algorithm = 'SHA256' AND hash_value = ?
                    """,
                    actorUserId,
                    dataset.hash("exact-one-match"));

            return updated;
        }));
        operations.add(new BenchmarkOperation("duplicate.summary.EXACT.replace-fingerprint", () -> {
            String oldHash = dataset.hash("exact-no-match");
            String newHash = BenchmarkSupport.sha256("summary-replace-" + UUID.randomUUID());
            jdbcTemplate.update(
                    """
                    UPDATE exact_duplicate_groups
                    SET active_file_count = active_file_count - 1, updated_at = CURRENT_TIMESTAMP
                    WHERE owner_user_id = ? AND algorithm = 'SHA256' AND hash_value = ?
                    """,
                    actorUserId,
                    oldHash);
            return jdbcTemplate.update(
                    """
                    INSERT INTO exact_duplicate_groups (
                        id, owner_user_id, algorithm, hash_value, active_file_count, representative_file_id
                    )
                    VALUES (uuidv7(), ?, 'SHA256', ?, 1, ?)
                    """,
                    actorUserId,
                    newHash,
                    dataset.source("exact-no-match"));
        }));
        operations.add(new BenchmarkOperation("duplicate.summary.EXACT.delete-file", () -> {
            int updated = jdbcTemplate.update(
                    """
                    UPDATE exact_duplicate_groups
                    SET active_file_count = active_file_count - 1, updated_at = CURRENT_TIMESTAMP
                    WHERE owner_user_id = ? AND algorithm = 'SHA256' AND hash_value = ?
                    """,
                    actorUserId,
                    dataset.hash("exact-one-match"));
            jdbcTemplate.update(
                    """
                    UPDATE exact_duplicate_groups
                    SET active_file_count = active_file_count + 1, updated_at = CURRENT_TIMESTAMP
                    WHERE owner_user_id = ? AND algorithm = 'SHA256' AND hash_value = ?
                    """,
                    actorUserId,
                    dataset.hash("exact-one-match"));

            return updated;
        }));
        operations.add(new BenchmarkOperation(
                "duplicate.candidates.IMAGE_PHASH.incremental-new-file",
                () -> {
                    duplicateCandidateMaintenanceService.refreshImagePhashCandidates(
                            sourceFile(dataset.source("image-phash-threshold"), actorUserId));
                    return null;
                }));
        operations.add(new BenchmarkOperation(
                "duplicate.candidates.IMAGE_EMBEDDING.incremental-new-file",
                () -> {
                    duplicateCandidateMaintenanceService.refreshImageEmbeddingCandidates(
                            sourceFile(dataset.source("image-embedding-threshold"), actorUserId),
                            BenchmarkDatasetGenerator.MODEL_NAME,
                            BenchmarkDatasetGenerator.MODEL_VERSION,
                            EmbeddingDimensions.IMAGE_EMBEDDING_DIMENSION);
                    return null;
                }));
        operations.add(new BenchmarkOperation(
                "duplicate.candidates.AUDIO_FINGERPRINT.incremental-new-file",
                () -> {
                    duplicateCandidateMaintenanceService.refreshAudioFingerprintCandidates(
                            sourceFile(dataset.source("audio-one-match"), actorUserId));
                    return null;
                }));
        operations.add(new BenchmarkOperation(
                "duplicate.candidates.VIDEO_EMBEDDING.incremental-new-file",
                () -> {
                    duplicateCandidateMaintenanceService.refreshVideoEmbeddingCandidates(
                            sourceFile(dataset.source("video-embedding-one-match"), actorUserId),
                            BenchmarkDatasetGenerator.MODEL_NAME,
                            BenchmarkDatasetGenerator.MODEL_VERSION,
                            EmbeddingDimensions.IMAGE_EMBEDDING_DIMENSION);
                    return null;
                }));
        operations.add(new BenchmarkOperation(
                "duplicate.candidates.read.per-file",
                () -> duplicateSearchService.searchDuplicatesForFile(
                        dataset.source("image-phash-threshold"),
                        List.of(DuplicateSearchMethod.IMAGE_PHASH),
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

    private FileEntity sourceFile(UUID fileId, UUID ownerUserId) {
        return FileEntity.builder()
                .id(fileId)
                .ownerUser(User.builder().id(ownerUserId).build())
                .build();
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

package com.filemanager.api.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.filemanager.api.duplicate.application.DuplicateCandidateMaintenanceService;
import com.filemanager.api.duplicate.application.DuplicateSearchService;
import com.filemanager.api.file.application.FileService;
import com.filemanager.api.processing.messaging.FileProcessingRequestedEvent;
import io.minio.MinioClient;
import jakarta.persistence.EntityManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@ActiveProfiles("benchmark")
@Testcontainers
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class BenchmarkSuiteTest {
    private static final int SCHEMA_VERSION = 3;
    private static final DockerImageName POSTGRES_IMAGE = DockerImageName
            .parse(System.getProperty("benchmark.postgres-image", "pgvector/pgvector:pg18"))
            .asCompatibleSubstituteFor("postgres");

    @SuppressWarnings("resource")
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(POSTGRES_IMAGE)
            .withDatabaseName("filemanager_benchmark")
            .withUsername("filemanager")
            .withPassword("filemanager")
            .withCommand(
                    "postgres",
                    "-c",
                    "shared_preload_libraries=pg_stat_statements",
                    "-c",
                    "pg_stat_statements.track=all");

    private final DuplicateSearchService duplicateSearchService;
    private final DuplicateCandidateMaintenanceService duplicateCandidateMaintenanceService;
    private final FileService fileService;
    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final BenchmarkArtifactWriter artifactWriter = new BenchmarkArtifactWriter(objectMapper);

    @MockitoBean
    private MinioClient minioClient;
    @MockitoBean
    private KafkaTemplate<String, FileProcessingRequestedEvent> kafkaTemplate;
    @MockitoBean
    private JwtDecoder jwtDecoder;

    BenchmarkSuiteTest(
            DuplicateSearchService duplicateSearchService,
            DuplicateCandidateMaintenanceService duplicateCandidateMaintenanceService,
            FileService fileService,
            JdbcTemplate jdbcTemplate,
            DataSource dataSource,
            EntityManager entityManager) {
        this.duplicateSearchService = duplicateSearchService;
        this.duplicateCandidateMaintenanceService = duplicateCandidateMaintenanceService;
        this.fileService = fileService;
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
        this.entityManager = entityManager;
    }

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void runBenchmarkSuite() throws Exception {
        BenchmarkOptions options = BenchmarkOptions.fromSystemProperties();
        BenchmarkRunContext context = BenchmarkRunContext.create(options);
        BenchmarkDatabaseCollector databaseCollector = new BenchmarkDatabaseCollector(jdbcTemplate, artifactWriter);
        SetupTimings setupTimings = new SetupTimings();

        long extensionStart = System.nanoTime();
        databaseCollector.initializePgStatStatements();
        setupTimings.record("pgStatStatementsExtensionMs", extensionStart);

        BenchmarkDatasetPlan dataset = new BenchmarkDatasetGenerator(options).generate();
        long copyStart = System.nanoTime();
        new BenchmarkDataLoader(dataSource).load(dataset);
        setupTimings.record("copyTotalMs", copyStart);

        long analyzeStart = System.nanoTime();
        BenchmarkDatabaseVerifier databaseVerifier = new BenchmarkDatabaseVerifier(jdbcTemplate);
        databaseVerifier.analyzeTables();
        setupTimings.record("analyzeMs", analyzeStart);

        databaseVerifier.verifyLoadedCounts(dataset);
        databaseVerifier.enrichDatasetSizes(dataset);
        entityManager.clear();

        CorrectnessResults correctness = new BenchmarkCorrectnessValidator(duplicateSearchService).validate(dataset);
        artifactWriter.writeJson(context.scaleDir().resolve("correctness-results.json"), correctness.toManifest());

        if (!correctness.passed()) {
            throw new AssertionError("Benchmark correctness failed: " + correctness.failedCaseNames());
        }

        List<BenchmarkMeasurement> measurements = new BenchmarkRunner(
                duplicateSearchService,
                duplicateCandidateMaintenanceService,
                fileService,
                jdbcTemplate,
                databaseCollector::resetStatementStats)
                .run(options, dataset);

        writeArtifacts(context, dataset, setupTimings, measurements, databaseCollector);

        new BenchmarkRawArtifactValidator(context.scaleDir()).validate();
    }

    private void writeArtifacts(
            BenchmarkRunContext context,
            BenchmarkDatasetPlan dataset,
            SetupTimings setupTimings,
            List<BenchmarkMeasurement> measurements,
            BenchmarkDatabaseCollector databaseCollector) throws Exception {
        artifactWriter.writeJson(
                context.scaleDir().resolve("environment.json"),
                new BenchmarkEnvironmentCollector(jdbcTemplate).collect(context));
        artifactWriter.writeJson(context.scaleDir().resolve("dataset-manifest.json"), dataset.toManifest());

        artifactWriter.writeJson(context.scaleDir().resolve("setup-timings.json"), Map.of(
                "schemaVersion", SCHEMA_VERSION,
                "timingsMs", setupTimings.values()));
        artifactWriter.writeJson(context.scaleDir().resolve("repository-latency.json"), Map.of(
                "schemaVersion", SCHEMA_VERSION,
                "measurements", measurements));

        artifactWriter.writeJson(context.scaleDir().resolve("jvm-resource-usage.json"), databaseCollector.resourceUsage());
        databaseCollector.writeDatabaseStats(context.scaleDir().resolve("pg-stat-statements.csv"));
        new BenchmarkQueryPlanCollector(jdbcTemplate, objectMapper).collect(context.scaleDir(), dataset);

        artifactWriter.writeJson(context.scaleDir().resolve("component-status.json"), databaseCollector.componentStatus());
    }

    static final class CorrectnessResults {
        private final List<BenchmarkCaseResult> cases = new ArrayList<>();

        void add(String name, boolean passed, Object expected, Object actual, String message) {
            cases.add(new BenchmarkCaseResult(name, passed, expected, actual, message));
        }

        boolean passed() {
            return cases.stream().allMatch(BenchmarkCaseResult::passed);
        }

        int caseCount() {
            return cases.size();
        }

        long passedCount() {
            return cases.stream().filter(BenchmarkCaseResult::passed).count();
        }

        long failedCount() {
            return cases.stream().filter(result -> !result.passed()).count();
        }

        List<String> failedCaseNames() {
            return cases.stream()
                    .filter(result -> !result.passed())
                    .map(BenchmarkCaseResult::name)
                    .toList();
        }

        Map<String, Object> toManifest() {
            Map<String, Object> manifest = new LinkedHashMap<>();
            manifest.put("schemaVersion", SCHEMA_VERSION);
            manifest.put("passed", passed());
            manifest.put("caseCount", caseCount());
            manifest.put("passedCount", passedCount());
            manifest.put("failedCount", failedCount());
            manifest.put("cases", cases);

            return manifest;
        }
    }

    private record BenchmarkCaseResult(String name, boolean passed, Object expected, Object actual, String message) {
    }

    private static final class SetupTimings {
        private final Map<String, Double> values = new LinkedHashMap<>();

        void record(String name, long startedNanos) {
            values.put(name, BenchmarkSupport.nanosToMillis(System.nanoTime() - startedNanos));
        }

        Map<String, Double> values() {
            return values;
        }
    }

    record BenchmarkMeasurement(
            int schemaVersion,
            String operation,
            BenchmarkScope scope,
            int sampleCount,
            int warmupCount,
            int successCount,
            int failureCount,
            String coldOrWarm,
            String percentileMethod,
            String standardDeviationMethod,
            double minMs,
            double maxMs,
            double meanMs,
            double p50Ms,
            double p90Ms,
            double p95Ms,
            Double p99Ms,
            String p99Status,
            double stddevMs) {

        static BenchmarkMeasurement from(String operation, BenchmarkOptions options, List<BenchmarkSample> samples) {
            return from(operation, options, BenchmarkStatistics.fromSamples(samples));
        }

        private static BenchmarkMeasurement from(
                String operation,
                BenchmarkOptions options,
                BenchmarkStatistics statistics) {
            return new BenchmarkMeasurement(
                    SCHEMA_VERSION,
                    operation,
                    BenchmarkScope.SPRING_SERVICE_REPOSITORY,
                    statistics.sampleCount(),
                    options.warmupIterations(),
                    statistics.successCount(),
                    statistics.failureCount(),
                    "WARM",
                    BenchmarkStatistics.PERCENTILE_METHOD,
                    BenchmarkStatistics.STANDARD_DEVIATION_METHOD,
                    statistics.minMs(),
                    statistics.maxMs(),
                    statistics.meanMs(),
                    statistics.p50Ms(),
                    statistics.p90Ms(),
                    statistics.p95Ms(),
                    statistics.p99Ms(),
                    statistics.p99Status(),
                    statistics.standardDeviationMs());
        }
    }

    private enum BenchmarkScope {
        SPRING_SERVICE_REPOSITORY,
        DATABASE_DIRECT
    }
}

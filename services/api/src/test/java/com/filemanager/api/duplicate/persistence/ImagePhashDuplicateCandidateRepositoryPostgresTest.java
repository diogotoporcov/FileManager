package com.filemanager.api.duplicate.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.filemanager.api.duplicate.phash.PhashMih;
import com.filemanager.api.processing.application.ProcessingJobService;
import com.filemanager.api.processing.domain.ProcessingJob;
import com.filemanager.api.processing.messaging.FileProcessingRequestedEvent;
import io.minio.MinioClient;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(properties = {
    "spring.flyway.enabled=true",
    "spring.flyway.baseline-on-migrate=false",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
    "filemanager.internal.api-token=test-internal-token-123456789012",
    "minio.endpoint=http://localhost:9000",
    "minio.access-key=dummy",
    "minio.secret-key=dummy",
    "minio.bucket-name=test-bucket"
})
@Testcontainers
class ImagePhashDuplicateCandidateRepositoryPostgresTest {
    private static final DockerImageName PGVECTOR_IMAGE = DockerImageName
            .parse("pgvector/pgvector:pg18")
            .asCompatibleSubstituteFor("postgres");
    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID ACTIVE_FOLDER_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID DELETED_FOLDER_ID = UUID.fromString("00000000-0000-0000-0000-000000000102");
    private static final String SOURCE_PHASH = "0123456789abcdef";
    private static final OffsetDateTime BASE_TIME = OffsetDateTime.parse("2026-01-01T12:00:00Z");

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(PGVECTOR_IMAGE)
            .withDatabaseName("filemanager_test")
            .withUsername("test")
            .withPassword("test");

    @Autowired
    private ImagePhashDuplicateCandidateRepository repository;
    @Autowired
    private ProcessingJobService processingJobService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private MinioClient minioClient;
    @MockitoBean
    private KafkaTemplate<String, FileProcessingRequestedEvent> kafkaTemplate;
    @MockitoBean
    private JwtDecoder jwtDecoder;

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @AfterEach
    void cleanDatabase() {
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS filemanager_test_fail_mih_chunk_insert ON image_phash_mih_chunks");
        jdbcTemplate.execute("DROP FUNCTION IF EXISTS filemanager_test_fail_mih_chunk_insert()");
        jdbcTemplate.update("DELETE FROM image_phash_mih_chunks");
        jdbcTemplate.update("DELETE FROM image_fingerprints");
        jdbcTemplate.update("DELETE FROM processing_jobs");
        jdbcTemplate.update("DELETE FROM files");
        jdbcTemplate.update("DELETE FROM folders");
        jdbcTemplate.update("DELETE FROM users");
    }

    @Test
    void findCandidates_UsesRealArrayBackedQueryAndStableCursorPagination() {
        seedUsersAndFolders();
        UUID sourceId = file("00000000-0000-0000-0000-000000001000", "source.jpg", OWNER_ID, ACTIVE_FOLDER_ID,
                "image/jpeg", null, BASE_TIME.plusHours(1));
        fingerprint(sourceId, SOURCE_PHASH);

        UUID distanceZero = candidate("00000000-0000-0000-0000-000000001001", "d0.jpg", SOURCE_PHASH, 0);
        UUID distanceOne = candidate("00000000-0000-0000-0000-000000001002", "d1.jpg", flip(SOURCE_PHASH, 0), 1);
        UUID distanceThreeA = candidate(
                "00000000-0000-0000-0000-000000001003",
                "d3a.jpg",
                flip(SOURCE_PHASH, 0, 1, 2),
                2);
        UUID distanceThreeB = candidate(
                "00000000-0000-0000-0000-000000001004",
                "d3b.jpg",
                flip(SOURCE_PHASH, 3, 4, 5),
                2);
        UUID distanceNine = candidate(
                "00000000-0000-0000-0000-000000001005",
                "d9.jpg",
                flip(SOURCE_PHASH, 0, 1, 2, 3, 4, 5, 6, 7, 8),
                3);
        UUID distanceTenNoIdenticalChunk = candidate(
                "00000000-0000-0000-0000-000000001006",
                "d10.jpg",
                flip(SOURCE_PHASH, 0, 1, 2, 3, 21, 22, 23, 42, 43, 44),
                4);

        candidate("00000000-0000-0000-0000-000000001007", "d11.jpg",
                flip(SOURCE_PHASH, 0, 1, 2, 3, 4, 21, 22, 23, 42, 43, 44), 5);
        excludedCandidate("00000000-0000-0000-0000-000000001008", "other-owner.jpg", OTHER_OWNER_ID,
                ACTIVE_FOLDER_ID, "image/jpeg", null, SOURCE_PHASH);
        excludedCandidate("00000000-0000-0000-0000-000000001009", "deleted-file.jpg", OWNER_ID,
                ACTIVE_FOLDER_ID, "image/jpeg", BASE_TIME.plusDays(1), SOURCE_PHASH);
        excludedCandidate("00000000-0000-0000-0000-000000001010", "deleted-folder.jpg", OWNER_ID,
                DELETED_FOLDER_ID, "image/jpeg", null, SOURCE_PHASH);
        excludedCandidate("00000000-0000-0000-0000-000000001011", "not-image.pdf", OWNER_ID,
                ACTIVE_FOLDER_ID, "application/pdf", null, SOURCE_PHASH);

        List<PhashDuplicateCandidateProjection> firstPage = repository.findCandidates(
                OWNER_ID, sourceId, SOURCE_PHASH, 10, null, null, null, 4);
        assertThat(firstPage)
                .extracting(PhashDuplicateCandidateProjection::getFileId)
                .containsExactly(distanceZero, distanceOne, distanceThreeA, distanceThreeB);

        PhashDuplicateCandidateProjection cursor = firstPage.get(2);
        List<PhashDuplicateCandidateProjection> secondPage = repository.findCandidates(
                OWNER_ID,
                sourceId,
                SOURCE_PHASH,
                10,
                cursor.getDistance(),
                cursor.getCreatedAt().toString(),
                cursor.getFileId().toString(),
                4);

        assertThat(secondPage)
                .extracting(PhashDuplicateCandidateProjection::getFileId)
                .containsExactly(distanceThreeB, distanceNine, distanceTenNoIdenticalChunk);

        List<UUID> visibleIds = new ArrayList<>();
        firstPage.stream().limit(3).map(PhashDuplicateCandidateProjection::getFileId).forEach(visibleIds::add);
        secondPage.stream().map(PhashDuplicateCandidateProjection::getFileId).forEach(visibleIds::add);
        assertThat(visibleIds).containsExactly(
                distanceZero,
                distanceOne,
                distanceThreeA,
                distanceThreeB,
                distanceNine,
                distanceTenNoIdenticalChunk);
        assertThat(new LinkedHashSet<>(visibleIds)).hasSameSizeAs(visibleIds);
        assertThat(visibleIds).doesNotContain(sourceId);
        assertThat(firstPage)
                .extracting(PhashDuplicateCandidateProjection::getDistance)
                .containsExactly(0, 1, 3, 3);
        assertThat(secondPage)
                .extracting(PhashDuplicateCandidateProjection::getDistance)
                .containsExactly(3, 9, 10);
    }

    @Test
    void handlePhashResult_RollsBackFingerprintAndJobCompletionWhenChunkPersistenceFails() {
        seedUsersAndFolders();
        UUID fileId = file("00000000-0000-0000-0000-000000002000", "rollback.jpg", OWNER_ID, ACTIVE_FOLDER_ID,
                "image/jpeg", null, BASE_TIME);
        UUID jobId = UUID.fromString("00000000-0000-0000-0000-000000002100");
        jdbcTemplate.update("""
                INSERT INTO processing_jobs(id, file_id, job_type, status, created_at, updated_at)
                VALUES (?, ?, 'PHASH', 'PENDING', ?, ?)
                """, jobId, fileId, BASE_TIME, BASE_TIME);
        jdbcTemplate.execute("""
                CREATE OR REPLACE FUNCTION filemanager_test_fail_mih_chunk_insert()
                RETURNS trigger
                LANGUAGE plpgsql
                AS $$
                BEGIN
                    RAISE EXCEPTION 'forced mih chunk failure';
                END;
                $$;
                """);
        jdbcTemplate.execute("""
                CREATE TRIGGER filemanager_test_fail_mih_chunk_insert
                BEFORE INSERT ON image_phash_mih_chunks
                FOR EACH ROW EXECUTE FUNCTION filemanager_test_fail_mih_chunk_insert();
                """);

        assertThatThrownBy(() -> processingJobService.handlePhashResult(jobId, fileId, SOURCE_PHASH))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("forced mih chunk failure");

        assertThat(count("image_fingerprints", fileId)).isZero();
        assertThat(count("image_phash_mih_chunks", fileId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM processing_jobs WHERE id = ?",
                String.class,
                jobId))
                .isEqualTo(ProcessingJob.JobStatus.PENDING.name());
    }

    private void seedUsersAndFolders() {
        insertUser(OWNER_ID, "owner@example.test");
        insertUser(OTHER_OWNER_ID, "other@example.test");
        insertFolder(ACTIVE_FOLDER_ID, "active", OWNER_ID, null);
        insertFolder(DELETED_FOLDER_ID, "deleted", OWNER_ID, BASE_TIME.plusDays(1));
    }

    private UUID candidate(String id, String name, String phash, int createdOffset) {
        UUID fileId = file(id, name, OWNER_ID, ACTIVE_FOLDER_ID, "image/jpeg", null,
                BASE_TIME.minusMinutes(createdOffset));
        fingerprint(fileId, phash);
        return fileId;
    }

    private void excludedCandidate(
            String id,
            String name,
            UUID ownerId,
            UUID folderId,
            String mimeType,
            OffsetDateTime deletedAt,
            String phash) {
        UUID fileId = file(id, name, ownerId, folderId, mimeType, deletedAt, BASE_TIME);
        fingerprint(fileId, phash);
    }

    private void insertUser(UUID userId, String email) {
        jdbcTemplate.update("""
                INSERT INTO users(id, email, first_name, last_name, created_at, updated_at)
                VALUES (?, ?, 'Test', 'User', ?, ?)
                """, userId, email, BASE_TIME, BASE_TIME);
    }

    private void insertFolder(UUID folderId, String name, UUID ownerId, OffsetDateTime deletedAt) {
        jdbcTemplate.update("""
                INSERT INTO folders(
                    id, name, owner_user_id, created_by_user_id, created_at, updated_at, deleted_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, folderId, name, ownerId, ownerId, BASE_TIME, BASE_TIME, deletedAt);
    }

    private UUID file(
            String id,
            String name,
            UUID ownerId,
            UUID folderId,
            String mimeType,
            OffsetDateTime deletedAt,
            OffsetDateTime createdAt) {
        UUID fileId = UUID.fromString(id);
        jdbcTemplate.update("""
                INSERT INTO files(
                    id, name, storage_path, mime_type, size, owner_user_id, folder_id,
                    created_by_user_id, created_at, updated_at, deleted_at
                )
                VALUES (?, ?, ?, ?, 1, ?, ?, ?, ?, ?, ?)
                """, fileId, name, "test/" + fileId, mimeType, ownerId, folderId, ownerId,
                createdAt, createdAt, deletedAt);
        return fileId;
    }

    private void fingerprint(UUID fileId, String phash) {
        jdbcTemplate.update("""
                INSERT INTO image_fingerprints(id, file_id, phash, created_at)
                VALUES (?, ?, ?, ?)
                """, UUID.randomUUID(), fileId, PhashMih.normalize(phash), BASE_TIME);
        for (PhashMih.Chunk chunk : PhashMih.chunks(phash)) {
            jdbcTemplate.update("""
                    INSERT INTO image_phash_mih_chunks(file_id, chunk_index, chunk_value)
                    VALUES (?, ?, ?)
                    """, fileId, (short) chunk.index(), chunk.value());
        }
    }

    private long count(String tableName, UUID fileId) {
        Long rowCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM " + tableName + " WHERE file_id = ?",
                Long.class,
                fileId);
        assertThat(rowCount).isNotNull();
        return rowCount;
    }

    private static String flip(String phash, int... bitIndexes) {
        long bits = PhashMih.unsignedBits(phash);
        for (int bitIndex : bitIndexes) {
            bits ^= 1L << bitIndex;
        }

        return PhashMih.hex(bits);
    }
}

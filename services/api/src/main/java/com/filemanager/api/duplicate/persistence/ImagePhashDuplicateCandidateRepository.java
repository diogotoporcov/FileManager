package com.filemanager.api.duplicate.persistence;

import com.filemanager.api.duplicate.phash.PhashMih;
import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ImagePhashDuplicateCandidateRepository {
    private static final String FIND_CANDIDATES_SQL = """
            WITH probes AS MATERIALIZED (
                SELECT *
                FROM unnest(?::smallint[], ?::integer[]) AS p(chunk_index, chunk_value)
            ),
            raw AS MATERIALIZED (
                SELECT chunk.file_id
                FROM probes probe
                JOIN image_phash_mih_chunks chunk
                    ON chunk.chunk_index = probe.chunk_index
                    AND chunk.chunk_value = probe.chunk_value
                WHERE chunk.file_id <> ?
            ),
            candidates AS MATERIALIZED (
                SELECT DISTINCT file_id
                FROM raw
            ),
            distances AS MATERIALIZED (
                SELECT
                    image.file_id,
                    filemanager_hex_hamming_distance(image.phash, ?) AS distance
                FROM candidates candidate
                JOIN image_fingerprints image ON image.file_id = candidate.file_id
            ),
            verified AS MATERIALIZED (
                SELECT file_id, distance
                FROM distances
                WHERE distance <= ?
            )
            SELECT
                f.id AS file_id,
                f.name,
                f.mime_type,
                f.size,
                verified.distance,
                f.created_at
            FROM verified
            JOIN files f ON f.id = verified.file_id
            LEFT JOIN folders folder ON folder.id = f.folder_id
            WHERE f.owner_user_id = ?
                AND f.deleted_at IS NULL
                AND (f.folder_id IS NULL OR folder.deleted_at IS NULL)
                AND lower(f.mime_type) LIKE 'image/%'
                AND (
                    ? IS NULL
                    OR verified.distance > ?
                    OR (
                        verified.distance = ?
                        AND f.created_at < ?
                    )
                    OR (
                        verified.distance = ?
                        AND f.created_at = ?
                        AND f.id > ?
                    )
                )
            ORDER BY verified.distance, f.created_at DESC, f.id
            LIMIT ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public List<PhashDuplicateCandidateProjection> findCandidates(
            UUID actorUserId,
            UUID sourceFileId,
            String sourcePhash,
            int maxDistance,
            Integer cursorDistance,
            String cursorCreatedAt,
            String cursorFileId,
            int limit) {
        PhashMih.ProbeKeys probeKeys = PhashMih.probeKeys(sourcePhash, maxDistance);
        OffsetDateTime parsedCursorCreatedAt = cursorCreatedAt == null ? null : OffsetDateTime.parse(cursorCreatedAt);
        UUID parsedCursorFileId = cursorFileId == null ? null : UUID.fromString(cursorFileId);

        return jdbcTemplate.execute((ConnectionCallback<List<PhashDuplicateCandidateProjection>>) connection -> {
            Array chunkIndexes = null;
            Array chunkValues = null;
            try {
                chunkIndexes = connection.createArrayOf(
                        "smallint",
                        probeKeys.chunkIndexes().stream().map(Integer::shortValue).toArray(Short[]::new));
                chunkValues = connection.createArrayOf("integer", probeKeys.chunkValues().toArray(Integer[]::new));

                try (PreparedStatement statement = connection.prepareStatement(FIND_CANDIDATES_SQL)) {
                    statement.setArray(1, chunkIndexes);
                    statement.setArray(2, chunkValues);
                    statement.setObject(3, sourceFileId);
                    statement.setString(4, PhashMih.normalize(sourcePhash));
                    statement.setInt(5, maxDistance);
                    statement.setObject(6, actorUserId);
                    setNullableInteger(statement, 7, cursorDistance);
                    setNullableInteger(statement, 8, cursorDistance);
                    setNullableInteger(statement, 9, cursorDistance);
                    setNullableTimestamp(statement, 10, parsedCursorCreatedAt);
                    setNullableInteger(statement, 11, cursorDistance);
                    setNullableTimestamp(statement, 12, parsedCursorCreatedAt);
                    setNullableUuid(statement, 13, parsedCursorFileId);
                    statement.setInt(14, limit);

                    try (ResultSet resultSet = statement.executeQuery()) {
                        return mapRows(resultSet);
                    }
                }
            } finally {
                freeArray(chunkIndexes);
                freeArray(chunkValues);
            }
        });
    }

    private static List<PhashDuplicateCandidateProjection> mapRows(ResultSet resultSet) throws SQLException {
        List<PhashDuplicateCandidateProjection> rows = new ArrayList<>();
        while (resultSet.next()) {
            rows.add(new PhashDuplicateCandidateRow(
                    resultSet.getObject("file_id", UUID.class),
                    resultSet.getString("name"),
                    resultSet.getString("mime_type"),
                    resultSet.getLong("size"),
                    resultSet.getInt("distance"),
                    resultSet.getObject("created_at", OffsetDateTime.class)));
        }

        return rows;
    }

    private static void freeArray(Array array) {
        if (array == null) {
            return;
        }

        try {
            array.free();
        } catch (SQLException ignored) {
            // Cleanup failures should not hide the query result or the original query exception.
        }
    }

    private static void setNullableInteger(PreparedStatement statement, int index, Integer value)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.INTEGER);
            return;
        }

        statement.setInt(index, value);
    }

    private static void setNullableTimestamp(PreparedStatement statement, int index, OffsetDateTime value)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.TIMESTAMP_WITH_TIMEZONE);
            return;
        }

        statement.setTimestamp(index, Timestamp.from(value.toInstant()), java.util.Calendar.getInstance(
                java.util.TimeZone.getTimeZone(ZoneOffset.UTC)));
    }

    private static void setNullableUuid(PreparedStatement statement, int index, UUID value)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.OTHER);
            return;
        }

        statement.setObject(index, value);
    }

    private record PhashDuplicateCandidateRow(
            UUID fileId,
            String name,
            String mimeType,
            Long size,
            Integer distance,
            OffsetDateTime createdAt) implements PhashDuplicateCandidateProjection {
        @Override
        public UUID getFileId() {
            return fileId;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getMimeType() {
            return mimeType;
        }

        @Override
        public Long getSize() {
            return size;
        }

        @Override
        public Integer getDistance() {
            return distance;
        }

        @Override
        public OffsetDateTime getCreatedAt() {
            return createdAt;
        }
    }
}

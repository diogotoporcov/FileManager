package com.diogotoporcov.filemanager.api.duplicate.persistence;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;
import java.util.UUID;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ExactDuplicateGroupRefreshRepository {
    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    public void refreshExactGroup(UUID ownerUserId, String algorithm, String hashValue) {
        if (isPostgres()) {
            refreshExactGroupPostgres(ownerUserId, algorithm, hashValue);
            return;
        }

        refreshExactGroupPortable(ownerUserId, algorithm, hashValue);
    }

    private void refreshExactGroupPostgres(UUID ownerUserId, String algorithm, String hashValue) {
        jdbcTemplate.update("""
                WITH grouped AS (
                    SELECT
                        f.owner_user_id,
                        fp.algorithm,
                        fp.hash_value,
                        count(f.id) AS active_file_count,
                        (array_agg(f.id ORDER BY f.created_at, f.id))[1] AS representative_file_id
                    FROM file_fingerprints fp
                    JOIN files f ON f.id = fp.file_id
                    LEFT JOIN folders folder ON folder.id = f.folder_id
                    WHERE f.owner_user_id = ?
                        AND fp.algorithm = ?
                        AND fp.hash_value = ?
                        AND f.deleted_at IS NULL
                        AND (f.folder_id IS NULL OR folder.deleted_at IS NULL)
                    GROUP BY f.owner_user_id, fp.algorithm, fp.hash_value
                ),
                deleted AS (
                    DELETE FROM exact_duplicate_groups existing
                    WHERE existing.owner_user_id = ?
                        AND existing.algorithm = ?
                        AND existing.hash_value = ?
                        AND NOT EXISTS (SELECT 1 FROM grouped)
                )
                INSERT INTO exact_duplicate_groups (
                    id,
                    owner_user_id,
                    algorithm,
                    hash_value,
                    active_file_count,
                    representative_file_id,
                    created_at,
                    updated_at
                )
                SELECT
                    uuidv7(),
                    owner_user_id,
                    algorithm,
                    hash_value,
                    active_file_count,
                    representative_file_id,
                    CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP
                FROM grouped
                ON CONFLICT (owner_user_id, algorithm, hash_value)
                DO UPDATE SET
                    active_file_count = EXCLUDED.active_file_count,
                    representative_file_id = EXCLUDED.representative_file_id,
                    updated_at = CURRENT_TIMESTAMP
                """,
                ownerUserId,
                algorithm,
                hashValue,
                ownerUserId,
                algorithm,
                hashValue);
    }

    private void refreshExactGroupPortable(UUID ownerUserId, String algorithm, String hashValue) {
        jdbcTemplate.update("""
                DELETE FROM exact_duplicate_groups
                WHERE owner_user_id = ?
                    AND algorithm = ?
                    AND hash_value = ?
                    AND NOT EXISTS (
                        SELECT 1
                        FROM file_fingerprints fp
                        JOIN files f ON f.id = fp.file_id
                        LEFT JOIN folders folder ON folder.id = f.folder_id
                        WHERE f.owner_user_id = ?
                            AND fp.algorithm = ?
                            AND fp.hash_value = ?
                            AND f.deleted_at IS NULL
                            AND (f.folder_id IS NULL OR folder.deleted_at IS NULL)
                    )
                """,
                ownerUserId,
                algorithm,
                hashValue,
                ownerUserId,
                algorithm,
                hashValue);

        jdbcTemplate.update("""
                MERGE INTO exact_duplicate_groups target
                USING (
                    SELECT
                        f.owner_user_id,
                        fp.algorithm,
                        fp.hash_value,
                        count(f.id) AS active_file_count,
                        (
                            SELECT representative.id
                            FROM file_fingerprints representative_fp
                            JOIN files representative ON representative.id = representative_fp.file_id
                            LEFT JOIN folders representative_folder ON representative_folder.id = representative.folder_id
                            WHERE representative.owner_user_id = ?
                                AND representative_fp.algorithm = ?
                                AND representative_fp.hash_value = ?
                                AND representative.deleted_at IS NULL
                                AND (
                                    representative.folder_id IS NULL
                                    OR representative_folder.deleted_at IS NULL
                                )
                            ORDER BY representative.created_at, representative.id
                            LIMIT 1
                        ) AS representative_file_id
                    FROM file_fingerprints fp
                    JOIN files f ON f.id = fp.file_id
                    LEFT JOIN folders folder ON folder.id = f.folder_id
                    WHERE f.owner_user_id = ?
                        AND fp.algorithm = ?
                        AND fp.hash_value = ?
                        AND f.deleted_at IS NULL
                        AND (f.folder_id IS NULL OR folder.deleted_at IS NULL)
                    GROUP BY f.owner_user_id, fp.algorithm, fp.hash_value
                ) source
                ON target.owner_user_id = source.owner_user_id
                    AND target.algorithm = source.algorithm
                    AND target.hash_value = source.hash_value
                WHEN MATCHED THEN UPDATE SET
                    active_file_count = source.active_file_count,
                    representative_file_id = source.representative_file_id,
                    updated_at = CURRENT_TIMESTAMP
                WHEN NOT MATCHED THEN INSERT (
                    id,
                    owner_user_id,
                    algorithm,
                    hash_value,
                    active_file_count,
                    representative_file_id,
                    created_at,
                    updated_at
                ) VALUES (
                    RANDOM_UUID(),
                    source.owner_user_id,
                    source.algorithm,
                    source.hash_value,
                    source.active_file_count,
                    source.representative_file_id,
                    CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP
                )
                """,
                ownerUserId,
                algorithm,
                hashValue,
                ownerUserId,
                algorithm,
                hashValue);
    }

    private boolean isPostgres() {
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            return connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT).contains("postgres");
        } catch (SQLException ex) {
            throw new IllegalStateException("Unable to inspect database product for exact duplicate refresh", ex);
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }
}

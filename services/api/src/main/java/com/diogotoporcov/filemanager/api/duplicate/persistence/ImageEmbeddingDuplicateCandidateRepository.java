package com.diogotoporcov.filemanager.api.duplicate.persistence;

import com.diogotoporcov.filemanager.api.processing.domain.result.FileEmbedding;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface ImageEmbeddingDuplicateCandidateRepository extends JpaRepository<FileEmbedding, UUID> {
    @Query(value = """
            WITH nearest AS MATERIALIZED (
                SELECT
                    candidate.file_id,
                    candidate.embedding <=> CAST(:sourceEmbedding AS vector) AS distance
                FROM file_embeddings candidate
                WHERE candidate.file_id <> :sourceFileId
                    AND candidate.model_name = :modelName
                    AND candidate.model_version = :modelVersion
                    AND candidate.dimension = :dimension
                ORDER BY candidate.embedding <=> CAST(:sourceEmbedding AS vector)
                LIMIT :searchWindow
            )
            SELECT
                f.id AS "fileId",
                f.name AS "name",
                f.mime_type AS "mimeType",
                f.size AS "size",
                nearest.distance AS "distance",
                f.created_at AS "createdAt"
            FROM nearest
            JOIN files f ON f.id = nearest.file_id
            LEFT JOIN folders folder ON folder.id = f.folder_id
            WHERE f.owner_user_id = :actorUserId
                AND f.deleted_at IS NULL
                AND (f.folder_id IS NULL OR folder.deleted_at IS NULL)
                AND lower(f.mime_type) LIKE 'image/%'
                AND nearest.distance <= :maxDistance
                AND (
                    :cursorDistance IS NULL
                    OR nearest.distance > :cursorDistance
                    OR (
                        nearest.distance = :cursorDistance
                        AND f.created_at < CAST(:cursorCreatedAt AS timestamptz)
                    )
                    OR (
                        nearest.distance = :cursorDistance
                        AND f.created_at = CAST(:cursorCreatedAt AS timestamptz)
                        AND f.id > CAST(:cursorFileId AS uuid)
                    )
                )
            ORDER BY nearest.distance, f.created_at DESC, f.id
            LIMIT :limit
            """, nativeQuery = true)
    List<EmbeddingDuplicateCandidateProjection> findCandidates(
            UUID actorUserId,
            UUID sourceFileId,
            String modelName,
            String modelVersion,
            int dimension,
            String sourceEmbedding,
            double maxDistance,
            int searchWindow,
            Double cursorDistance,
            String cursorCreatedAt,
            String cursorFileId,
            int limit);
}

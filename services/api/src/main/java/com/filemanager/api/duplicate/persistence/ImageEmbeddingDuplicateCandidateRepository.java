package com.filemanager.api.duplicate.persistence;

import com.filemanager.api.processing.domain.result.FileEmbedding;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface ImageEmbeddingDuplicateCandidateRepository extends JpaRepository<FileEmbedding, UUID> {
    @Query(value = """
            SELECT
                f.id AS "fileId",
                f.name AS "name",
                f.mime_type AS "mimeType",
                f.size AS "size",
                (candidate.embedding <=> source.embedding) AS "distance"
            FROM file_embeddings source
            JOIN file_embeddings candidate
                ON candidate.file_id <> source.file_id
                AND candidate.model_name = source.model_name
                AND candidate.model_version = source.model_version
                AND candidate.dimension = source.dimension
            JOIN files f ON f.id = candidate.file_id
            LEFT JOIN folders folder ON folder.id = f.folder_id
            WHERE source.file_id = :sourceFileId
                AND source.model_name = :modelName
                AND source.model_version = :modelVersion
                AND source.dimension = :dimension
                AND f.owner_user_id = :actorUserId
                AND f.deleted_at IS NULL
                AND (f.folder_id IS NULL OR folder.deleted_at IS NULL)
                AND lower(f.mime_type) LIKE 'image/%'
                AND (candidate.embedding <=> source.embedding) <= :maxDistance
            ORDER BY (candidate.embedding <=> source.embedding), f.created_at DESC, f.id
            LIMIT :maxCandidates
            """, nativeQuery = true)
    List<EmbeddingDuplicateCandidateProjection> findCandidates(
            UUID actorUserId,
            UUID sourceFileId,
            String modelName,
            String modelVersion,
            int dimension,
            double maxDistance,
            int maxCandidates);
}

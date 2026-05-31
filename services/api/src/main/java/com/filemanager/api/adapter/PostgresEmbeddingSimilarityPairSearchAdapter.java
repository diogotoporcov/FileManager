package com.filemanager.api.adapter;

import com.filemanager.api.port.EmbeddingSimilarityPairCandidate;
import com.filemanager.api.port.EmbeddingSimilarityPairSearchPort;
import com.filemanager.api.port.EmbeddingSimilarityPairSearchRequest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PostgresEmbeddingSimilarityPairSearchAdapter implements EmbeddingSimilarityPairSearchPort {

    private static final String BASE_SQL = """
            SELECT source.file_id,
                   candidate.file_id,
                   (candidate.embedding <=> source.embedding) AS distance
            FROM file_embeddings source
            JOIN file_embeddings candidate
              ON candidate.model_name = source.model_name
             AND candidate.model_version = source.model_version
             AND candidate.file_id <> source.file_id
            JOIN files source_file ON source_file.id = source.file_id
            JOIN files candidate_file ON candidate_file.id = candidate.file_id
            WHERE source.model_name = :modelName
              AND source.model_version = :modelVersion
              AND source_file.deleted_at IS NULL
              AND candidate_file.deleted_at IS NULL
              AND %s
              AND (
                    CAST(:cursorCreatedAt AS timestamptz) IS NULL
                    OR source_file.created_at > CAST(:cursorCreatedAt AS timestamptz)
                    OR (source_file.created_at = CAST(:cursorCreatedAt AS timestamptz)
                        AND source.file_id > CAST(:cursorFileId AS uuid))
                  )
              AND (
                    source_file.created_at < candidate_file.created_at
                    OR (source_file.created_at = candidate_file.created_at AND source.file_id < candidate.file_id)
                  )
              AND (candidate.embedding <=> source.embedding) <= :threshold
            ORDER BY source_file.created_at ASC,
                     source.file_id ASC,
                     distance ASC,
                     candidate_file.created_at ASC,
                     candidate.file_id ASC
            LIMIT :maxResults
            """;

    private final EntityManager entityManager;

    @Override
    public List<EmbeddingSimilarityPairCandidate> search(EmbeddingSimilarityPairSearchRequest request) {
        String ownerPredicate = request.ownerUserId() != null
                ? "source_file.owner_user_id = :ownerId AND candidate_file.owner_user_id = :ownerId"
                : "source_file.owner_organization_id = :ownerId AND candidate_file.owner_organization_id = :ownerId";

        Query query = entityManager.createNativeQuery(BASE_SQL.formatted(ownerPredicate));
        query.setParameter("ownerId", request.ownerUserId() != null
                ? request.ownerUserId()
                : request.ownerOrganizationId());
        query.setParameter("modelName", request.modelName());
        query.setParameter("modelVersion", request.modelVersion());
        query.setParameter("threshold", request.maxCosineDistance());
        query.setParameter("maxResults", request.maxResults());
        query.setParameter("cursorCreatedAt", request.cursorCreatedAt());
        query.setParameter("cursorFileId", request.cursorFileId());

        List<?> rows = query.getResultList();
        return rows.stream()
                .map(this::toCandidate)
                .toList();
    }

    private EmbeddingSimilarityPairCandidate toCandidate(Object row) {
        Object[] values = (Object[]) row;
        return new EmbeddingSimilarityPairCandidate(
                (UUID) values[0],
                (UUID) values[1],
                ((Number) values[2]).doubleValue());
    }
}

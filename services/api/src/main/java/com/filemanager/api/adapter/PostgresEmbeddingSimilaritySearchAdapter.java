package com.filemanager.api.adapter;

import com.filemanager.api.port.EmbeddingSimilarityCandidate;
import com.filemanager.api.port.EmbeddingSimilaritySearchPort;
import com.filemanager.api.port.EmbeddingSimilaritySearchRequest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PostgresEmbeddingSimilaritySearchAdapter implements EmbeddingSimilaritySearchPort {

    private static final String BASE_SQL = """
            SELECT candidate.file_id, (candidate.embedding <=> source.embedding) AS distance
            FROM file_embeddings source
            JOIN file_embeddings candidate
              ON candidate.model_name = source.model_name
             AND candidate.model_version = source.model_version
             AND candidate.file_id <> source.file_id
            JOIN files source_file ON source_file.id = source.file_id
            JOIN files candidate_file ON candidate_file.id = candidate.file_id
            WHERE source.file_id = :sourceFileId
              AND source.model_name = :modelName
              AND source.model_version = :modelVersion
              AND source_file.deleted_at IS NULL
              AND candidate_file.deleted_at IS NULL
              AND %s
              AND (candidate.embedding <=> source.embedding) <= :threshold
            ORDER BY distance ASC, candidate.created_at ASC
            LIMIT :maxResults
            """;

    private final EntityManager entityManager;

    @Override
    public List<EmbeddingSimilarityCandidate> search(EmbeddingSimilaritySearchRequest request) {
        String ownerPredicate = request.ownerUserId() != null
                ? "source_file.owner_user_id = :ownerId AND candidate_file.owner_user_id = :ownerId"
                : "source_file.owner_organization_id = :ownerId AND candidate_file.owner_organization_id = :ownerId";

        Query query = entityManager.createNativeQuery(BASE_SQL.formatted(ownerPredicate));
        query.setParameter("sourceFileId", request.sourceFileId());
        query.setParameter("ownerId", request.ownerUserId() != null
                ? request.ownerUserId()
                : request.ownerOrganizationId());
        query.setParameter("modelName", request.modelName());
        query.setParameter("modelVersion", request.modelVersion());
        query.setParameter("threshold", request.maxCosineDistance());
        query.setParameter("maxResults", request.maxResults());

        List<?> rows = query.getResultList();
        return rows.stream()
                .map(this::toCandidate)
                .toList();
    }

    private EmbeddingSimilarityCandidate toCandidate(Object row) {
        Object[] values = (Object[]) row;
        return new EmbeddingSimilarityCandidate((UUID) values[0], ((Number) values[1]).doubleValue());
    }
}

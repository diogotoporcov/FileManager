package com.filemanager.api.adapter;

import com.filemanager.api.port.SimilarImageCandidate;
import com.filemanager.api.port.SimilarImageSearchPort;
import com.filemanager.api.port.SimilarImageSearchRequest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PostgresSimilarImageSearchAdapter implements SimilarImageSearchPort {

    private static final String BASE_SQL = """
            SELECT i.file_id, hamming_distance_hex64(i.phash, :phash) AS distance
            FROM image_fingerprints i
            JOIN files f ON f.id = i.file_id
            WHERE i.file_id <> :sourceFileId
              AND f.deleted_at IS NULL
              AND %s
              AND hamming_distance_hex64(i.phash, :phash) <= :threshold
            ORDER BY distance ASC, i.created_at ASC
            LIMIT :maxResults
            """;

    private final EntityManager entityManager;

    @Override
    public List<SimilarImageCandidate> search(SimilarImageSearchRequest request) {
        Query query = entityManager.createNativeQuery(BASE_SQL.formatted(
                NativeOwnerScopeSql.singleFilePredicate(request.ownerUserId())));
        query.setParameter("sourceFileId", request.sourceFileId());
        query.setParameter("ownerId", NativeOwnerScopeSql.ownerId(request.ownerUserId(), request.ownerOrganizationId()));
        query.setParameter("phash", request.phash());
        query.setParameter("threshold", request.threshold());
        query.setParameter("maxResults", request.maxResults());

        List<?> rows = query.getResultList();
        return rows.stream()
                .map(this::toCandidate)
                .toList();
    }

    private SimilarImageCandidate toCandidate(Object row) {
        Object[] values = (Object[]) row;
        return new SimilarImageCandidate((UUID) values[0], ((Number) values[1]).intValue());
    }
}

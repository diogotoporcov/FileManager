package com.filemanager.api.adapter;

import com.filemanager.api.port.SimilarImagePairCandidate;
import com.filemanager.api.port.SimilarImagePairSearchPort;
import com.filemanager.api.port.SimilarImagePairSearchRequest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PostgresSimilarImagePairSearchAdapter implements SimilarImagePairSearchPort {

    private static final String BASE_SQL = """
            SELECT source.file_id,
                   candidate.file_id,
                   hamming_distance_hex64(source.phash, candidate.phash) AS distance
            FROM image_fingerprints source
            JOIN files source_file ON source_file.id = source.file_id
            JOIN image_fingerprints candidate ON candidate.file_id <> source.file_id
            JOIN files candidate_file ON candidate_file.id = candidate.file_id
            WHERE source_file.deleted_at IS NULL
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
              AND hamming_distance_hex64(source.phash, candidate.phash) <= :threshold
            ORDER BY source_file.created_at ASC,
                     source.file_id ASC,
                     distance ASC,
                     candidate_file.created_at ASC,
                     candidate.file_id ASC
            LIMIT :maxResults
            """;

    private final EntityManager entityManager;

    @Override
    public List<SimilarImagePairCandidate> search(SimilarImagePairSearchRequest request) {
        Query query = entityManager.createNativeQuery(BASE_SQL.formatted(
                NativeOwnerScopeSql.pairFilePredicate(request.ownerUserId())));
        query.setParameter("ownerId", NativeOwnerScopeSql.ownerId(request.ownerUserId(), request.ownerOrganizationId()));
        query.setParameter("threshold", request.threshold());
        query.setParameter("maxResults", request.maxResults());
        query.setParameter("cursorCreatedAt", request.cursorCreatedAt());
        query.setParameter("cursorFileId", request.cursorFileId());

        List<?> rows = query.getResultList();
        return rows.stream()
                .map(this::toCandidate)
                .toList();
    }

    private SimilarImagePairCandidate toCandidate(Object row) {
        Object[] values = (Object[]) row;
        return new SimilarImagePairCandidate(
                (UUID) values[0],
                (UUID) values[1],
                ((Number) values[2]).intValue());
    }
}

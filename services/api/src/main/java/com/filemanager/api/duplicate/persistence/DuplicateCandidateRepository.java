package com.filemanager.api.duplicate.persistence;

import com.filemanager.api.duplicate.domain.DuplicateCandidate;
import com.filemanager.api.duplicate.domain.DuplicateCandidate.DuplicateCandidateStatus;
import com.filemanager.api.duplicate.domain.DuplicateSearchMethod;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface DuplicateCandidateRepository extends JpaRepository<DuplicateCandidate, UUID> {
    @Modifying
    @Query(value = """
            DELETE FROM duplicate_candidates
            WHERE owner_user_id = :ownerUserId
                AND method = :method
                AND (file_id_low = :sourceFileId OR file_id_high = :sourceFileId)
            """, nativeQuery = true)
    void deleteInvolvingSource(UUID ownerUserId, UUID sourceFileId, String method);

    @Query("""
            select new com.filemanager.api.duplicate.persistence.DuplicateCandidateFileProjection(
                file.id,
                file.name,
                file.mimeType,
                file.size,
                candidate.distance,
                candidate.score
            )
            from DuplicateCandidate candidate, FileEntity file
            left join file.folder folder
            where candidate.ownerUserId = :ownerUserId
                and candidate.method = :method
                and candidate.modelName = :modelName
                and candidate.modelVersion = :modelVersion
                and candidate.thresholdVersion = :thresholdVersion
                and candidate.status = :status
                and (candidate.fileIdLow = :sourceFileId or candidate.fileIdHigh = :sourceFileId)
                and file.id = case
                    when candidate.fileIdLow = :sourceFileId then candidate.fileIdHigh
                    else candidate.fileIdLow
                end
                and file.ownerUser.id = :ownerUserId
                and file.deletedAt is null
                and (folder is null or folder.deletedAt is null)
            order by candidate.distance asc,
                candidate.score desc,
                file.createdAt desc,
                file.id asc
            """)
    List<DuplicateCandidateFileProjection> findCandidatesForFile(
            UUID ownerUserId,
            UUID sourceFileId,
            DuplicateSearchMethod method,
            String modelName,
            String modelVersion,
            String thresholdVersion,
            DuplicateCandidateStatus status,
            Pageable pageable);

    @Modifying
    @Query(value = """
            INSERT INTO duplicate_candidates (
                id,
                owner_user_id,
                file_id_low,
                file_id_high,
                method,
                confidence,
                distance,
                score,
                evidence_type,
                model_name,
                model_version,
                threshold_version,
                status,
                created_at,
                updated_at
            )
            SELECT
                uuidv7(),
                :ownerUserId,
                CASE WHEN ranked.source_file_id < ranked.candidate_file_id
                    THEN ranked.source_file_id ELSE ranked.candidate_file_id END,
                CASE WHEN ranked.source_file_id < ranked.candidate_file_id
                    THEN ranked.candidate_file_id ELSE ranked.source_file_id END,
                'IMAGE_PHASH',
                'NEAR_DUPLICATE',
                ranked.distance,
                GREATEST(0.0, 1.0 - ranked.distance / 64.0),
                'IMAGE_PHASH',
                :modelName,
                :modelVersion,
                :thresholdVersion,
                'ACTIVE',
                CURRENT_TIMESTAMP,
                CURRENT_TIMESTAMP
            FROM (
                SELECT
                    source.file_id AS source_file_id,
                    candidate.file_id AS candidate_file_id,
                    filemanager_hex_hamming_distance(candidate.phash, source.phash) AS distance,
                    candidate_file.created_at,
                    candidate_file.id
                FROM image_fingerprints source
                JOIN image_fingerprints candidate ON candidate.file_id <> source.file_id
                JOIN files source_file ON source_file.id = source.file_id
                JOIN files candidate_file ON candidate_file.id = candidate.file_id
                LEFT JOIN folders source_folder ON source_folder.id = source_file.folder_id
                LEFT JOIN folders candidate_folder ON candidate_folder.id = candidate_file.folder_id
                WHERE source.file_id = :sourceFileId
                    AND source_file.owner_user_id = :ownerUserId
                    AND candidate_file.owner_user_id = :ownerUserId
                    AND source_file.deleted_at IS NULL
                    AND candidate_file.deleted_at IS NULL
                    AND (source_file.folder_id IS NULL OR source_folder.deleted_at IS NULL)
                    AND (candidate_file.folder_id IS NULL OR candidate_folder.deleted_at IS NULL)
                    AND lower(source_file.mime_type) LIKE 'image/%'
                    AND lower(candidate_file.mime_type) LIKE 'image/%'
                    AND filemanager_hex_hamming_distance(candidate.phash, source.phash) <= :maxDistance
                ORDER BY filemanager_hex_hamming_distance(candidate.phash, source.phash),
                    candidate_file.created_at DESC,
                    candidate_file.id
                LIMIT :maxCandidates
            ) ranked
            ON CONFLICT (
                owner_user_id,
                file_id_low,
                file_id_high,
                method,
                model_name,
                model_version,
                threshold_version
            ) DO UPDATE SET
                confidence = EXCLUDED.confidence,
                distance = EXCLUDED.distance,
                score = EXCLUDED.score,
                evidence_type = EXCLUDED.evidence_type,
                status = 'ACTIVE',
                updated_at = CURRENT_TIMESTAMP
            """, nativeQuery = true)
    int insertImagePhashCandidates(
            UUID ownerUserId,
            UUID sourceFileId,
            int maxDistance,
            int maxCandidates,
            String modelName,
            String modelVersion,
            String thresholdVersion);

    @Modifying
    @Query(value = """
            INSERT INTO duplicate_candidates (
                id,
                owner_user_id,
                file_id_low,
                file_id_high,
                method,
                confidence,
                distance,
                score,
                evidence_type,
                model_name,
                model_version,
                threshold_version,
                status,
                created_at,
                updated_at
            )
            SELECT
                uuidv7(),
                :ownerUserId,
                CASE WHEN ranked.source_file_id < ranked.candidate_file_id
                    THEN ranked.source_file_id ELSE ranked.candidate_file_id END,
                CASE WHEN ranked.source_file_id < ranked.candidate_file_id
                    THEN ranked.candidate_file_id ELSE ranked.source_file_id END,
                'IMAGE_EMBEDDING',
                'NEAR_DUPLICATE',
                ranked.distance,
                GREATEST(0.0, 1.0 - ranked.distance / 2.0),
                'IMAGE_EMBEDDING',
                :modelName,
                :modelVersion,
                :thresholdVersion,
                'ACTIVE',
                CURRENT_TIMESTAMP,
                CURRENT_TIMESTAMP
            FROM (
                SELECT
                    source.file_id AS source_file_id,
                    candidate.file_id AS candidate_file_id,
                    (candidate.embedding <=> source.embedding) AS distance,
                    candidate_file.created_at,
                    candidate_file.id
                FROM file_embeddings source
                JOIN file_embeddings candidate
                    ON candidate.file_id <> source.file_id
                    AND candidate.model_name = source.model_name
                    AND candidate.model_version = source.model_version
                    AND candidate.dimension = source.dimension
                JOIN files source_file ON source_file.id = source.file_id
                JOIN files candidate_file ON candidate_file.id = candidate.file_id
                LEFT JOIN folders source_folder ON source_folder.id = source_file.folder_id
                LEFT JOIN folders candidate_folder ON candidate_folder.id = candidate_file.folder_id
                WHERE source.file_id = :sourceFileId
                    AND source.model_name = :modelName
                    AND source.model_version = :modelVersion
                    AND source.dimension = :dimension
                    AND source_file.owner_user_id = :ownerUserId
                    AND candidate_file.owner_user_id = :ownerUserId
                    AND source_file.deleted_at IS NULL
                    AND candidate_file.deleted_at IS NULL
                    AND (source_file.folder_id IS NULL OR source_folder.deleted_at IS NULL)
                    AND (candidate_file.folder_id IS NULL OR candidate_folder.deleted_at IS NULL)
                    AND lower(source_file.mime_type) LIKE 'image/%'
                    AND lower(candidate_file.mime_type) LIKE 'image/%'
                    AND (candidate.embedding <=> source.embedding) <= :maxDistance
                ORDER BY (candidate.embedding <=> source.embedding),
                    candidate_file.created_at DESC,
                    candidate_file.id
                LIMIT :maxCandidates
            ) ranked
            ON CONFLICT (
                owner_user_id,
                file_id_low,
                file_id_high,
                method,
                model_name,
                model_version,
                threshold_version
            ) DO UPDATE SET
                confidence = EXCLUDED.confidence,
                distance = EXCLUDED.distance,
                score = EXCLUDED.score,
                evidence_type = EXCLUDED.evidence_type,
                status = 'ACTIVE',
                updated_at = CURRENT_TIMESTAMP
            """, nativeQuery = true)
    int insertImageEmbeddingCandidates(
            UUID ownerUserId,
            UUID sourceFileId,
            String modelName,
            String modelVersion,
            int dimension,
            double maxDistance,
            int maxCandidates,
            String thresholdVersion);

    @Modifying
    @Query(value = """
            INSERT INTO duplicate_candidates (
                id,
                owner_user_id,
                file_id_low,
                file_id_high,
                method,
                confidence,
                distance,
                score,
                evidence_type,
                model_name,
                model_version,
                threshold_version,
                status,
                created_at,
                updated_at
            )
            SELECT
                uuidv7(),
                :ownerUserId,
                CASE WHEN ranked.source_file_id < ranked.candidate_file_id
                    THEN ranked.source_file_id ELSE ranked.candidate_file_id END,
                CASE WHEN ranked.source_file_id < ranked.candidate_file_id
                    THEN ranked.candidate_file_id ELSE ranked.source_file_id END,
                'AUDIO_FINGERPRINT',
                'EXACT',
                0.0,
                1.0,
                'AUDIO_FINGERPRINT',
                :modelName,
                :modelVersion,
                :thresholdVersion,
                'ACTIVE',
                CURRENT_TIMESTAMP,
                CURRENT_TIMESTAMP
            FROM (
                SELECT
                    source.file_id AS source_file_id,
                    candidate.file_id AS candidate_file_id,
                    candidate_file.created_at,
                    candidate_file.id
                FROM audio_fingerprints source
                JOIN audio_fingerprints candidate
                    ON candidate.file_id <> source.file_id
                    AND candidate.fingerprint_algorithm = source.fingerprint_algorithm
                    AND candidate.fingerprint_version = source.fingerprint_version
                    AND candidate.fingerprint_hash = source.fingerprint_hash
                JOIN files source_file ON source_file.id = source.file_id
                JOIN files candidate_file ON candidate_file.id = candidate.file_id
                LEFT JOIN folders source_folder ON source_folder.id = source_file.folder_id
                LEFT JOIN folders candidate_folder ON candidate_folder.id = candidate_file.folder_id
                WHERE source.file_id = :sourceFileId
                    AND source_file.owner_user_id = :ownerUserId
                    AND candidate_file.owner_user_id = :ownerUserId
                    AND source_file.deleted_at IS NULL
                    AND candidate_file.deleted_at IS NULL
                    AND (source_file.folder_id IS NULL OR source_folder.deleted_at IS NULL)
                    AND (candidate_file.folder_id IS NULL OR candidate_folder.deleted_at IS NULL)
                    AND lower(source_file.mime_type) LIKE 'audio/%'
                    AND lower(candidate_file.mime_type) LIKE 'audio/%'
                ORDER BY candidate_file.created_at DESC, candidate_file.id
                LIMIT :maxCandidates
            ) ranked
            ON CONFLICT (
                owner_user_id,
                file_id_low,
                file_id_high,
                method,
                model_name,
                model_version,
                threshold_version
            ) DO UPDATE SET
                confidence = EXCLUDED.confidence,
                distance = EXCLUDED.distance,
                score = EXCLUDED.score,
                evidence_type = EXCLUDED.evidence_type,
                status = 'ACTIVE',
                updated_at = CURRENT_TIMESTAMP
            """, nativeQuery = true)
    int insertAudioFingerprintCandidates(
            UUID ownerUserId,
            UUID sourceFileId,
            int maxCandidates,
            String modelName,
            String modelVersion,
            String thresholdVersion);

    @Modifying
    @Query(value = """
            INSERT INTO duplicate_candidates (
                id,
                owner_user_id,
                file_id_low,
                file_id_high,
                method,
                confidence,
                distance,
                score,
                evidence_type,
                model_name,
                model_version,
                threshold_version,
                status,
                created_at,
                updated_at
            )
            SELECT
                uuidv7(),
                :ownerUserId,
                CASE WHEN ranked.source_file_id < ranked.candidate_file_id
                    THEN ranked.source_file_id ELSE ranked.candidate_file_id END,
                CASE WHEN ranked.source_file_id < ranked.candidate_file_id
                    THEN ranked.candidate_file_id ELSE ranked.source_file_id END,
                'VIDEO_EMBEDDING',
                'NEAR_DUPLICATE',
                ranked.distance,
                GREATEST(0.0, 1.0 - ranked.distance / 2.0),
                'VIDEO_EMBEDDING',
                :modelName,
                :modelVersion,
                :thresholdVersion,
                'ACTIVE',
                CURRENT_TIMESTAMP,
                CURRENT_TIMESTAMP
            FROM (
                SELECT
                    source.file_id AS source_file_id,
                    candidate.file_id AS candidate_file_id,
                    (candidate.embedding <=> source.embedding) AS distance,
                    candidate_file.created_at,
                    candidate_file.id
                FROM video_embeddings source
                JOIN video_embeddings candidate
                    ON candidate.file_id <> source.file_id
                    AND candidate.model_name = source.model_name
                    AND candidate.model_version = source.model_version
                    AND candidate.dimension = source.dimension
                JOIN files source_file ON source_file.id = source.file_id
                JOIN files candidate_file ON candidate_file.id = candidate.file_id
                LEFT JOIN folders source_folder ON source_folder.id = source_file.folder_id
                LEFT JOIN folders candidate_folder ON candidate_folder.id = candidate_file.folder_id
                WHERE source.file_id = :sourceFileId
                    AND source.model_name = :modelName
                    AND source.model_version = :modelVersion
                    AND source.dimension = :dimension
                    AND source_file.owner_user_id = :ownerUserId
                    AND candidate_file.owner_user_id = :ownerUserId
                    AND source_file.deleted_at IS NULL
                    AND candidate_file.deleted_at IS NULL
                    AND (source_file.folder_id IS NULL OR source_folder.deleted_at IS NULL)
                    AND (candidate_file.folder_id IS NULL OR candidate_folder.deleted_at IS NULL)
                    AND lower(source_file.mime_type) LIKE 'video/%'
                    AND lower(candidate_file.mime_type) LIKE 'video/%'
                    AND (candidate.embedding <=> source.embedding) <= :maxDistance
                ORDER BY (candidate.embedding <=> source.embedding),
                    candidate_file.created_at DESC,
                    candidate_file.id
                LIMIT :maxCandidates
            ) ranked
            ON CONFLICT (
                owner_user_id,
                file_id_low,
                file_id_high,
                method,
                model_name,
                model_version,
                threshold_version
            ) DO UPDATE SET
                confidence = EXCLUDED.confidence,
                distance = EXCLUDED.distance,
                score = EXCLUDED.score,
                evidence_type = EXCLUDED.evidence_type,
                status = 'ACTIVE',
                updated_at = CURRENT_TIMESTAMP
            """, nativeQuery = true)
    int insertVideoEmbeddingCandidates(
            UUID ownerUserId,
            UUID sourceFileId,
            String modelName,
            String modelVersion,
            int dimension,
            double maxDistance,
            int maxCandidates,
            String thresholdVersion);
}

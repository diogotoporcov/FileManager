package com.filemanager.api.duplicate.persistence;

import com.filemanager.api.processing.domain.result.ImageFingerprint;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface ImagePhashDuplicateCandidateRepository extends JpaRepository<ImageFingerprint, UUID> {
    @Query(value = """
            SELECT
                f.id AS "fileId",
                f.name AS "name",
                f.mime_type AS "mimeType",
                f.size AS "size",
                filemanager_hex_hamming_distance(candidate.phash, :sourcePhash) AS "distance"
            FROM image_fingerprints candidate
            JOIN files f ON f.id = candidate.file_id
            LEFT JOIN folders folder ON folder.id = f.folder_id
            WHERE f.owner_user_id = :actorUserId
                AND f.id <> :sourceFileId
                AND f.deleted_at IS NULL
                AND (f.folder_id IS NULL OR folder.deleted_at IS NULL)
                AND lower(f.mime_type) LIKE 'image/%'
                AND filemanager_hex_hamming_distance(candidate.phash, :sourcePhash) <= :maxDistance
            ORDER BY filemanager_hex_hamming_distance(candidate.phash, :sourcePhash), f.created_at DESC, f.id
            LIMIT :maxCandidates
            """, nativeQuery = true)
    List<PhashDuplicateCandidateProjection> findCandidates(
            UUID actorUserId,
            UUID sourceFileId,
            String sourcePhash,
            int maxDistance,
            int maxCandidates);
}

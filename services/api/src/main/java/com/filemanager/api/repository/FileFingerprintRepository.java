package com.filemanager.api.repository;

import com.filemanager.api.entity.FileFingerprint;
import com.filemanager.api.entity.FileFingerprint.FingerprintAlgorithm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FileFingerprintRepository extends JpaRepository<FileFingerprint, UUID> {
    List<FileFingerprint> findByAlgorithmAndHashValueAndFileOwnerUserIdAndFileDeletedAtIsNull(FingerprintAlgorithm algorithm, String hashValue, UUID ownerUserId);
    List<FileFingerprint> findByAlgorithmAndHashValueAndFileOwnerOrganizationIdAndFileDeletedAtIsNull(FingerprintAlgorithm algorithm, String hashValue, UUID ownerOrganizationId);
    List<FileFingerprint> findByAlgorithmAndHashValueAndFileOwnerUserIdAndFileDeletedAtIsNull(FingerprintAlgorithm algorithm, String hashValue, UUID ownerUserId, Pageable pageable);
    List<FileFingerprint> findByAlgorithmAndHashValueAndFileOwnerOrganizationIdAndFileDeletedAtIsNull(FingerprintAlgorithm algorithm, String hashValue, UUID ownerOrganizationId, Pageable pageable);
    Optional<FileFingerprint> findByFileIdAndAlgorithm(UUID fileId, FingerprintAlgorithm algorithm);

    @Query("""
            select fp
            from FileFingerprint fp
            join fetch fp.file f
            where fp.algorithm = :algorithm
              and f.deletedAt is null
              and f.ownerUser.id = :ownerUserId
              and fp.hashValue in (
                  select grouped.hashValue
                  from FileFingerprint grouped
                  where grouped.algorithm = :algorithm
                    and grouped.file.deletedAt is null
                    and grouped.file.ownerUser.id = :ownerUserId
                  group by grouped.hashValue
                  having count(grouped) > 1
              )
            order by fp.hashValue asc, f.createdAt asc, f.id asc
            """)
    List<FileFingerprint> findDuplicateSha256FingerprintsForOwnerUser(FingerprintAlgorithm algorithm, UUID ownerUserId);

    @Query("""
            select fp
            from FileFingerprint fp
            join fetch fp.file f
            where fp.algorithm = :algorithm
              and f.deletedAt is null
              and f.ownerOrganization.id = :ownerOrganizationId
              and fp.hashValue in (
                  select grouped.hashValue
                  from FileFingerprint grouped
                  where grouped.algorithm = :algorithm
                    and grouped.file.deletedAt is null
                    and grouped.file.ownerOrganization.id = :ownerOrganizationId
                  group by grouped.hashValue
                  having count(grouped) > 1
              )
            order by fp.hashValue asc, f.createdAt asc, f.id asc
            """)
    List<FileFingerprint> findDuplicateSha256FingerprintsForOwnerOrganization(FingerprintAlgorithm algorithm, UUID ownerOrganizationId);

    @Query(value = """
            WITH duplicate_hashes AS (
                SELECT fp.hash_value
                FROM file_fingerprints fp
                JOIN files f ON f.id = fp.file_id
                WHERE fp.algorithm = :algorithm
                  AND f.deleted_at IS NULL
                  AND f.owner_user_id = :ownerUserId
                GROUP BY fp.hash_value
                HAVING count(*) > 1
            ),
            originals AS (
                SELECT DISTINCT ON (fp.hash_value)
                       fp.hash_value AS "hashValue",
                       f.created_at AS "originalCreatedAt",
                       f.id AS "originalFileId"
                FROM file_fingerprints fp
                JOIN files f ON f.id = fp.file_id
                JOIN duplicate_hashes dh ON dh.hash_value = fp.hash_value
                WHERE fp.algorithm = :algorithm
                  AND f.deleted_at IS NULL
                  AND f.owner_user_id = :ownerUserId
                ORDER BY fp.hash_value, f.created_at ASC, f.id ASC
            )
            SELECT "hashValue", "originalCreatedAt", "originalFileId"
            FROM originals
            WHERE CAST(:cursorCreatedAt AS timestamptz) IS NULL
               OR "originalCreatedAt" > CAST(:cursorCreatedAt AS timestamptz)
               OR ("originalCreatedAt" = CAST(:cursorCreatedAt AS timestamptz)
                   AND "originalFileId" > CAST(:cursorFileId AS uuid))
            ORDER BY "originalCreatedAt" ASC, "originalFileId" ASC
            LIMIT :maxRows
            """, nativeQuery = true)
    List<DuplicateHashGroupProjection> findDuplicateSha256GroupsForOwnerUser(
            String algorithm,
            UUID ownerUserId,
            OffsetDateTime cursorCreatedAt,
            UUID cursorFileId,
            int maxRows);

    @Query(value = """
            WITH duplicate_hashes AS (
                SELECT fp.hash_value
                FROM file_fingerprints fp
                JOIN files f ON f.id = fp.file_id
                WHERE fp.algorithm = :algorithm
                  AND f.deleted_at IS NULL
                  AND f.owner_organization_id = :ownerOrganizationId
                GROUP BY fp.hash_value
                HAVING count(*) > 1
            ),
            originals AS (
                SELECT DISTINCT ON (fp.hash_value)
                       fp.hash_value AS "hashValue",
                       f.created_at AS "originalCreatedAt",
                       f.id AS "originalFileId"
                FROM file_fingerprints fp
                JOIN files f ON f.id = fp.file_id
                JOIN duplicate_hashes dh ON dh.hash_value = fp.hash_value
                WHERE fp.algorithm = :algorithm
                  AND f.deleted_at IS NULL
                  AND f.owner_organization_id = :ownerOrganizationId
                ORDER BY fp.hash_value, f.created_at ASC, f.id ASC
            )
            SELECT "hashValue", "originalCreatedAt", "originalFileId"
            FROM originals
            WHERE CAST(:cursorCreatedAt AS timestamptz) IS NULL
               OR "originalCreatedAt" > CAST(:cursorCreatedAt AS timestamptz)
               OR ("originalCreatedAt" = CAST(:cursorCreatedAt AS timestamptz)
                   AND "originalFileId" > CAST(:cursorFileId AS uuid))
            ORDER BY "originalCreatedAt" ASC, "originalFileId" ASC
            LIMIT :maxRows
            """, nativeQuery = true)
    List<DuplicateHashGroupProjection> findDuplicateSha256GroupsForOwnerOrganization(
            String algorithm,
            UUID ownerOrganizationId,
            OffsetDateTime cursorCreatedAt,
            UUID cursorFileId,
            int maxRows);

    @Query("""
            select fp
            from FileFingerprint fp
            join fetch fp.file f
            where fp.algorithm = :algorithm
              and fp.hashValue in :hashValues
              and f.deletedAt is null
              and f.ownerUser.id = :ownerUserId
            order by fp.hashValue asc, f.createdAt asc, f.id asc
            """)
    List<FileFingerprint> findByOwnerUserAndAlgorithmAndHashValueIn(
            FingerprintAlgorithm algorithm,
            UUID ownerUserId,
            @Param("hashValues") Collection<String> hashValues);

    @Query("""
            select fp
            from FileFingerprint fp
            join fetch fp.file f
            where fp.algorithm = :algorithm
              and fp.hashValue in :hashValues
              and f.deletedAt is null
              and f.ownerOrganization.id = :ownerOrganizationId
            order by fp.hashValue asc, f.createdAt asc, f.id asc
            """)
    List<FileFingerprint> findByOwnerOrganizationAndAlgorithmAndHashValueIn(
            FingerprintAlgorithm algorithm,
            UUID ownerOrganizationId,
            @Param("hashValues") Collection<String> hashValues);
}
